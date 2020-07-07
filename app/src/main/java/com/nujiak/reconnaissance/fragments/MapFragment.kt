package com.nujiak.reconnaissance.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.TypeEvaluator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Property
import android.view.LayoutInflater
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
    private var coordSysId = 0

    private var currentPinColor = 0
    private var markersMap = HashMap<Marker, Pin>()
    private var polylinesMap = HashMap<Polyline, Chain>()
    private var polygonsMap = HashMap<Polygon, Chain>()
    private var checkpointsMap = HashMap<Marker, ChainNode>()

    private var myLocationMarker: Marker? = null
    private var myLocationCircle: Circle? = null
    private var currentPolyline: Polyline? = null
    private val currentPolylineMarkers = mutableListOf<Marker>()

    private var isShowingPin = false
    private var isShowingMyLocation = false
    private var isShowingCheckpoint = false
    private var zoomStack = 0f

    private var isCheckpointInfobarVisible = false
    private var isLiveMeasurementVisible = false
    private var isMapCompassVisible = false
    private var compassImageDrawable: Drawable? = null

    private lateinit var numberFormat: NumberFormat

    private var currentMapType = GoogleMap.MAP_TYPE_HYBRID

    companion object {

        const val MAP_TYPE_KEY = "map_type"
        const val CHAINS_GUIDE_SHOWN_KEY = "chains_guide_shown"
    }

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

        // Set up show pin and checkpoint sequence
        viewModel.pinInFocus.observe(viewLifecycleOwner, Observer { pin -> moveMapTo(pin) })
        viewModel.chainInFocus.observe(viewLifecycleOwner, Observer { node -> moveMapTo(node)})

        // Set up custom map controls
        binding.mapZoomInButton.setOnClickListener { onZoomIn() }
        binding.mapZoomOutButton.setOnClickListener { onZoomOut() }
        binding.mapLocationButton.apply {
            setOnClickListener { onMyLocationPressed(false) }
            setOnLongClickListener { onMyLocationPressed(true); true }
        }
        // Set card background to color of last added pin
        viewModel.lastPin.value.let { pin ->
            if (pin != null) {
                currentPinColor = pin.color
            }
            val color =
                ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[currentPinColor])
            binding.mapDetailsCardView.setCardBackgroundColor(color)
        }

        binding.mapPolylineAdd.apply {
            setOnClickListener {
                onAddPolylinePoint()
                if (!viewModel.isInPolylineMode.value!!) {
                    viewModel.enterPolylineMode()
                    if (!viewModel.chainsGuideShown) {
                        showChainsGuide()
                    }
                }
            }
            setOnLongClickListener {
                onAddPolylineNamedPoint()
                if (!viewModel.isInPolylineMode.value!!) {
                    viewModel.enterPolylineMode()
                    if (!viewModel.chainsGuideShown) {
                        showChainsGuide()
                    }
                }
                true
            }
        }


        binding.mapPolylineUndo.apply {
            setOnClickListener { undoPolyline() }
            setOnLongClickListener {
                viewModel.exitPolylineMode()
                true
            }
        }

        viewModel.isInPolylineMode.observe(viewLifecycleOwner, Observer { isInPolylineMode ->
            if (isInPolylineMode) {
                onEnterPolylineMode()
            } else {
                onExitPolylineMode()
            }
        })

        viewModel.toUndoPolyline.observe(viewLifecycleOwner, Observer { toUndoPolyline ->
            if (toUndoPolyline) {
                undoPolyline()
            }
        })

        binding.mapPolylineSave.setOnClickListener { onSavePolyline() }

        // Show chains guide if device was rotated mid-guide
        if (viewModel.isInPolylineMode.value!! && !viewModel.chainsGuideShown) {
            showChainsGuide()
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

        // Set up Map rotation reset
        binding.mapCompass.setOnClickListener { onResetMapRotation() }

        viewModel.chainsGuideShown =
            viewModel.sharedPreference.getBoolean(CHAINS_GUIDE_SHOWN_KEY, false)

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

            map.apply {
                setOnCameraMoveListener { onCameraMove() }
                setOnMarkerClickListener { onMarkerClick(it) }
                setOnCameraMoveStartedListener { onCameraMoveStarted(it) }
                setOnPolylineClickListener { onPolylineClick(it) }
                setOnPolygonClickListener { onPolygonClick(it) }
                isIndoorEnabled = false
            }
            val uiSetting = map.uiSettings
            uiSetting.isZoomControlsEnabled = true

            if (viewModel.isLocationGranted) {
                onLocPermGranted()
            }

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

            // Draw current polyline is available. This is needed
            // to restore the polyline after a rotation change
            drawCurrentPolyline(true)

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
        polylinesMap.keys.forEach { it.remove() }
        polylinesMap.clear()

        checkpointsMap.keys.forEach { it.remove() }
        checkpointsMap.clear()

        polygonsMap.keys.forEach { it.remove() }
        polygonsMap.clear()

        val scale = resources.displayMetrics.density
        for (chain in allChains) {
            val chainNodes = chain.getNodes()
            val points = chainNodes.map { it.position }
            if (chain.cyclical) {
                val color = ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[chain.color])
                val polygonOptions = PolygonOptions()
                points.forEach { polygonOptions.add(it) }
                polygonOptions.apply {
                    strokeColor(color)
                    strokeWidth(2 * scale)
                    fillColor(withAlpha(color, 50))
                    strokeJointType(JointType.ROUND)
                    clickable(true)
                    geodesic(true)
                }
                val polygon = map.addPolygon(polygonOptions)
                polygonsMap[polygon] = chain
            } else {
                val polyline = map.addPolyline(PolylineOptions())
                polyline.apply {
                    color = ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[chain.color])
                    width = 4 * scale
                    jointType = JointType.ROUND
                    startCap = ButtCap()
                    endCap = startCap
                    isClickable = true
                    isGeodesic = true
                }
                polyline.points = points
                polylinesMap[polyline] = chain
            }

            for (node in chainNodes) {
                if (node.isCheckpoint) {
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(node.position)
                            .anchor(0.5f, 0.5f)
                            .flat(true)
                            .icon(bitmapDescriptorFromVector(R.drawable.ic_map_checkpoint))
                    )
                    checkpointsMap[marker] = node
                }
            }
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
        binding.mapLocationButton.isEnabled = true
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
    private fun onLocPermGranted() {

        binding.mapLocationButton.isEnabled = true
        // Observe my location once
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

    @SuppressLint("SetTextI18n")
    private fun updateLatLong(latitude: Double? = null, longitude: Double? = null) {
        val cameraTarget = map.cameraPosition.target
        val lat = latitude ?: cameraTarget.latitude
        val lng = longitude ?: cameraTarget.longitude
        binding.mapLatLngText.text = "%.6f %.6f".format(lat, lng)
        binding.mapGridText.text = getGridString(lat, lng, coordSysId, resources)

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

        binding.mapCurrentDistance.text = distance.formatAsDistanceString()
        binding.mapCurrentDirection.text =
            "${directionMils.roundToInt().toString().padStart(4, '0')} mils"
    }

    private fun updateCardGridSystem(coordSysId: Int) {
        binding.mapGridSystem.text =
            resources.getStringArray(R.array.coordinate_systems)[coordSysId]
    }

    private fun onAddPinFromMap(): Boolean {
        if (isShowingPin && !isShowingCheckpoint) {
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
        zoomStack = if (zoomStack < 0) 1f else zoomStack + 1

        val currentZoomStack = zoomStack

        map.animateCamera(CameraUpdateFactory.zoomBy(zoomStack), 300, object : GoogleMap.CancelableCallback {
            override fun onFinish() { zoomStack = 0f }

            override fun onCancel() {
                // Call onFinish() if animation is cancelled NOT due to another call to zoom.
                if (zoomStack == currentZoomStack) {
                    onFinish()
                }
            }

        })
    }

    private fun onZoomOut() {
        zoomStack = if (zoomStack > 0) -1f else zoomStack - 1

        val currentZoomStack = zoomStack

        map.animateCamera(CameraUpdateFactory.zoomBy(zoomStack), 300,  object : GoogleMap.CancelableCallback {
            override fun onFinish() { zoomStack = 0f }

            override fun onCancel() {
                // Call onFinish() if animation is cancelled NOT due to another call to zoom.
                if (zoomStack == currentZoomStack) {
                    onFinish()
                }
            }
        })
    }

    private fun onMyLocationPressed(resetRotation: Boolean) {
        isShowingMyLocation = true
        val currentCameraPosition = map.cameraPosition
        val currentZoom = currentCameraPosition.zoom
        val location = viewModel.fusedLocationData.value
        if (location != null) {
            val cameraPosition = CameraPosition.builder()
                .target(LatLng(location.latitude, location.longitude))
                .zoom(if (currentZoom < 10) 15f else currentZoom)
                .tilt(if (resetRotation) 0f else currentCameraPosition.tilt)
                .bearing(if (resetRotation) 0f else currentCameraPosition.bearing)
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
            resetCard()
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
        // Marker is My Location custom marker
        if (marker == myLocationMarker) {
            onMyLocationPressed(false)
            return true
        }

        // Marker represents a save polyline checkpoint
        checkpointsMap[marker]?.let { node ->
            moveMapTo(node)
            return true
        }

        // Marker represents a current polyline checkpoint
        if (currentPolylineMarkers.contains(marker)) {
            val position = marker.position
            val checkpointNode = ChainNode(marker.title, position, null)
            moveMapTo(checkpointNode)
            return true
        }

        // Marker represents a saved Pin
        markersMap[marker]?.let { pin ->
            viewModel.showPinOnMap(pin)
            toggleLiveMeasurement(true)
            return true
        }

        return true
    }

    private fun onCameraMoveStarted(reason: Int) {
        if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
            resetCard()
        }
        if (reason != GoogleMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION || isShowingPin) {
            isShowingMyLocation = false
            toggleLiveMeasurement(true)
        }
    }

    private fun moveMapTo(pin: Pin) {
        moveMapTo(pin.latitude, pin.longitude)
        showInCard(pin)
    }

    private fun moveMapTo(chain: Chain) {
        val nodes = chain.getNodes()
        if (chain.cyclical) {
            // Area
            val bounds = LatLngBounds.builder().apply {
                for (node in nodes) {
                    include(node.position)
                }
            }.build()
            val padding = 64 * resources.displayMetrics.density
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding.toInt()), 350, null)

        } else {
            // Route
            moveMapTo(nodes[0])
        }
    }

    private fun moveMapTo(node: ChainNode) {
        moveMapTo(node.position.latitude, node.position.longitude)
        showInCard(node)
    }

    private fun moveMapTo(lat: Double, lng: Double, bearing: Float? = null, tilt: Float? = null, zoom: Float? = null) {
        if (!this::map.isInitialized) {
            return
        }
        val currentPosition = map.cameraPosition

        val toBearing = bearing ?: currentPosition.bearing
        val toTilt = tilt ?: currentPosition.tilt
        val toZoom = zoom ?: if (map.cameraPosition.zoom < 10f) 15f else map.cameraPosition.zoom

        val cameraPosition = CameraPosition.builder()
            .target(LatLng(lat, lng))
            .zoom(toZoom)
            .bearing(toBearing)
            .tilt(toTilt)
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 350, null)
    }

    private fun showInCard(lat: Double, lng: Double, group: String, color: Int, name: String?) {
        // update currentPinColor
        currentPinColor = color

        // Set name on card
        binding.mapPinNameText.text = name ?: resources.getString(R.string.add_pin_plus)

        // Set card background color
        val cardColor = ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[color])
        binding.mapDetailsCardView.setCardBackgroundColor(cardColor)

        // Set group
        if (group.isNotEmpty()) {
            binding.mapPinGroup.text = group
            binding.mapPinGroup.setTextColor(cardColor)
            binding.mapPinGroup.visibility = View.VISIBLE
        } else {
            binding.mapPinGroup.visibility = View.INVISIBLE
        }

        // Set card coordinates
        updateLatLong(lat, lng)

        isShowingPin = true
    }

    private fun showInCard(checkpoint: ChainNode) {
        toggleCheckpointInfobar(true)
        isShowingCheckpoint = true
        isShowingPin = true
        updateCheckpointInfobar(checkpoint)
        val parentChain = checkpoint.parentChain
        showInCard(checkpoint.position.latitude,
            checkpoint.position.longitude,
            parentChain?.group ?: "",
            parentChain?.color ?: currentPinColor,
            null
        )

    }

    private fun showInCard(pin: Pin) {
        toggleCheckpointInfobar(false)
        isShowingCheckpoint = false
        showInCard(pin.latitude, pin.longitude, pin.group, pin.color, pin.name)
        isShowingPin = true
    }

    private fun resetCard() {
        binding.mapPinNameText.text = resources.getString(R.string.add_pin_plus)
        binding.mapPinGroup.visibility = View.INVISIBLE
        isShowingPin = false
        isShowingCheckpoint = false
        toggleCheckpointInfobar(false)
    }

    private fun updateCheckpointInfobar(checkpoint: ChainNode) {
        binding.mapCheckpointName.text = checkpoint.name
        binding.mapCheckpointChain.text =
            checkpoint.parentChain?.name ?: getString(R.string.unnamed)

        val color =
            if (checkpoint.parentChain != null) {
                ContextCompat.getColor(
                    requireContext(), PIN_CARD_DARK_BACKGROUNDS[checkpoint.parentChain.color]
                )
            } else {
                ContextCompat.getColor(requireContext(), R.color.colorSurface)
            }

        binding.mapCheckpointInfobar.setCardBackgroundColor(color)

    }

    private fun toggleCheckpointInfobar(makeVisible: Boolean = true) {
        if (isCheckpointInfobarVisible == makeVisible) {
            return
        }

        val checkpointInfobar = binding.mapCheckpointInfobar

        val centreX = checkpointInfobar.width / 2
        val centreY = checkpointInfobar.height / 2

        // Get radius of circle covering entire element
        val radius = hypot(centreX.toDouble(), centreY.toDouble()).toFloat()

        if (makeVisible) {
            try {
                // Attempt to create and start the animation
                val anim = ViewAnimationUtils.createCircularReveal(
                    checkpointInfobar,
                    centreX,
                    centreY,
                    0f,
                    radius
                )
                checkpointInfobar.visibility = View.VISIBLE
                anim.duration = 200
                anim.start()
            } catch (e: Exception) {
                // If view is detached, just toggle visibility without animation
                checkpointInfobar.visibility = View.VISIBLE
            } finally {
                isCheckpointInfobarVisible = true
            }
        } else {
            try {
                // Attempt to create and start the animation
                val anim = ViewAnimationUtils.createCircularReveal(
                    checkpointInfobar,
                    centreX,
                    centreY,
                    radius,
                    0f
                )
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        checkpointInfobar.visibility = View.INVISIBLE
                        isCheckpointInfobarVisible = false
                    }

                })
                anim.duration = 200
                anim.start()
            } catch (e: Exception) {
                // If view is detached, just toggle visibility without animation
                checkpointInfobar.visibility = View.INVISIBLE
                isCheckpointInfobarVisible = false
            }
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

    private fun onEnterPolylineMode() {
        binding.mapPolylineUndo.isEnabled = true
    }

    private fun onExitPolylineMode() {
        currentPolyline?.remove()
        currentPolyline = null
        currentPolylineMarkers.map { it.remove() }
        currentPolylineMarkers.clear()
        binding.mapPolylineUndo.isEnabled = false
        binding.mapPolylineSave.isEnabled = false
    }

    private fun onAddPolylinePoint(name: String = "") {
        viewModel.currentPolylinePoints.let {
            it.add(ChainNode(name, map.cameraPosition.target))
            if (it.size >= 2) {
                binding.mapPolylineSave.isEnabled = true
            }
        }
        drawCurrentPolyline(true)
    }

    private fun onAddPolylineNamedPoint() {
        val alertDialog = AlertDialog.Builder(requireActivity())
            .setView(R.layout.dialog_new_checkpoint)
            .create()
        alertDialog.show()

        // Set up layout and interactions of the dialog
        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val inputLayout = alertDialog.findViewById<TextInputLayout>(R.id.new_checkpoint_input)
        val editText = alertDialog.findViewById<TextInputEditText>(R.id.new_checkpoint_edit_text)
        editText.setOnKeyListener { _, _, _ ->
            editText.text?.let {
                if (it.length <= 12) {
                    inputLayout.error = null
                }
            }
            true
        }

        val posBtn = alertDialog.findViewById<Button>(R.id.new_checkpoint_add_button)
        posBtn.setOnClickListener {
            editText.text?.trim()?.let {
                when {
                    it.contains(',', true) || it.contains(';', true) -> {
                        inputLayout.error = getString(R.string.checkpoint_invalid_error)
                    }
                    it.length > 20 -> {
                        inputLayout.error = getString(R.string.checkpoint_name_too_long_error)
                    }
                    else -> {
                        // Group name is valid, add to ArrayAdapter and set in AutoCompleteTextView
                        onAddPolylinePoint(it.toString())
                        alertDialog.dismiss()
                    }
                }
            }

        }
    }

    private fun onPolylineClick(polyline: Polyline) {
        polylinesMap[polyline]?.let {
            viewModel.openChainCreator(it)
        }
    }

    private fun onPolygonClick(polygon: Polygon) {
        polygonsMap[polygon]?.let {
            viewModel.openChainCreator(it)
        }
    }

    private fun drawCurrentPolyline(redrawCheckpoints: Boolean = false) {
        if (currentPolyline == null) {
            val polylineOptions = PolylineOptions()
            for (point in viewModel.currentPolylinePoints) {
                polylineOptions.add(point.position)
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
                isGeodesic = true
            }
        } else {
            currentPolyline?.points = viewModel.currentPolylinePoints
                .map { it.position }
                .toMutableList().apply {
                    add(map.cameraPosition.target)
                }
        }

        if (redrawCheckpoints) {
            for (marker in currentPolylineMarkers) {
                marker.remove()
            }
            currentPolylineMarkers.clear()

            for (node in viewModel.currentPolylinePoints) {
                if (node.isCheckpoint) {
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(node.position)
                            .anchor(0.5f, 0.5f)
                            .title(node.name)
                            .flat(true)
                            .icon(bitmapDescriptorFromVector(R.drawable.ic_map_checkpoint))
                    )
                    currentPolylineMarkers.add(marker)
                }
            }
        }
    }

    private fun undoPolyline() {
        viewModel.currentPolylinePoints.let {
            it.removeAt(it.size - 1)
            drawCurrentPolyline(true)

            if (it.size == 0) {
                viewModel.exitPolylineMode()
            } else if (it.size < 2) {
                binding.mapPolylineSave.isEnabled = false
            }
        }
    }

    private fun onSavePolyline() {
        viewModel.openChainCreator(Chain("", viewModel.currentPolylinePoints.toChainDataString()))
    }

    private fun showChainsGuide() {
        val builder = AlertDialog.Builder(requireActivity())
        builder.apply {
            setView(R.layout.guide_chains)
            setCancelable(false)
        }
        val alertDialog = builder.create()
        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        alertDialog.show()

        val doneBtn = alertDialog.findViewById<Button>(R.id.guide_chain_done)
        doneBtn.setOnClickListener {
            alertDialog.dismiss()
            viewModel.chainsGuideShown = true
            viewModel.sharedPreference.edit().putBoolean(CHAINS_GUIDE_SHOWN_KEY, true).apply()
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
