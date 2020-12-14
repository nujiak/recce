package com.nujiak.recce.fragments

import android.animation.*
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.maps.android.SphericalUtil
import com.nujiak.recce.MainViewModel
import com.nujiak.recce.R
import com.nujiak.recce.database.*
import com.nujiak.recce.databinding.FragmentMapBinding
import com.nujiak.recce.livedatas.FusedLocationLiveData
import com.nujiak.recce.utils.*
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.cos
import kotlin.math.hypot

@AndroidEntryPoint
class MapFragment : Fragment(), OnMapReadyCallback,
    ActivityCompat.OnRequestPermissionsResultCallback {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentMapBinding
    private var mapMgr: MapManager? = null
    private var coordSysId = 0
    private var angleUnitId = 0

    private var currentPinColor = 2

    private var isCheckpointInfobarVisible = false
    private var isLiveMeasurementVisible = false
    private var isMapCompassVisible = false
    private var compassImageDrawable: Drawable? = null

    private var isChainControlsVisible = false

    private lateinit var numberFormat: NumberFormat

    private var lastDirectionUpdate = 0L

    companion object {

        const val MAP_TYPE_KEY = "map_type"
        const val CHAINS_GUIDE_SHOWN_KEY = "chains_guide_shown"

        // Lat Lng evaluator for animating myLocationMarker and myLocationCircle positions
        private val latLngEvaluator by lazy {
            TypeEvaluator<LatLng> { fraction, startValue, endValue ->
                SphericalUtil.interpolate(
                    startValue,
                    endValue,
                    fraction.toDouble()
                )
            }
        }

        // Lat Lng evaluator for animating myLocationCircle radius
        private val doubleEvaluator by lazy {
            TypeEvaluator<Double> { fraction, startValue, endValue ->
                startValue + fraction * (endValue - startValue)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        // Set up data binding
        binding = FragmentMapBinding.inflate(inflater, container, false)

        // Set up MapView
        val mapView = binding.mapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        // Set up pin-adding sequence
        viewModel.toAddPinFromMap.observe(viewLifecycleOwner, {
            if (it) onAddPinFromMap()
        })
        binding.mapFab.setOnClickListener { onAddPinFromMap() }

        // Set up custom map controls
        binding.mapZoomInButton.setOnClickListener { mapMgr?.zoomIn() }
        binding.mapZoomOutButton.setOnClickListener { mapMgr?.zoomOut() }
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
            updateFab(color)
        }

        binding.mapLiveGrids.setOnClickListener { viewModel.openSettings() }

        binding.mapPolylineAdd.apply {
            setOnClickListener { onAddPolylinePoint() }
            setOnLongClickListener {
                onAddPolylineNamedPoint()
                true
            }
        }
        binding.mapChainFab.apply {
            setOnClickListener { onAddPolylinePoint() }
            setOnLongClickListener {
                onAddPolylineNamedPoint()
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

        viewModel.isInPolylineMode.observe(viewLifecycleOwner, { isInPolylineMode ->
            if (isInPolylineMode) {
                onEnterPolylineMode()
            } else {
                onExitPolylineMode()
            }
        })

        viewModel.toUndoPolyline.observe(viewLifecycleOwner, { toUndoPolyline ->
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
        viewModel.coordinateSystem.observe(viewLifecycleOwner, {
            coordSysId = it
            updateCardGridSystem(coordSysId)
            updateGrids()
        })
        viewModel.angleUnit.observe(viewLifecycleOwner, {
            angleUnitId = it
            updateLiveMeasurements()
        })

        // Set up Map Type toggle buttons
        binding.mapNormalType.setOnClickListener {
            mapMgr?.mapType = GoogleMap.MAP_TYPE_NORMAL

        }
        binding.mapHybridType.setOnClickListener {
            mapMgr?.mapType = GoogleMap.MAP_TYPE_HYBRID
        }
        binding.mapSatelliteType.setOnClickListener {
            mapMgr?.mapType = GoogleMap.MAP_TYPE_SATELLITE
        }

        // NumberFormat used to format distances in Live Measurements
        numberFormat = NumberFormat.getNumberInstance(Locale.US)
        numberFormat.minimumFractionDigits = 1
        numberFormat.maximumFractionDigits = 1

        // Set up Map rotation reset
        binding.mapCompass.setOnClickListener { mapMgr?.resetMapRotation() }

        viewModel.chainsGuideShown =
            viewModel.sharedPreference.getBoolean(CHAINS_GUIDE_SHOWN_KEY, false)

        return binding.root
    }

    override fun onMapReady(mMap: GoogleMap?) {
        if (mMap != null) {
            mapMgr = MapManager(mMap)

            val newMapType =
                viewModel.sharedPreference.getInt(MAP_TYPE_KEY, GoogleMap.MAP_TYPE_HYBRID)
            mapMgr?.mapType = newMapType
            binding.mapTypeGroup.check(
                when (newMapType) {
                    GoogleMap.MAP_TYPE_NORMAL -> R.id.map_normal_type
                    GoogleMap.MAP_TYPE_HYBRID -> R.id.map_hybrid_type
                    GoogleMap.MAP_TYPE_SATELLITE -> R.id.map_satellite_type
                    else -> R.id.map_normal_type
                }
            )

            // Set up show pin and checkpoint sequence
            viewModel.pinInFocus.observe(viewLifecycleOwner, { pin -> focusOn(pin) })
            viewModel.chainInFocus.observe(viewLifecycleOwner, { chain -> focusOn(chain) })

            if (viewModel.isLocationGranted) {
                onLocPermGranted()
            }

            // Observe location to update Live Measurement
            viewModel.fusedLocationData.observe(viewLifecycleOwner) {
                updateLiveMeasurements()
                mapMgr?.updateMyLocation(it)
            }
            viewModel.rotationLiveData.observe(viewLifecycleOwner) {
                val marker = mapMgr?.myLocationDirection
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastDirectionUpdate > 100 && marker != null) {
                    var newRotation = it.azimuth * 180 / Math.PI.toFloat() - 90
                    val currentRotation = marker.rotation
                    val difference = newRotation - currentRotation

                    // Correction for rotation past 0 or 360 degrees
                    newRotation = when {
                        difference > 180 -> currentRotation - (360 - difference)
                        difference < -180 -> currentRotation + (360 + difference)
                        else -> newRotation
                    }

                    ValueAnimator.ofObject(FloatEvaluator(), currentRotation, newRotation).apply {
                        duration = 100
                        addUpdateListener { valAnim ->
                            marker.rotation = valAnim.animatedValue as Float
                        }
                        interpolator = LinearInterpolator()
                        start()
                    }
                    lastDirectionUpdate = currentTime
                }
            }

            // Add markers
            viewModel.allPins.observe(viewLifecycleOwner, { allPins ->
                mapMgr?.drawMarkers(allPins)
                toggleCheckpointInfobar(false)
            })

            // Add polylines
            viewModel.allChains.observe(viewLifecycleOwner, { allChains ->
                mapMgr?.drawChains(allChains)
                toggleCheckpointInfobar(false)
            })

            // Draw current polyline if available. This is needed
            // to restore the polyline after a rotation change
            mapMgr?.drawCurrentPolyline(true)

            updateGrids()
            mapMgr?.drawMyLocation(viewModel.fusedLocationData.value)
        }
    }

    private fun onMyLocationPressed(resetRotation: Boolean) {
        toggleCheckpointInfobar(false)
        mapMgr?.goToMyLocation(resetRotation)
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
                    mapMgr?.moveTo(
                        target = LatLng(location.latitude, location.longitude),
                        zoom = 15f,
                        duration = 0
                    )
                    updateGrids()
                    removeFocus()
                    mapMgr?.drawMyLocation(location)
                    viewModel.fusedLocationData.removeObserver(this)
                }
            })
    }

    private fun onCameraMoveStarted(reason: Int) {
        if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
            removeFocus()
        }
        if (reason != GoogleMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION || mapMgr?.isShowingPin == true) {
            mapMgr?.isShowingMyLocation = false
            toggleLiveMeasurement(true)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateGrids(latitude: Double? = null, longitude: Double? = null) {
        if (latitude != null && longitude != null) {
            binding.mapCurrentGrids.text = getGridString(latitude, longitude, coordSysId, resources)
            updateLiveMeasurements(latitude, longitude)
        } else if (mapMgr != null && mapMgr?.isShowingPin != true) {
            // Update grids and live measurement only if not showing Pin
            val cameraTarget = mapMgr!!.cameraPosition.target
            val lat = cameraTarget.latitude
            val lng = cameraTarget.longitude
            binding.mapCurrentGrids.text = getGridString(lat, lng, coordSysId, resources)
            updateLiveMeasurements(lat, lng)
        }
    }

    private fun updateFab(color: Int? = null) {
        if (color != null) {
            animateColor(binding.mapFab.backgroundTintList, color, 150) { intermediateColor ->
                binding.mapFab.backgroundTintList = ColorStateList.valueOf((intermediateColor))
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateLiveMeasurements(lat: Double? = null, lng: Double? = null) {
        val fusedLocationData = viewModel.fusedLocationData.value ?: return
        var targetLat = lat
        var targetLng = lng
        if ((targetLat == null || targetLng == null)) {
            if (mapMgr != null) {
                val mapTarget = mapMgr!!.cameraPosition.target
                targetLat = mapTarget.latitude
                targetLng = mapTarget.longitude
            } else {
                return
            }
        }

        val myLatLng = LatLng(fusedLocationData.latitude, fusedLocationData.longitude)
        val targetLatLng = LatLng(targetLat, targetLng)

        val distance = SphericalUtil.computeDistanceBetween(myLatLng, targetLatLng)
        var direction = SphericalUtil.computeHeading(myLatLng, targetLatLng)
        if (direction < 0) {
            direction += 360
        }

        binding.mapCurrentDistance.text = distance.formatAsDistanceString()
        binding.mapCurrentDirection.text = getAngleString(degToRad(direction), angleUnitId, false)
    }

    private fun updateCardGridSystem(coordSysId: Int) {
        binding.mapGridSystem.text =
            resources.getStringArray(R.array.coordinate_systems)[coordSysId]
    }

    private fun onAddPinFromMap(): Boolean {
        val mapMgr = this.mapMgr ?: return true
        val cameraPosition = mapMgr.cameraPosition
        val target = cameraPosition.target
        val pinLat = target.latitude.toString().toDouble()
        val pinLong = target.longitude.toString().toDouble()

        val newPin = Pin("", pinLat, pinLong, currentPinColor)
        viewModel.openPinCreator(newPin)
        return true
    }

    private fun focusOn(lat: Double, lng: Double, color: Int) {
        toggleLiveMeasurement(true)

        // update currentPinColor
        currentPinColor = color

        // Set card background color
        val cardColor = ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[color])
        updateFab(cardColor)

        // Set card coordinates
        updateGrids(lat, lng)
    }

    private fun focusOn(checkpoint: ChainNode) {
        updateCheckpointInfobar(checkpoint)
        toggleCheckpointInfobar(true)
        val parentChain = checkpoint.parentChain
        mapMgr?.moveTo(checkpoint)
        focusOn(
            checkpoint.position.latitude,
            checkpoint.position.longitude,
            parentChain?.color ?: currentPinColor,
        )
    }

    private fun focusOn(pin: Pin) {
        mapMgr?.moveTo(pin)
        updateCheckpointInfobar(pin)
        toggleCheckpointInfobar(true)
        focusOn(pin.latitude, pin.longitude, pin.color)
    }

    private fun focusOn(chain: Chain) {
        if (chain.cyclical) {
            removeFocus()
            mapMgr?.moveTo(chain)
        } else {
            focusOn(chain.getNodes()[0])
        }
    }

    private fun removeFocus() {
        mapMgr?.removeFocus()
        toggleCheckpointInfobar(false)
    }

    private fun updateCheckpointInfobar(name: String, colorId: Int?) {
        binding.mapCheckpointChain.text = name
        binding.mapCheckpointChain.isSelected = true

        val bgColor =
            if (colorId != null) {
                ContextCompat.getColor(
                    requireContext(), PIN_CARD_BACKGROUNDS[colorId]
                )
            } else {
                ContextCompat.getColor(requireContext(), android.R.color.black)
            }

        animateColor(binding.mapCheckpointInfobar.backgroundTintList, bgColor, 150) {
            binding.mapCheckpointInfobar.backgroundTintList = ColorStateList.valueOf(it)
        }

    }

    private fun updateCheckpointInfobar(checkpoint: ChainNode) {
        val name = if (checkpoint.name.isEmpty()) {
            checkpoint.parentChain?.name ?: getString(R.string.unnamed)
        } else {
            resources.getString(
                R.string.route_checkpoint,
                checkpoint.parentChain?.name ?: getString(R.string.unnamed),
                checkpoint.name
            )
        }

        if (checkpoint.parentChain != null) {
            if (checkpoint.parentChain.cyclical) {
                // Area
                binding.areaIcon.visibility = View.VISIBLE
                binding.routeIcon.visibility = View.INVISIBLE
            } else {
                // Route
                binding.areaIcon.visibility = View.INVISIBLE
                binding.routeIcon.visibility = View.VISIBLE
            }
            binding.mapCheckpointInfobar.setOnClickListener {
                viewModel.showChainInfo(checkpoint.parentChain.chainId)
            }
        } else {
            binding.mapCheckpointInfobar.isClickable = false
        }

        updateCheckpointInfobar(name, checkpoint.parentChain?.color)
    }

    private fun updateCheckpointInfobar(pin: Pin) {
        updateCheckpointInfobar(pin.name, pin.color)

        binding.areaIcon.visibility = View.INVISIBLE
        binding.routeIcon.visibility = View.INVISIBLE

        binding.mapCheckpointInfobar.setOnClickListener { viewModel.showPinInfo(pin.pinId) }
    }

    private fun toggleCheckpointInfobar(makeVisible: Boolean = true) {
        if (isCheckpointInfobarVisible == makeVisible) {
            return
        }

        val checkpointInfobar = binding.mapCheckpointInfobar

        val centreX = checkpointInfobar.width / 2
        val centreY = checkpointInfobar.height

        // Get radius of circle covering entire element
        val radius = hypot(centreX.toDouble(), centreY.toDouble()).toFloat()

        if (makeVisible) {
            try {
                // Attempt to create and start the animation
                val anim = ViewAnimationUtils.createCircularReveal(
                    checkpointInfobar,
                    centreX,
                    0, // Start animation from above
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
                    0, // Start animation from above
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

        // Return with no changes if LiveMeasurement visibility is already equal to make_visible
        // or if location is not granted.
        if (isLiveMeasurementVisible == makeVisible
            || (makeVisible && !viewModel.isLocationGranted)
        ) {
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
        binding.mapPolylineAdd.isEnabled = true
        binding.mapPolylineSave.isEnabled = false // Only enable when >1 node
        binding.mapChainFab.hide()
        toggleChainControls(true)
    }

    private fun onExitPolylineMode() {
        mapMgr?.exitPolylineMode()
        binding.mapChainFab.show()

        // Disable all buttons
        binding.mapPolylineUndo.isEnabled = false
        binding.mapPolylineAdd.isEnabled = false
        binding.mapPolylineSave.isEnabled = false

        toggleChainControls(false)
    }

    private fun onAddPolylinePoint(name: String = "") {
        val mapMgr = this.mapMgr ?: return
        if (!viewModel.isInPolylineMode.value!!) {
            viewModel.enterPolylineMode()
            if (!viewModel.chainsGuideShown) {
                showChainsGuide()
            }
        }
        viewModel.currentPolylinePoints.let {
            it.add(ChainNode(name, mapMgr.cameraPosition.target))
            if (it.size >= 2) {
                binding.mapPolylineSave.isEnabled = true
            }
        }
        mapMgr.drawCurrentPolyline(true)
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
                    it.contains(Regex("[;,]")) -> {
                        inputLayout.error = getString(R.string.checkpoint_invalid_error)
                    }
                    it.length > 20 -> {
                        inputLayout.error = getString(R.string.checkpoint_name_too_long_error)
                    }
                    else -> {
                        // Group name is valid, add to ArrayAdapter and set in AutoCompleteTextView
                        onAddPolylinePoint(it.toString())

                        // Enter Polyline mode if not already inside
                        if (!viewModel.isInPolylineMode.value!!) {
                            viewModel.enterPolylineMode()
                            if (!viewModel.chainsGuideShown) {
                                showChainsGuide()
                            }
                        }
                        alertDialog.dismiss()
                    }
                }
            }

        }
    }

    private fun onPolylineClick(polyline: Polyline) {
        val mapMgr = this.mapMgr ?: return
        mapMgr.polylinesMap[polyline]?.let {
            viewModel.showChainInfo(it.chainId)
        }
    }

    private fun onPolygonClick(polygon: Polygon) {
        val mapMgr = this.mapMgr ?: return
        mapMgr.polygonsMap[polygon]?.let {
            viewModel.showChainInfo(it.chainId)
        }
    }

    private fun undoPolyline() {
        viewModel.currentPolylinePoints.let {
            it.removeAt(it.size - 1)
            mapMgr?.drawCurrentPolyline(true)

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


    private fun toggleChainControls(makeVisible: Boolean = true) {

        // Return with no changes if LiveMeasurement visibility is already equal to make_visible
        // or if location is not granted.
        if (isChainControlsVisible == makeVisible) {
            return
        }

        val chainControls = binding.mapPolylineControlsCardView

        val centreX = chainControls.width / 2
        val centreY = chainControls.height

        // Get radius of circle covering entire element
        val radius = hypot(centreX.toDouble(), centreY.toDouble()).toFloat()

        if (makeVisible) {
            try {
                // Attempt to create and start the animation
                val anim = ViewAnimationUtils.createCircularReveal(
                    chainControls,
                    centreX,
                    centreY,
                    0f,
                    radius
                )
                chainControls.visibility = View.VISIBLE
                anim.duration = 200
                anim.start()
            } catch (e: Exception) {
                // If view is detached, just toggle visibility without animation
                chainControls.visibility = View.VISIBLE
            } finally {
                isChainControlsVisible = true
            }
        } else {
            try {
                // Attempt to create and start the animation
                val anim = ViewAnimationUtils.createCircularReveal(
                    chainControls,
                    centreX,
                    centreY,
                    radius,
                    0f
                )
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        chainControls.visibility = View.INVISIBLE
                        isChainControlsVisible = false
                    }

                })
                anim.duration = 200
                anim.start()
            } catch (e: Exception) {
                // If view is detached, just toggle visibility without animation
                chainControls.visibility = View.INVISIBLE
                isChainControlsVisible = false
            }
        }
    }

    private fun onCameraMove() {
        val mapMgr = mapMgr ?: return

        updateGrids()

        val position = mapMgr.cameraPosition
        val bearing = position.bearing
        val tilt = position.tilt
        if (bearing != 0f || tilt != 0f) {
            toggleMapCompass(true)
            updateMapCompass(bearing, tilt)
        } else {
            toggleMapCompass(false)
        }

        if (viewModel.isInPolylineMode.value!!) {
            mapMgr.drawCurrentPolyline()
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

    private inner class MapManager(var map: GoogleMap) {
        var markersMap = HashMap<Marker, Pin>()
        var polylinesMap = HashMap<Polyline, Chain>()
        var polygonsMap = HashMap<Polygon, Chain>()
        var checkpointsMap = HashMap<Marker, ChainNode>()

        var myLocationMarker: Marker? = null
        var myLocationCircle: Circle? = null
        var myLocationDirection: Marker? = null
        var currentPolyline: Polyline? = null
        val currentPolylineMarkers = mutableListOf<Marker>()

        var isShowingPin = false
        var isShowingMyLocation = false
        var isShowingCheckpoint = false
        var zoomStack = 0f

        var isAnimating = false

        var mapType: Int = map.mapType
            set(value) {
                if (field != value) {
                    map.mapType = value
                    viewModel.sharedPreference.edit().putInt(MAP_TYPE_KEY, value).apply()
                    field = value
                }
            }

        private var targetPosition: CameraPosition = map.cameraPosition
        val cameraPosition: CameraPosition
            get() = map.cameraPosition

        init {
            map.apply {
                setOnCameraMoveListener { onCameraMove() }
                setOnMarkerClickListener { onMarkerClick(it) }
                setOnCameraMoveStartedListener { onCameraMoveStarted(it) }
                setOnPolylineClickListener { onPolylineClick(it) }
                setOnPolygonClickListener { onPolygonClick(it) }
                isIndoorEnabled = false
                setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style))
                uiSettings.isZoomControlsEnabled = true
            }

            map.uiSettings.apply {
                isZoomControlsEnabled = false
                isMyLocationButtonEnabled = false
                isCompassEnabled = false
            }
        }

        fun moveTo(
            target: LatLng? = null,
            bearing: Float? = null,
            tilt: Float? = null,
            zoom: Float? = null,
            duration: Int = 350,
            onAnimFinish: () -> Unit = { },
            onAnimCancel: () -> Unit = { }
        ) {
            // If starting from static position, record current position as target
            if (!isAnimating) {
                targetPosition = map.cameraPosition
            }

            // Fetch undeclared parameters from target position
            val tgt = target ?: targetPosition.target
            val zm = zoom ?: if (map.cameraPosition.zoom < 10f) 15f else map.cameraPosition.zoom
            val brng = bearing ?: targetPosition.bearing
            val tlt = tilt ?: targetPosition.tilt

            // Create new camera position
            val cameraPosition = CameraPosition.builder()
                .target(tgt)
                .zoom(zm)
                .bearing(brng)
                .tilt(tlt)
                .build()

            targetPosition = cameraPosition

            if (duration > 0) {
                isAnimating = true
                map.animateCamera(
                    CameraUpdateFactory.newCameraPosition(cameraPosition),
                    duration,
                    object : GoogleMap.CancelableCallback {
                        override fun onFinish() {
                            onAnimFinish()
                            isAnimating = false
                        }

                        override fun onCancel() {
                            onAnimCancel()
                            isAnimating = false
                        }
                    }
                )
            } else {
                map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                isAnimating = false
            }
        }

        fun moveTo(pin: Pin) {
            isShowingPin = true
            isShowingCheckpoint = false
            isShowingMyLocation = false
            moveTo(target = LatLng(pin.latitude, pin.longitude))
        }

        fun moveTo(chain: Chain) {
            isShowingCheckpoint = false
            isShowingPin = false
            isShowingMyLocation = false
            val nodes = chain.getNodes()
            if (chain.cyclical) {
                // Area
                val bounds = LatLngBounds.builder().apply {
                    for (node in nodes) {
                        include(node.position)
                    }
                }.build()
                val padding = 64 * resources.displayMetrics.density
                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, padding.toInt()),
                    350,
                    null
                )

            } else {
                // Route
                moveTo(nodes[0])
            }
        }

        fun moveTo(node: ChainNode) {
            isShowingPin = true
            isShowingCheckpoint = true
            isShowingMyLocation = false
            moveTo(target = LatLng(node.position.latitude, node.position.longitude))
        }

        private fun onMarkerClick(marker: Marker): Boolean {
            // Marker is My Location custom marker
            if (marker == myLocationMarker) {
                onMyLocationPressed(false)
                return true
            }

            // Marker represents a save polyline checkpoint
            checkpointsMap[marker]?.let { node ->
                focusOn(node)
                return true
            }

            // Marker represents a current polyline checkpoint
            if (currentPolylineMarkers.contains(marker)) {
                val position = marker.position
                val checkpointNode = ChainNode(marker.title, position, null)
                focusOn(checkpointNode)
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

        fun drawMarkers(allPins: List<Pin>) {
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

        fun drawChains(allChains: List<Chain>) {
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
                    val color =
                        ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[chain.color])
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
                        color =
                            ContextCompat.getColor(
                                requireContext(),
                                PIN_CARD_BACKGROUNDS[chain.color]
                            )
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

        fun drawMyLocation(locationData: FusedLocationLiveData.LocationData?) {
            if (locationData == null) {
                return
            }

            myLocationMarker?.remove()
            myLocationCircle?.remove()
            myLocationDirection?.remove()
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
            myLocationDirection = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .anchor(-0.2f, .5f)
                    .flat(true)
                    .zIndex(Float.MAX_VALUE - 2)
                    .icon(bitmapDescriptorFromVector(R.drawable.ic_twotone_play_arrow_24))
            )
        }

        fun updateMyLocation(locationData: FusedLocationLiveData.LocationData) {
            val position = LatLng(locationData.latitude, locationData.longitude)
            if (myLocationMarker != null && myLocationCircle != null && myLocationDirection != null) {
                // Marker positions animator
                ValueAnimator.ofObject(latLngEvaluator, myLocationMarker!!.position, position)
                    .apply {
                        duration = 750
                        addUpdateListener {
                            val newPosition = it.animatedValue as LatLng
                            if (isShowingMyLocation) {
                                moveTo(target = newPosition, duration = 0)
                            }
                            myLocationMarker?.position = newPosition
                            myLocationDirection?.position = newPosition
                            myLocationCircle?.center = newPosition
                        }
                        interpolator = AccelerateDecelerateInterpolator()
                        start()
                    }
                // MyLocation accuracy radius animator
                ObjectAnimator.ofObject(
                    doubleEvaluator, myLocationCircle!!.radius, locationData.accuracy.toDouble()
                ).apply {
                    duration = 750
                    addUpdateListener {
                        myLocationCircle?.radius = it.animatedValue as Double
                    }
                    start()
                }
            } else {
                drawMyLocation(locationData)
            }
        }

        fun drawCurrentPolyline(redrawCheckpoints: Boolean = false) {
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

        private val resetZoomStack = { zoomStack = 0f }

        fun zoomIn() {
            zoomStack = if (zoomStack < 0) 1f else zoomStack + 1

            val currentZoomStack = zoomStack

            val onCancel = {
                if (zoomStack == currentZoomStack) {
                    resetZoomStack()
                }
            }

            moveTo(
                zoom = targetPosition.zoom + zoomStack,
                duration = 300,
                onAnimFinish = resetZoomStack,
                onAnimCancel = onCancel
            )
        }

        fun zoomOut() {
            zoomStack = if (zoomStack > 0) -1f else zoomStack - 1

            val currentZoomStack = zoomStack

            val onCancel = {
                if (zoomStack == currentZoomStack) {
                    resetZoomStack()
                }
            }

            moveTo(
                zoom = targetPosition.zoom + zoomStack,
                duration = 300,
                onAnimFinish = resetZoomStack,
                onAnimCancel = onCancel
            )
        }

        private val hideLiveMeasurement = { toggleLiveMeasurement(false) }
        fun goToMyLocation(resetRotation: Boolean) {
            isShowingMyLocation = true
            val location = viewModel.fusedLocationData.value
            if (location != null) {
                if (resetRotation) {
                    moveTo(
                        target = LatLng(location.latitude, location.longitude),
                        tilt = 0f,
                        bearing = 0f,
                        duration = 500,
                        onAnimFinish = hideLiveMeasurement
                    )
                } else {
                    moveTo(
                        target = LatLng(location.latitude, location.longitude),
                        duration = 500,
                        onAnimFinish = hideLiveMeasurement
                    )
                }
                removeFocus()
            }
        }

        fun resetMapRotation() = moveTo(tilt = 0f, bearing = 0f)

        fun exitPolylineMode() {
            currentPolyline?.remove()
            currentPolyline = null
            currentPolylineMarkers.map { it.remove() }
            currentPolylineMarkers.clear()
        }

        fun removeFocus() {
            isShowingPin = false
            isShowingCheckpoint = false
        }
    }
}
