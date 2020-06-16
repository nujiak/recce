package com.nujiak.reconnaissance.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.nujiak.reconnaissance.*
import com.nujiak.reconnaissance.database.Pin
import com.nujiak.reconnaissance.database.PinDatabase
import com.nujiak.reconnaissance.databinding.FragmentMapBinding
import com.nujiak.reconnaissance.location.FusedLocationLiveData
import com.nujiak.reconnaissance.mapping.kertau.getKertauGridsString
import com.nujiak.reconnaissance.mapping.utm.getUtmString
import com.nujiak.reconnaissance.modalsheets.SettingsSheet.Companion.COORD_SYS_ID_KERTAU
import com.nujiak.reconnaissance.modalsheets.SettingsSheet.Companion.COORD_SYS_ID_UTM


class MapFragment : Fragment(), OnMapReadyCallback,
    ActivityCompat.OnRequestPermissionsResultCallback {

    private val LOCATION_REQUEST_CODE = 1
    private lateinit var binding: FragmentMapBinding
    lateinit var viewModel: MainViewModel
    lateinit var map: GoogleMap
    private var coordSysId = 0

    private var currentPinColor = 0
    private var markersMap = HashMap<Marker, Pin>()

    private var isShowingPin = false

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
                updateLatLong() // Force card update
            }
        })

        return binding.root
    }

    override fun onMapReady(mMap: GoogleMap?) {
        if (mMap != null) {
            map = mMap

            map.mapType = GoogleMap.MAP_TYPE_HYBRID
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

            // Disable built-in Maps controls
            map.uiSettings.isZoomControlsEnabled = false
            map.uiSettings.isMyLocationButtonEnabled = false

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

    private fun isLocationGranted() = ContextCompat.checkSelfPermission(
        this.requireContext(),
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun onLocationGranted(firstRun: Boolean = false) {
        map.isMyLocationEnabled = true
        binding.mapLocationButton.isEnabled = true

        // Observe my location once
        if (firstRun) {
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

        binding.mapGridText.text = when (coordSysId) {
            COORD_SYS_ID_UTM -> {
                getUtmString(latitude, longitude)
            }
            COORD_SYS_ID_KERTAU -> {
                getKertauGridsString(latitude, longitude)
            }
            else -> throw IllegalArgumentException("Invalid coordinate system index: $coordSysId")
        } ?: binding.root.resources.getString(R.string.not_available)
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
        val currentZoom = map.cameraPosition.zoom
        val location = viewModel.fusedLocationData.value
        if (location != null) {
            val cameraPosition = CameraPosition.builder()
                .target(LatLng(location.latitude, location.longitude))
                .zoom(if (currentZoom < 10) 15f else currentZoom)
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 500, null)
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
            true
        } else {
            false
        }
    }

    private fun onCameraMoveStarted(reason: Int) {
        if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
            showPinInCard(null)
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

            // Set card coordinates
            updateLatLong(pin)

            isShowingPin = true
        } else {
            binding.mapPinNameText.text = "+"
            isShowingPin = false
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
