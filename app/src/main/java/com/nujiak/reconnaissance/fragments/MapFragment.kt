package com.nujiak.reconnaissance.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.TypeEvaluator
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.util.Property
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
import com.nujiak.reconnaissance.database.*
import com.nujiak.reconnaissance.databinding.FragmentMapBinding
import com.nujiak.reconnaissance.location.FusedLocationLiveData
import java.text.NumberFormat
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.cos
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
    private var chainsMap = HashMap<Polyline, Chain>()
    private var myLocationMarker: Marker? = null
    private var myLocationCircle: Circle? = null
    private var currentPolyline: Polyline? = null

    private var isShowingPin = false
    private var isShowingMyLocation = false

    private var isLiveMeasurementVisible = false
    private var isMapCompassVisible = false
    private var compassImageDrawable: Drawable? = null

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
        val dataSource = ReconDatabase.getInstance(application).pinDatabaseDao
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

        binding.mapPolylineAdd.setOnClickListener {
            if (!viewModel.isInPolylineMode.value!!) {
                enterPolylineMode()
            } else {
                onAddPolylinePoint()
            }
        }

        binding.mapPolylineUndo.apply {
            setOnClickListener { undoPolyline() }
            setOnLongClickListener {
                exitPolylineMode()
                true
            }
        }

        binding.mapPolylineSave.setOnClickListener { onSavePolyline() }

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

        // Set up Map rotation reset
        binding.mapCompass.setOnClickListener { onResetMapRotation() }

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
            binding.mapTypeGroup.check(
                when (newMapType) {
                    GoogleMap.MAP_TYPE_NORMAL -> R.id.map_normal_type
                    GoogleMap.MAP_TYPE_HYBRID -> R.id.map_hybrid_type
                    GoogleMap.MAP_TYPE_SATELLITE -> R.id.map_satellite_type
                    else -> R.id.map_normal_type
                }
            )

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
                updateMyLocation(it)
            })

            // Disable built-in Maps controls
            map.uiSettings.apply {
                isZoomControlsEnabled = false
                isMyLocationButtonEnabled = false
                isCompassEnabled = false
            }

            // Add markers
            viewModel.allPins.observe(viewLifecycleOwner, Observer { allPins ->
                drawMarkers(allPins)
            })

            // Add polylines
            viewModel.allChains.observe(viewLifecycleOwner, Observer { allChains ->
                drawChains(allChains)
            })

            updateLatLong()
            drawMyLocation(viewModel.fusedLocationData.value)
        }
    }

    private fun drawMarkers(allPins: List<Pin>) {
        for (marker in markersMap.keys) {
            marker.remove()
        }
        markersMap.clear()

        for (pin in allPins) {
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(pin.latitude, pin.longitude))
                    .title(pin.name)
                    .icon(bitmapDescriptorFromVector(PIN_VECTOR_DRAWABLE[pin.color]))
            )
            markersMap[marker] = pin
        }
    }

    private fun drawChains(allChains: List<Chain>) {
        for (polyline in chainsMap.keys) {
            polyline.remove()
        }
        chainsMap.clear()

        val scale = resources.displayMetrics.density
        for (chain in allChains) {
            val chainData = chain.getParsedData()
            val points = chainData.map { it.first }
            val polyline = map.addPolyline(PolylineOptions())
            polyline.apply {
                color = ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[chain.color])
                width = 2 * scale
                jointType = JointType.ROUND
                startCap = ButtCap()
                endCap = startCap
                isClickable = false
            }
            polyline.points = points
            chainsMap[polyline] = chain
        }
    }

    private fun drawMyLocation(locationData: FusedLocationLiveData.LocationData?) {
        if (locationData == null) {
            return
        }

        myLocationMarker?.remove()
        myLocationCircle?.remove()
        val position = LatLng(locationData.latitude, locationData.longitude)
        myLocationMarker = map.addMarker(
            MarkerOptions()
                .position(position)
                .anchor(0.5f, 0.5f)
                .flat(true)
                .zIndex(Float.MAX_VALUE)
                .icon(bitmapDescriptorFromVector(R.drawable.ic_map_my_location))
        )
        myLocationCircle = map.addCircle(
            CircleOptions()
                .center(position)
                .radius(locationData.accuracy.toDouble())
                .strokeColor(ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark))
                .strokeWidth(1f)
                .zIndex(Float.MAX_VALUE - 1)
                .fillColor(ContextCompat.getColor(requireContext(), R.color.myLocationFill))
        )
    }

    private fun updateMyLocation(locationData: FusedLocationLiveData.LocationData?) {
        if (locationData == null) {
            return
        }
        val position = LatLng(locationData.latitude, locationData.longitude)
        if (myLocationMarker != null && myLocationCircle != null) {
            val latLngEvaluator =
                TypeEvaluator<LatLng> { fraction, startValue, endValue ->
                    SphericalUtil.interpolate(
                        startValue,
                        endValue,
                        fraction.toDouble()
                    )
                }

            val doubleEvaluator =
                TypeEvaluator<Double> { fraction, startValue, endValue ->
                    startValue + fraction * (endValue - startValue)
                }

            val markerProperty = Property.of(Marker::class.java, LatLng::class.java, "position")
            val circleCenterProperty = Property.of(Circle::class.java, LatLng::class.java, "center")
            val circleRadiusProperty = Property.of(Circle::class.java, Double::class.java, "radius")

            val markerAnimator =
                ObjectAnimator.ofObject(myLocationMarker, markerProperty, latLngEvaluator, position)
            val circleCenterAnimator = ObjectAnimator.ofObject(
                myLocationCircle,
                circleCenterProperty,
                latLngEvaluator,
                position
            )
            val circleRadiusAnimator = ObjectAnimator.ofObject(
                myLocationCircle,
                circleRadiusProperty,
                doubleEvaluator,
                locationData.accuracy.toDouble()
            )

            markerAnimator.duration = 750
            circleCenterAnimator.duration = 750
            circleRadiusAnimator.duration = 750

            markerAnimator.start()
            circleCenterAnimator.start()
            circleRadiusAnimator.start()
        } else {
            drawMyLocation(locationData)
        }

        if (isShowingMyLocation) {
            val cameraPosition = map.cameraPosition
            val newCameraPosition = CameraPosition.Builder()
                .target(position)
                .bearing(cameraPosition.bearing)
                .tilt(cameraPosition.tilt)
                .zoom(cameraPosition.zoom)
                .build()
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(newCameraPosition),
                750,
                object : GoogleMap.CancelableCallback {
                    override fun onFinish() {}
                    override fun onCancel() {
                        // If animation is cancelled prematurely (i.e through map rotation reset), set
                        // isShowingMyLocation to false and switch on Live Measurement
                        isShowingMyLocation = false
                        toggleLiveMeasurement(true)
                    }

                })
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
                        drawMyLocation(location)
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
        val currentCameraPosition = map.cameraPosition
        val currentZoom = currentCameraPosition.zoom
        val location = viewModel.fusedLocationData.value
        if (location != null) {
            val cameraPosition = CameraPosition.builder()
                .target(LatLng(location.latitude, location.longitude))
                .zoom(if (currentZoom < 10) 15f else currentZoom)
                .tilt(currentCameraPosition.tilt)
                .bearing(currentCameraPosition.bearing)
                .build()
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(cameraPosition),
                500,
                object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        toggleLiveMeasurement(false)
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

        val bearing = map.cameraPosition.bearing
        val tilt = map.cameraPosition.tilt
        if (bearing != 0f || tilt != 0f) {
            toggleMapCompass(true)
            updateMapCompass(bearing, tilt)
        } else {
            toggleMapCompass(false)
        }

        if (viewModel.isInPolylineMode.value!!) {
            drawCurrentPolyline()
        }
    }

    private fun onMarkerClick(marker: Marker): Boolean {
        if (marker == myLocationMarker) {
            onMyLocationPressed()
            return true
        }

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
        }
        if (reason != GoogleMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION || isShowingPin) {
            isShowingMyLocation = false
            toggleLiveMeasurement(true)
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
            binding.mapPinNameText.text = resources.getString(R.string.add_pin_plus)
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

    private fun updateMapCompass(bearing: Float, tilt: Float) {

        if (compassImageDrawable == null) {
            compassImageDrawable = binding.mapCompassImg.drawable
        }
        val matrix = Matrix().apply {
            postRotate(
                -bearing,
                compassImageDrawable!!.intrinsicWidth / 2f,
                compassImageDrawable!!.intrinsicHeight / 2f
            )
            postScale(
                1f, cos(degToRad(tilt)),
                compassImageDrawable!!.intrinsicWidth / 2f,
                compassImageDrawable!!.intrinsicHeight / 2f
            )
        }
        binding.mapCompassImg.imageMatrix = matrix
    }

    private fun onResetMapRotation() {
        val cameraPosition = map.cameraPosition
        val newPosition = CameraPosition(cameraPosition.target, cameraPosition.zoom, 0f, 0f)

        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(newPosition),
            350,
            object : GoogleMap.CancelableCallback {
                override fun onFinish() {
                    toggleMapCompass(false)
                }

                override fun onCancel() {}

            })
    }

    private fun toggleMapCompass(makeVisible: Boolean = true) {

        if (isMapCompassVisible == makeVisible) {
            return
        }

        val mapCompass = binding.mapCompass

        val centreX = mapCompass.width / 2
        val centreY = mapCompass.height / 2

        // Get radius of circle covering entire element
        val radius = hypot(centreX.toDouble(), centreY.toDouble()).toFloat()

        if (makeVisible) {
            try {
                // Attempt to create and start the animation
                val anim = ViewAnimationUtils.createCircularReveal(
                    mapCompass,
                    centreX,
                    centreY,
                    0f,
                    radius
                )
                mapCompass.visibility = View.VISIBLE
                anim.duration = 200
                anim.start()
            } catch (e: Exception) {
                // If view is detached, just toggle visibility without animation
                mapCompass.visibility = View.VISIBLE
            } finally {
                isMapCompassVisible = true
            }
        } else {
            try {
                // Attempt to create and start the animation
                val anim = ViewAnimationUtils.createCircularReveal(
                    mapCompass,
                    centreX,
                    centreY,
                    radius,
                    0f
                )
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        mapCompass.visibility = View.INVISIBLE
                        isMapCompassVisible = false
                    }

                })
                anim.duration = 200
                anim.start()
            } catch (e: Exception) {
                // If view is detached, just toggle visibility without animation
                mapCompass.visibility = View.INVISIBLE
                isMapCompassVisible = false
            }
        }
    }

    private fun enterPolylineMode() {
        viewModel.enterPolylineMode()
        viewModel.currentPolylinePoints.add(map.cameraPosition.target to "")
        binding.mapPolylineUndo.isEnabled = true
        binding.mapPolylineSave.isEnabled = true
    }

    private fun exitPolylineMode() {
        viewModel.exitPolylineMode()
        currentPolyline?.remove()
        currentPolyline = null
        binding.mapPolylineUndo.isEnabled = false
        binding.mapPolylineSave.isEnabled = false
    }

    private fun onAddPolylinePoint() {
        viewModel.currentPolylinePoints.add(map.cameraPosition.target to "")
        drawCurrentPolyline()
    }

    private fun drawCurrentPolyline() {
        if (currentPolyline == null) {
            val polylineOptions = PolylineOptions()
            for (point in viewModel.currentPolylinePoints) {
                polylineOptions.add(point.first)
            }
            polylineOptions.add(map.cameraPosition.target)
            currentPolyline = map.addPolyline(polylineOptions)
            val scale = resources.displayMetrics.density
            currentPolyline?.apply {
                pattern = listOf(
                    Dash(8 * scale),
                    Gap(4 * scale)
                )
                width = 6 * scale
                color = ContextCompat.getColor(requireContext(), R.color.colorPrimaryLight)
                jointType = JointType.ROUND
                endCap = ButtCap()

            }
        } else {
            currentPolyline?.points = viewModel.currentPolylinePoints
                .map { it.first }
                .toMutableList().apply {
                    add(map.cameraPosition.target)
                }
        }
    }

    private fun undoPolyline() {
        viewModel.currentPolylinePoints.let {
            Log.i(this::class.simpleName, "Undoing polyline...")
            Log.i(this::class.simpleName, "$it")
            it.removeAt(it.size - 1)
            drawCurrentPolyline()

            if (it.size == 0) {
                exitPolylineMode()
            }
        }
    }

    private fun onSavePolyline() {
        viewModel.addChain(Chain("test", viewModel.currentPolylinePoints.toChainDataString()))
        exitPolylineMode()
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
