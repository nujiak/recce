package com.nujiak.reconnaissance.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.*
import android.os.Bundle
import android.view.*
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.nujiak.reconnaissance.*
import com.nujiak.reconnaissance.database.PinDatabase
import com.nujiak.reconnaissance.databinding.FragmentGpsBinding
import com.nujiak.reconnaissance.location.FusedLocationLiveData
import com.nujiak.reconnaissance.mapping.getKertauGridsString
import com.nujiak.reconnaissance.mapping.getUtmString
import com.nujiak.reconnaissance.modalsheets.SettingsSheet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt


/**
 * A simple [Fragment] subclass.
 */
class GpsFragment : Fragment(), SensorEventListener {

    private lateinit var viewModel: MainViewModel
    private lateinit var binding: FragmentGpsBinding

    // Variables for compass readings
    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var lastCompassUpdateMillis = System.currentTimeMillis()
    private val textViewUpdateInterval = 1000 / 10
    private var lastLocationData: FusedLocationLiveData.LocationData? = null

    private var currentCompassAngleDeg = 0f
    private var coordSysId = 0

    private var screenRotation: Int = 0
    private lateinit var display: Display
    private var sensorAccuracy: Int = 1

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentGpsBinding.inflate(inflater, container, false)

        // Set up ViewModel
        val application = requireNotNull(this.activity).application
        val dataSource = PinDatabase.getInstance(application).pinDatabaseDao
        val viewModelFactory = MainViewModelFactory(dataSource, application)
        viewModel = activity?.let {
            ViewModelProvider(
                it,
                viewModelFactory
            ).get(MainViewModel::class.java)
        }!!

        // Listen for fused location updates
        viewModel.fusedLocationData.observe(viewLifecycleOwner, Observer {
            updateLocationUI(it)
        })

        // Initialise sensor manager
        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Observe for coordinate system preference change
        viewModel.coordinateSystem.observe(viewLifecycleOwner, Observer {
            coordSysId = it
            binding.gpsGridSystem.text =
                resources.getStringArray(R.array.coordinate_systems)[coordSysId]
            updateLocationUI()
        })

        return binding.root
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensorAccuracy = accuracy
        if (accuracy <= SENSOR_STATUS_ACCURACY_MEDIUM) {
            binding.gpsAccuracyWarning.visibility = View.VISIBLE
        } else {
            binding.gpsAccuracyWarning.visibility = View.INVISIBLE
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }

        val currentTime = System.currentTimeMillis()
        if ((currentTime - lastCompassUpdateMillis) > textViewUpdateInterval
            && sensorAccuracy >= SENSOR_STATUS_ACCURACY_LOW
        ) {
            updateOrientationAngles(updateTexts = true)
            lastCompassUpdateMillis = currentTime
        } else {
            updateOrientationAngles(updateTexts = false)
        }
    }

    private fun updateOrientationAngles(updateTexts: Boolean = true) {
        // Update rotation matrix, which is needed to update orientation angles.
        getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        // "rotationMatrix" now has up-to-date information.
        var remappedRotationMatrix = FloatArray(9)
        when (viewModel.screenRotation) {
            Surface.ROTATION_90 -> {
                remapCoordinateSystem(rotationMatrix, AXIS_Y, AXIS_MINUS_X, remappedRotationMatrix)
            }
            Surface.ROTATION_180 -> {
                remapCoordinateSystem(
                    rotationMatrix,
                    AXIS_MINUS_Y,
                    AXIS_MINUS_X,
                    remappedRotationMatrix
                )
            }
            Surface.ROTATION_270 -> {
                remapCoordinateSystem(rotationMatrix, AXIS_MINUS_Y, AXIS_X, remappedRotationMatrix)
            }
            else -> remappedRotationMatrix = rotationMatrix
        }

        getOrientation(remappedRotationMatrix, orientationAngles)
        // "orientationAngles" now has up-to-date information.

        updateOrientationUI(updateTexts)
    }

    @SuppressLint("SetTextI18n")
    private fun updateOrientationUI(updateTexts: Boolean = true) {
        var (aziRad, pitRad, rolRad) = orientationAngles

        if (aziRad < 0) {
            aziRad += 2 * PI.toFloat()
        }
        if (updateTexts) {
            binding.gpsAzimuth.text = "%.2f°".format(radToDeg(aziRad))
            val aziMilsStr =
                "${radToNatoMils(aziRad).roundToInt().toString().padStart(4, '0')} mils"
            binding.gpsAzimuthMils.text = aziMilsStr

            pitRad = -pitRad
            binding.gpsPitch.text = "%.2f°".format(radToDeg(pitRad))
            val pitMilsStr =
                "${if (pitRad > 0) '+' else '-'}${radToNatoMils(abs(pitRad)).roundToInt().toString()
                    .padStart(4, '0')} mils"
            binding.gpsPitchMils.text = pitMilsStr

            binding.gpsRoll.text = "%.2f°".format(radToDeg(rolRad))
            val rolMilsStr =
                "${if (rolRad > 0) '+' else '-'}${radToNatoMils(abs(rolRad)).roundToInt().toString()
                    .padStart(4, '0')} mils"
            binding.gpsRollMils.text = rolMilsStr
        }
        updateCompass(radToDeg(aziRad))

    }

    private fun updateCompass(bearingFromNorthDeg: Float) {

        // Target angle is bearing from current direction to North
        val targetAngleDeg = -bearingFromNorthDeg

        val rotateAnimation = RotateAnimation(
            currentCompassAngleDeg,
            targetAngleDeg,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )

        rotateAnimation.duration = 0
        rotateAnimation.fillAfter = true
        binding.gpsCompassArrow.startAnimation(rotateAnimation)

        currentCompassAngleDeg = targetAngleDeg
    }

    private fun updateLocationUI(locationData: FusedLocationLiveData.LocationData? = lastLocationData) {
        if (locationData != null) {
            val (latitude, longitude, altitude, _) = locationData
            binding.gpsLatLng.text =
                getString(R.string.coordinates_format).format(latitude, longitude, altitude)

            binding.gpsGrids.text = when (coordSysId) {
                SettingsSheet.COORD_SYS_ID_UTM -> {
                    getUtmString(
                        locationData.latitude,
                        locationData.longitude
                    )
                }
                SettingsSheet.COORD_SYS_ID_KERTAU -> {
                    getKertauGridsString(
                        locationData.latitude,
                        locationData.longitude
                    )
                }
                else -> throw IllegalArgumentException("Invalid coordinate system index: $coordSysId")
            } ?: getString(R.string.not_available)
        }

    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SENSOR_DELAY_UI,
                SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                this,
                magneticField,
                SENSOR_DELAY_UI,
                SENSOR_DELAY_UI
            )
        }

        // Get current screen rotation
        display = requireActivity().windowManager.defaultDisplay
        screenRotation = display.rotation

    }

    override fun onPause() {
        super.onPause()
        // Stop listening to sensor
        sensorManager.unregisterListener(this)
    }
}
