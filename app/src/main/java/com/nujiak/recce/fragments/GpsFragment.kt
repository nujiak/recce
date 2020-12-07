package com.nujiak.recce.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.nujiak.recce.MainViewModel
import com.nujiak.recce.R
import com.nujiak.recce.databinding.FragmentGpsBinding
import com.nujiak.recce.location.FusedLocationLiveData
import com.nujiak.recce.utils.formatAsDistanceString
import com.nujiak.recce.utils.getAngleString
import com.nujiak.recce.utils.getGridString
import com.nujiak.recce.utils.radToDeg
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.PI
import kotlin.math.cos


@AndroidEntryPoint
class GpsFragment : Fragment(), SensorEventListener {

    private val viewModel: MainViewModel by activityViewModels()
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

    private var coordSysId = 0
    private var angleUnitId = 0

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentGpsBinding.inflate(inflater, container, false)

        binding.root.fitsSystemWindows = false

        // Listen for fused location updates
        viewModel.fusedLocationData.observe(viewLifecycleOwner, {
            updateLocationUI(it)
        })

        // Initialise sensor manager
        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Observe for preferences changes
        viewModel.coordinateSystem.observe(viewLifecycleOwner, {
            coordSysId = it
            binding.gpsGridSystem.text =
                resources.getStringArray(R.array.coordinate_systems)[coordSysId]
            updateLocationUI()
        })
        viewModel.angleUnit.observe(viewLifecycleOwner, {
            angleUnitId = it
            updateOrientationUI(true)
        })

        return binding.root
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Do nothing
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }

        val currentTime = System.currentTimeMillis()
        if ((currentTime - lastCompassUpdateMillis) > textViewUpdateInterval) {
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
            binding.gpsAzimuth.text = getAngleString(aziRad, angleUnitId, false)
            binding.gpsPitch.text = getAngleString(-pitRad, angleUnitId, true)
            binding.gpsRoll.text = getAngleString(rolRad, angleUnitId, true)
        }
        updateCompass(aziRad, pitRad, rolRad)
    }

    private fun updateCompass(aziRad: Float, pitRad: Float, rolRad: Float) {

        val compassDrawable = binding.gpsCompassArrow.drawable

        val matrix = Matrix().apply {
            postRotate(
                -radToDeg(aziRad),
                compassDrawable!!.intrinsicWidth / 2f,
                compassDrawable.intrinsicHeight / 2f
            )
            postScale(
                cos(rolRad), cos(pitRad),
                compassDrawable.intrinsicWidth / 2f,
                compassDrawable.intrinsicHeight / 2f
            )
        }
        binding.gpsCompassArrow.imageMatrix = matrix
    }

    @SuppressLint("SetTextI18n")
    private fun updateLocationUI(locationData: FusedLocationLiveData.LocationData? = lastLocationData) {
        if (locationData != null) {
            val (latitude, longitude, altitude, accuracy) = locationData
            binding.gpsAccuracy.text = "±" + accuracy.formatAsDistanceString()
            binding.gpsAltitude.text = altitude.formatAsDistanceString()
            binding.gpsLatLng.text =
                "%.6f, %.6f".format(latitude, longitude)
            binding.gpsGrids.text = getGridString(latitude, longitude, coordSysId, resources)
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
    }

    override fun onPause() {
        super.onPause()
        // Stop listening to sensor
        sensorManager.unregisterListener(this)
    }
}
