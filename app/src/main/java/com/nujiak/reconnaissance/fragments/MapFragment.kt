package com.nujiak.reconnaissance.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.maps.android.SphericalUtil
import com.nujiak.reconnaissance.*
import com.nujiak.reconnaissance.database.Pin
import com.nujiak.reconnaissance.database.PinDatabase
import com.nujiak.reconnaissance.databinding.FragmentMapBinding
import com.nujiak.reconnaissance.location.FusedLocationLiveData
import java.text.NumberFormat
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.hypot
import kotlin.math.roundToInt


class MapFragment : Fragment(), OnMapReadyCallback,
    ActivityCompat.OnRequestPermissionsResultCallback {

    private lateinit var binding: FragmentMapBinding
    lateinit var viewModel: MainViewModel
    lateinit var map: GoogleMap
    lateinit var layerPopup: PopupWindow
    private var coordSysId = 0

    private var currentPinColor = 0
    private var markersMap = HashMap<Marker, Pin>()

    private var isShowingPin = false
    private var isShowingMyLocation = false

    private var isLiveMeasurementVisible = false
    private var isLayersPopupVisible = false

    private lateinit var numberFormat: NumberFormat

    private val MAP_TYPE_KEY = "map_type"
    private var currentMapType = GoogleMap.MAP_TYPE_HYBRID

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
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
        // Set up data binding
        binding = FragmentMapBinding.inflate(inflater, container, false)

        // Set up MapView
        val mapView = binding.mapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        // Set up pin-adding sequence
        viewModel.toAddPinFromMap.observe(viewLifecycleOwner, Observer {
            if (it) onAddPinFromMap()
        })
        binding.mapCardParent.setOnClickListener { onAddPinFromMap() }

        // Set up pin-showing sequence
        viewModel.pinInFocus.observe(viewLifecycleOwner, Observer { pin -> moveToPin(pin) })

        // Set up custom map controls
        binding.mapZoomInButton.setOnClickListener { onZoomIn() }
        binding.mapZoomOutButton.setOnClickListener { onZoomOut() }
        binding.mapLocationButton.setOnClickListener { onMyLocationPressed() }
        // Set card background to color of last added pin
        viewModel.lastPin.value.let { pin ->
            if (pin != null) {
                currentPinColor = pin.color
            }
            val color =
                ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[currentPinColor])
            binding.mapDetailsCardView.setCardBackgroundColor(color)
        }

        // Set up Coordinate System
        viewModel.coordinateSystem.observe(viewLifecycleOwner, Observer {
            coordSysId = it
            updateCardGridSystem(coordSysId)
            if (this::map.isInitialized) {
                // Force card update
                updateLatLong()
            }
        })

        // Set up Map Type toggle buttons
        binding.mapNormalType.setOnClickListener {
            updateMapType(GoogleMap.MAP_TYPE_NORMAL)

        }
        binding.mapHybridType.setOnClickListener {
            updateMapType(GoogleMap.MAP_TYPE_HYBRID)
        }
        binding.mapSatelliteType.setOnClickListener {
            updateMapType(GoogleMap.MAP_TYPE_SATELLITE)
        }

        // NumberFormat used to format distances in Live Measurements
        numberFormat = NumberFormat.getNumberInstance(Locale.US)
        numberFormat.minimumFractionDigits = 1
        numberFormat.maximumFractionDigits = 1

        return binding.root
    }

    private fun updateMapType(newMapType: Int) {
        if (currentMapType == newMapType) {
            return
        }
        currentMapType = newMapType
        map.mapType = newMapType
        viewModel.sharedPreference.edit().putInt(MAP_TYPE_KEY, newMapType).apply()
    }

    override fun onMapReady(mMap: GoogleMap?) {
        if (mMap != null) {
            map = mMap

            val newMapType = viewModel.sharedPreference.getInt(MAP_TYPE_KEY, currentMapType)
            map.mapType = newMapType
            currentMapType = newMapType
            binding.mapTypeGroup.check(when (newMapType) {
                GoogleMap.MAP_TYPE_NORMAL -> R.id.map_normal_type
                GoogleMap.MAP_TYPE_HYBRID -> R.id.map_hybrid_type
                GoogleMap.MAP_TYPE_SATELLITE -> R.id.map_satellite_type
                else -> R.id.map_normal_type
            })

            map.setOnCameraMoveListener {
                onCameraMove()
            }
            map.setOnMarkerClickListener { marker ->
                onMarkerClick(marker)
            }
            map.setOnCameraMoveStartedListener {
                onCameraMoveStarted(it)
            }

            val uiSetting = map.uiSettings
            uiSetting.isZoomControlsEnabled = true

            viewModel.isLocPermGranted.observe(viewLifecycleOwner, Observer { onLocPermChange(it) })

            // Observe location to update Live Measurement
            viewModel.fusedLocationData.observe(viewLifecycleOwner, Observer {
                updateLiveMeasurements()
            })

            // Disable built-in Maps controls
            map.uiSettings.isZoomControlsEnabled = false
            map.uiSettings.isMyLocationButtonEnabled = false
            val scale = resources.displayMetrics.density
            val padding = (24 * scale).roundToInt()
            map.setPadding(padding, padding, padding, padding)

            // Add markers
            viewModel.allPins.observe(viewLifecycleOwner, Observer { allPins ->
                drawMarkers(allPins)
            })

            updateLatLong()

        }
    }

    private fun drawMarkers(allPins: List<Pin>) {
        map.clear()
        for (pin in allPins) {
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(pin.latitude, pin.longitude))
                    .title(pin.name)
                    .icon(bitmapDescriptorFromVector(PIN_VECTOR_DRAWABLE[pin.color]))
                //.icon(BitmapDescriptorFactory.defaultMarker(PIN_COLOR_HUES[pin.color]))
            )
            markersMap[marker] = pin
        }
    }

    private fun bitmapDescriptorFromVector(vectorResId: Int): BitmapDescriptor? {
        return ContextCompat.getDrawable(requireContext(), vectorResId)?.run {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            val bitmap =
                Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    @SuppressLint("MissingPermission")
    private fun onLocPermChange(isGranted: Boolean) {

        map.isMyLocationEnabled = isGranted
        binding.mapLocationButton.isEnabled = isGranted
        // Observe my location once
        if (isGranted) {
            viewModel.fusedLocationData.observe(
                viewLifecycleOwner,
                object : Observer<FusedLocationLiveData.LocationData> {
                    override fun onChanged(location: FusedLocationLiveData.LocationData) {
                        val cameraPosition = CameraPosition.builder()
                            .target(LatLng(location.latitude, location.longitude))
                            .zoom(15f)
                            .build()
                        map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                        updateLatLong()

                        viewModel.fusedLocationData.removeObserver(this)
                    }
                })
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateLatLong(pin: Pin? = null) {
        val latitude: Double
        val longitude: Double
        if (pin != null) {
            latitude = pin.latitude
            longitude = pin.longitude
        } else {
            val cameraPosition = map.cameraPosition
            val target = cameraPosition.target
            latitude = target.latitude
            longitude = target.longitude
        }
        binding.mapLatLngText.text = "%.6f %.6f".format(latitude, longitude)
        binding.mapGridText.text = getGridString(latitude, longitude, coordSysId, resources)

        updateLiveMeasurements(latitude, longitude)
    }

    @SuppressLint("SetTextI18n")
    private fun updateLiveMeasurements(lat: Double? = null, lng: Double? = null) {
        var targetLat = lat
        var targetLng = lng
        if (targetLat == null || targetLng == null) {
            val mapTarget = map.cameraPosition.target
            targetLat = mapTarget.latitude
            targetLng = mapTarget.longitude
        }
        val fusedLocationData = viewModel.fusedLocationData.value ?: return

        val myLatLng = LatLng(fusedLocationData.latitude, fusedLocationData.longitude)
        val targetLatLng = LatLng(targetLat, targetLng)

        val distance = SphericalUtil.computeDistanceBetween(myLatLng, targetLatLng)
        var direction = SphericalUtil.computeHeading(myLatLng, targetLatLng)
        if (direction < 0) {
            direction += 360
        }
        val directionMils = degToNatoMils(direction)

        binding.mapCurrentDistance.text = if (distance < 1000000) {
            numberFormat.format(distance) + " m"
        } else {
            numberFormat.format(distance / 1000) + " km"
        }
        binding.mapCurrentDirection.text =
            "${directionMils.roundToInt().toString().padStart(4, '0')} mils"
    }

    private fun updateCardGridSystem(coordSysId: Int) {
        binding.mapGridSystem.text =
            resources.getStringArray(R.array.coordinate_systems)[coordSysId]
    }

    private fun onAddPinFromMap(): Boolean {
        if (isShowingPin) {
            viewModel.openPinCreator(viewModel.pinInFocus.value)
        } else {
            val cameraPosition = map.cameraPosition
            val target = cameraPosition.target
            val pinLat = target.latitude.toString().toDouble()
            val pinLong = target.longitude.toString().toDouble()

            val newPin = Pin("", pinLat, pinLong, currentPinColor)
            viewModel.openPinCreator(newPin)
        }
        return true
    }

    private fun onZoomIn() {
        if (map.cameraPosition.zoom > 15) {
            map.animateCamera(CameraUpdateFactory.zoomBy(1f))
        } else {
            map.animateCamera(CameraUpdateFactory.zoomBy(2f))
        }
    }

    private fun onZoomOut() {
        if (map.cameraPosition.zoom > 15) {
            map.animateCamera(CameraUpdateFactory.zoomBy(-1f))
        } else {
            map.animateCamera(CameraUpdateFactory.zoomBy(-2f))
        }
    }

    private fun onMyLocationPressed() {
        isShowingMyLocation = true
        val currentZoom = map.cameraPosition.zoom
        val location = viewModel.fusedLocationData.value
        if (location != null) {
            val cameraPosition = CameraPosition.builder()
                .target(LatLng(location.latitude, location.longitude))
                .zoom(if (currentZoom < 10) 15f else currentZoom)
                .build()
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(cameraPosition),
                500,
                object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        toggleLiveMeasurement(false)
                        isShowingMyLocation = false
                    }

                    override fun onCancel() {}
                })
            showPinInCard(null)
        }
    }

    private fun onCameraMove() {
        if (!isShowingPin) {
            updateLatLong()
        }
    }

    private fun onMarkerClick(marker: Marker): Boolean {
        val pin = markersMap[marker]
        return if (pin != null) {
            viewModel.putPinInFocus(pin)
            toggleLiveMeasurement(true)
            true
        } else {
            false
        }
    }

    private fun onCameraMoveStarted(reason: Int) {
        if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
            showPinInCard(null)
            if (!isShowingMyLocation) {
                toggleLiveMeasurement(true)
            }
        }
    }

    private fun moveToPin(pin: Pin) {
        if (this::map.isInitialized) {
            showPinInCard(pin)
            val currentZoom = map.cameraPosition.zoom
            val cameraPosition = CameraPosition.builder()
                .target(LatLng(pin.latitude, pin.longitude))
                .zoom(if (currentZoom < 10f) 15f else currentZoom)
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 350, null)
        }
    }

    private fun showPinInCard(pin: Pin?) {
        if (pin != null) {
            // Set name on card
            binding.mapPinNameText.text = pin.name

            // Set card background color
            val color = ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[pin.color])
            binding.mapDetailsCardView.setCardBackgroundColor(color)
            currentPinColor = pin.color

            if (pin.group.isNotEmpty()) {
                binding.mapPinGroup.text = pin.group
                binding.mapPinGroup.setTextColor(color)
                binding.mapPinGroup.visibility = View.VISIBLE
            } else {
                binding.mapPinGroup.visibility = View.INVISIBLE
            }

            // Set card coordinates
            updateLatLong(pin)

            isShowingPin = true
        } else {
            binding.mapPinNameText.text = "+"
            binding.mapPinGroup.visibility = View.INVISIBLE
            isShowingPin = false
        }
    }

    private fun toggleLiveMeasurement(makeVisible: Boolean = true) {

        if (isLiveMeasurementVisible == makeVisible) {
            return
        }

        val liveMeasurement = binding.mapLiveMeasurement

        val centreX = liveMeasurement.width / 2
        val centreY = liveMeasurement.height / 2

        // Get radius of circle covering entire element
        val radius = hypot(centreX.toDouble(), centreY.toDouble()).toFloat()

        if (makeVisible) {
            try {
                // Attempt to create and start the animation
                val anim = ViewAnimationUtils.createCircularReveal(
                    liveMeasurement,
                    centreX,
                    centreY,
                    0f,
                    radius
                )
                liveMeasurement.visibility = View.VISIBLE
                anim.duration = 200
                anim.start()
            } catch (e: Exception) {
                // If view is detached, just toggle visibility without animation
                liveMeasurement.visibility = View.VISIBLE
            } finally {
                isLiveMeasurementVisible = true
            }
        } else {
            try {
                // Attempt to create and start the animation
                val anim = ViewAnimationUtils.createCircularReveal(
                    liveMeasurement,
                    centreX,
                    centreY,
                    radius,
                    0f
                )
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        liveMeasurement.visibility = View.INVISIBLE
                        isLiveMeasurementVisible = false
                    }

                })
                anim.duration = 200
                anim.start()
            } catch (e: Exception) {
                // If view is detached, just toggle visibility without animation
                liveMeasurement.visibility = View.INVISIBLE
                isLiveMeasurementVisible = false
            }
        }
    }

    /**
     * Functions below are to forward lifecycle
     * events to MapView
     */

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()

        binding.mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }
}
