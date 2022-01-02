package com.nujiak.recce.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.TypeEvaluator
import android.animation.ValueAnimator
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
import android.util.TypedValue
import android.view.HapticFeedbackConstants
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
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.ButtCap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.maps.android.SphericalUtil
import com.nujiak.recce.MainViewModel
import com.nujiak.recce.R
import com.nujiak.recce.database.Chain
import com.nujiak.recce.database.ChainNode
import com.nujiak.recce.database.Pin
import com.nujiak.recce.databinding.FragmentMapBinding
import com.nujiak.recce.enums.AngleUnit
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.livedatas.FusedLocationLiveData
import com.nujiak.recce.utils.PIN_CARD_BACKGROUNDS
import com.nujiak.recce.utils.PIN_VECTOR_DRAWABLE
import com.nujiak.recce.utils.animate
import com.nujiak.recce.utils.animateColor
import com.nujiak.recce.utils.degToRad
import com.nujiak.recce.utils.dpToPx
import com.nujiak.recce.utils.formatAsDistanceString
import com.nujiak.recce.utils.withAlpha
import dagger.hilt.android.AndroidEntryPoint
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView
import uk.co.deanwild.materialshowcaseview.ShowcaseConfig
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

@AndroidEntryPoint
class MapFragment :
    Fragment(),
    OnMapReadyCallback,
    ActivityCompat.OnRequestPermissionsResultCallback {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentMapBinding
    private var mapMgr: MapManager? = null
    private var coordSys = CoordinateSystem.atIndex(0)
    private var angleUnit = AngleUnit.atIndex(0)

    private var currentPinColor = 2

    private var isCheckpointInfobarVisible = false
    private var isLiveMeasurementVisible = false
    private var isMapCompassVisible = false
    private val compassImageDrawable: Drawable by lazy {
        binding.mapCompassImg.drawable
    }

    private var isChainControlsVisible = false

    private lateinit var numberFormat: NumberFormat

    private var isChainGuideShowing = false
    private val chainShowcaseView: MaterialShowcaseSequence by lazy {
        // Colours needed for showcase
        val maskColour = withAlpha(
            ContextCompat.getColor(
                requireContext(),
                R.color.colorPrimaryDark
            ),
            215
        )
        val white = ContextCompat.getColor(requireContext(), android.R.color.white)

        MaterialShowcaseSequence(activity).apply {

            // Set showcase configs
            setConfig(
                ShowcaseConfig().apply {
                    delay = 100
                    renderOverNavigationBar = true
                    fadeDuration = 300
                }
            )

            // Showcase for add button
            addSequenceItem(
                MaterialShowcaseView.Builder(activity)
                    .setTarget(binding.mapPolylineAdd)
                    .setTitleText(R.string.add)
                    .setContentText(R.string.guide_chains_add)
                    .setContentTextColor(white)
                    .setDismissText(R.string.next)
                    .setMaskColour(maskColour)
                    .build()
            )
            // Showcase for undo button
            addSequenceItem(
                MaterialShowcaseView.Builder(activity)
                    .setTarget(binding.mapPolylineUndo)
                    .setTitleText(R.string.undo)
                    .setContentText(R.string.guide_chains_undo)
                    .setContentTextColor(white)
                    .setDismissText(R.string.next)
                    .setMaskColour(maskColour)
                    .build()
            )

            // Showcase for save button
            addSequenceItem(
                MaterialShowcaseView.Builder(activity)
                    .setTarget(binding.mapPolylineSave)
                    .setTitleText(R.string.save)
                    .setContentText(R.string.guide_chains_save)
                    .setContentTextColor(white)
                    .setDismissText(R.string.next)
                    .setMaskColour(maskColour)
                    .build()
            )

            // Showcase for moving map around
            val lastShowcase = MaterialShowcaseView.Builder(activity)
                .setTarget(binding.mapCrossHair)
                .setTitleText(R.string.move_around)
                .setContentText(R.string.guide_chains_move)
                .setContentTextColor(white)
                .setDismissText(R.string.done)
                .setMaskColour(maskColour)
                .setShapePadding(200)
                .build()
            addSequenceItem(lastShowcase)

            // Set SharedPreference when last showcase is dismissed
            setOnItemDismissedListener { itemView, _ ->
                if (itemView.equals(lastShowcase)) {
                    isChainGuideShowing = false
                    viewModel.chainsGuideShown = true
                }
            }

            setOnItemShownListener { itemView, _ ->
                if (itemView.equals(lastShowcase)) {
                    val currentLatLng = mapMgr?.getCameraTarget() ?: return@setOnItemShownListener
                    mapMgr?.stopShowingMyLocation()
                    mapMgr?.moveTo(LatLng(currentLatLng.latitude + 0.005, currentLatLng.longitude), duration = 700)
                }
            }
        }
    }

    companion object {

        private operator fun LatLng.component1() = this.latitude
        private operator fun LatLng.component2() = this.longitude

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

        binding.mapLiveGrids.setOnClickListener {
            viewModel.openSettings()
        }
        binding.mapGotoFab.setOnClickListener {
            mapMgr?.getCameraTarget()?.let {
                viewModel.openGoTo(it.latitude, it.longitude)
            }
        }

        binding.mapPolylineAdd.apply {
            setOnClickListener {
                onAddPolylinePoint()
                performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }
            setOnLongClickListener {
                onAddPolylineNamedPoint()
                performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                true
            }
            isHapticFeedbackEnabled = true
        }
        binding.mapChainFab.apply {
            setOnClickListener {
                onAddPolylinePoint()
                performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }
            setOnLongClickListener {
                onAddPolylineNamedPoint()
                true
            }
        }

        binding.mapPolylineUndo.apply {
            setOnClickListener {
                undoPolyline()
                performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }
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
        viewModel.coordinateSystem.observe(viewLifecycleOwner) {
            coordSys = it
            updateCardGridSystem(coordSys)
            updateGrids()
        }
        viewModel.angleUnit.observe(viewLifecycleOwner) {
            angleUnit = it
            updateLiveMeasurements()
        }

        // Set up Map Type toggle buttons
        binding.mapNormalType.setOnClickListener {
            mapMgr?.changeMapType(GoogleMap.MAP_TYPE_NORMAL)
        }
        binding.mapHybridType.setOnClickListener {
            mapMgr?.changeMapType(GoogleMap.MAP_TYPE_HYBRID)
        }
        binding.mapSatelliteType.setOnClickListener {
            mapMgr?.changeMapType(GoogleMap.MAP_TYPE_SATELLITE)
        }

        // NumberFormat used to format distances in Live Measurements
        numberFormat = NumberFormat.getNumberInstance(Locale.US)
        numberFormat.minimumFractionDigits = 1
        numberFormat.maximumFractionDigits = 1

        // Set up Map rotation reset
        binding.mapCompass.setOnClickListener { mapMgr?.resetMapRotation() }

        return binding.root
    }

    override fun onMapReady(mMap: GoogleMap) {
        if (mMap != null) {
            mapMgr = MapManager(mMap)

            val newMapType = viewModel.mapType
            mapMgr?.changeMapType(newMapType)
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

            // Set up Go To sequence
            viewModel.mapGoTo.observe(viewLifecycleOwner) {
                mapMgr?.moveTo(target = it)
                mapMgr?.stopShowingMyLocation()
            }

            if (viewModel.isLocationGranted) {
                onLocPermGranted()
            }

            // Observe location to update Live Measurement
            viewModel.fusedLocationData.observe(viewLifecycleOwner) {
                updateLiveMeasurements()
                mapMgr?.updateMyLocation(it)
            }
            viewModel.rotation.observe(viewLifecycleOwner) {
                val (azimuth, pitch, roll) = it
                mapMgr?.updateRotation(azimuth)
            }

            // Add markers
            viewModel.allPins.observe(viewLifecycleOwner, { allPins ->
                mapMgr?.drawMarkers(allPins)
                togglePoiInfobar(false)
            })

            // Add polylines
            viewModel.allChains.observe(viewLifecycleOwner, { allChains ->
                mapMgr?.drawChains(allChains)
                togglePoiInfobar(false)
            })

            // Draw current polyline if available. This is needed
            // to restore the polyline after a rotation change
            mapMgr?.drawCurrentPolyline(true)

            updateGrids()
            mapMgr?.drawMyLocation(viewModel.fusedLocationData.value)
        }
    }

    private fun onMyLocationPressed(resetRotation: Boolean) {
        togglePoiInfobar(false)
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
                    mapMgr?.drawMyLocation(location)
                    onMyLocationPressed(false)
                    viewModel.fusedLocationData.removeObserver(this)
                }
            }
        )
    }

    private fun onCameraMoveStarted(reason: Int) {
        if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
            removeFocus()
        }
        if (reason != GoogleMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION) {
            mapMgr?.onCameraMoveByUser()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateGrids(latitude: Double? = null, longitude: Double? = null) {
        if (latitude != null && longitude != null) {
            binding.mapCurrentGrids.text = viewModel.formatAsGrids(latitude, longitude)
            updateLiveMeasurements(latitude, longitude)
        } else if (mapMgr != null) {
            // Update grids and live measurement only if not showing Pin
            val (lat, lng) = mapMgr?.getCameraTarget() ?: return
            binding.mapCurrentGrids.text = viewModel.formatAsGrids(lat, lng)
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

    private fun updateCardGridSystem(coordSys: CoordinateSystem) {
        binding.mapGridSystem.text =
            resources.getStringArray(R.array.coordinate_systems)[coordSys.index]
    }

    private fun onAddPinFromMap(): Boolean {
        val mapMgr = this.mapMgr ?: return true
        val target = mapMgr.getCameraTarget()
        val pinLat = target.latitude.toString().toDouble()
        val pinLong = target.longitude.toString().toDouble()

        val newPin = Pin("", pinLat, pinLong, currentPinColor)
        viewModel.openPinCreator(newPin)
        return true
    }

    /**
     * Activates live measurement and colors the fab
     *
     * @param color
     */
    private fun focusOn(color: Int) {
        toggleLiveMeasurement(true)

        // update currentPinColor
        currentPinColor = color

        // Set card background color
        val cardColor = ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[color])
        updateFab(cardColor)
    }

    /**
     * Moves the map to focus on a [ChainNode] checkpoint
     *
     * @param checkpoint
     */
    private fun focusOn(checkpoint: ChainNode) {
        updatePoiInfobar(checkpoint)
        togglePoiInfobar(true)
        val parentChain = checkpoint.parentChain
        mapMgr?.moveTo(checkpoint)
        focusOn(parentChain?.color ?: currentPinColor)
    }

    /**
     * Moves the map to focus on a [Pin]
     *
     * @param pin
     */
    private fun focusOn(pin: Pin) {
        mapMgr?.moveTo(pin)
        updatePoiInfobar(pin)
        togglePoiInfobar(true)
        focusOn(pin.color)
    }

    /**
     * Moves the map to focus on a [Chain]
     *
     * @param chain
     */
    private fun focusOn(chain: Chain) {
        if (chain.cyclical) {
            removeFocus()
            mapMgr?.moveTo(chain)
        } else {
            focusOn(chain.nodes[0])
        }
    }

    /**
     * Removes the map focus
     *
     * Called when the user moves the map manually
     */
    private fun removeFocus() {
        mapMgr?.removeFocus()
        togglePoiInfobar(false)
    }

    /**
     * Updates the information in the POI infobar
     *
     * @param name
     * @param colorId
     */
    private fun updatePoiInfobar(name: String, colorId: Int?) {
        binding.mapPoiInfobar.text = name
        binding.mapPoiInfobar.isSelected = true

        val color = if (colorId != null) {
            ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[colorId])
        } else {
            ContextCompat.getColor(requireContext(), android.R.color.black)
        }

        val currentColor: Int? = binding.mapPoiInfobar.tag as Int?

        if (currentColor == null) {
            binding.mapPoiInfobar.tag = color
            binding.mapPoiInfobar.setTextColor(color)
            val colorStateList = ColorStateList.valueOf(color)
            binding.mapCheckpointInfobar.setStrokeColor(colorStateList)
            binding.areaIcon.imageTintList = colorStateList
            binding.routeIcon.imageTintList = colorStateList
        } else {
            binding.mapCheckpointInfobar.setStrokeColor(ColorStateList.valueOf(color))
            animateColor(currentColor, color, 150) {
                binding.mapPoiInfobar.tag = it
                binding.mapPoiInfobar.setTextColor(it)
                val colorStateList = ColorStateList.valueOf(it)
                binding.areaIcon.imageTintList = colorStateList
                binding.routeIcon.imageTintList = colorStateList
            }
        }
    }

    /**
     * Updates the information in the POI infobar to show the details of a [ChainNode] checkpoint
     *
     * @param checkpoint
     */
    private fun updatePoiInfobar(checkpoint: ChainNode) {
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
            if (checkpoint.parentChain!!.cyclical) {
                // Area
                binding.areaIcon.visibility = View.VISIBLE
                binding.routeIcon.visibility = View.INVISIBLE
            } else {
                // Route
                binding.areaIcon.visibility = View.INVISIBLE
                binding.routeIcon.visibility = View.VISIBLE
            }
            binding.mapCheckpointInfobar.setOnClickListener {
                viewModel.showChainInfo(checkpoint.parentChain!!.chainId)
            }
        } else {
            binding.mapCheckpointInfobar.isClickable = false
        }

        updatePoiInfobar(name, checkpoint.parentChain?.color)
    }

    /**
     * Updates the information in the POI infobar to show the details of a [Pin]
     *
     * @param pin
     */
    private fun updatePoiInfobar(pin: Pin) {
        updatePoiInfobar(pin.name, pin.color)

        binding.areaIcon.visibility = View.INVISIBLE
        binding.routeIcon.visibility = View.INVISIBLE

        binding.mapCheckpointInfobar.setOnClickListener { viewModel.showPinInfo(pin.pinId) }
    }

    /**
     * Animates the POI infobar in and out of view
     *
     * @param makeVisible
     */
    private fun togglePoiInfobar(makeVisible: Boolean = true) {
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

    /**
     * Animates the live measurement bar in and out of view
     *
     * @param makeVisible
     */
    private fun toggleLiveMeasurement(makeVisible: Boolean = true) {

        // Return with no changes if LiveMeasurement visibility is already equal to make_visible
        // or if location is not granted.
        if (isLiveMeasurementVisible == makeVisible || (makeVisible && !viewModel.isLocationGranted)) {
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
     * Updates the live measurement information
     *
     * @param lat latitude of the camera target
     * @param lng longitude of the camera target
     */
    @SuppressLint("SetTextI18n")
    private fun updateLiveMeasurements(lat: Double? = null, lng: Double? = null) {
        val fusedLocationData = viewModel.fusedLocationData.value ?: return
        var targetLat = lat
        var targetLng = lng
        if ((targetLat == null || targetLng == null)) {
            if (mapMgr != null) {
                val mapTarget = mapMgr!!.getCameraTarget()
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
        binding.mapCurrentDirection.text = viewModel.formatAsAngle(degToRad(direction).toFloat(), false)
    }

    /**
     * Rotates the mini map compass to suit the map camera bearing and tilt
     *
     * @param bearing
     * @param tilt
     */
    private fun updateMapCompass(bearing: Float, tilt: Float) {
        val matrix = Matrix().apply {
            postRotate(
                -bearing,
                compassImageDrawable.intrinsicWidth / 2f,
                compassImageDrawable.intrinsicHeight / 2f
            )
            postScale(
                1f, cos(degToRad(tilt)),
                compassImageDrawable.intrinsicWidth / 2f,
                compassImageDrawable.intrinsicHeight / 2f
            )
        }
        binding.mapCompassImg.imageMatrix = matrix
    }

    /**
     * Animates the mini map compass in and out of view
     *
     * @param makeVisible
     */
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
            it.add(ChainNode(name, mapMgr.getCameraTarget()))
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
        mapMgr.getChain(polyline)?.let {
            viewModel.showChainInfo(it.chainId)
        }
    }

    private fun onPolygonClick(polygon: Polygon) {
        val mapMgr = this.mapMgr ?: return
        mapMgr.getChain(polygon)?.let {
            viewModel.showChainInfo(it.chainId)
        }
    }

    /**
     * Removes the last added checkpoint or node in the polyline
     *
     * Exits polyline mode if there are no nodes left
     */
    private fun undoPolyline() {
        if (isChainGuideShowing) {
            return
        }
        viewModel.currentPolylinePoints.let {
            it.removeLast()
            mapMgr?.drawCurrentPolyline(true)

            if (it.size == 0) {
                viewModel.exitPolylineMode()
            } else if (it.size < 2) {
                binding.mapPolylineSave.isEnabled = false
            }
        }
    }

    private fun onSavePolyline() {
        viewModel.openChainCreator(Chain("", viewModel.currentPolylinePoints))
    }

    /**
     * Displays the interactive guide for chains
     */
    private fun showChainsGuide() {
        isChainGuideShowing = true
        chainShowcaseView.start()
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

        val bearing = mapMgr.getCameraBearing()
        val tilt = mapMgr.getCameraTilt()
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

    /**
     * Abstracts the operations of the map to handle movements and animations
     *
     * @property map [GoogleMap] used for displaying the map in MapView
     */
    @SuppressLint("PotentialBehaviorOverride")
    private inner class MapManager(var map: GoogleMap) {
        private var markersMap = HashMap<Marker, Pin>()
        private var polylinesMap = HashMap<Polyline, Chain>()
        private var polygonsMap = HashMap<Polygon, Chain>()
        private var checkpointsMap = HashMap<Marker, ChainNode>()

        private var myLocationMarker: Marker? = null
        private var myLocationCircle: Circle? = null
        private var myLocationDirection: Marker? = null
        private var currentPolyline: Polyline? = null
        private val currentPolylineMarkers = mutableListOf<Marker>()

        private var lastDirectionUpdate = 0L
        private var directionUpdateSum = 0f
        private var directionUpdateCount = 0

        private var isShowingPin = false
        private var isShowingMyLocation = false
            private set(value) {
                if (field == value) {
                    return
                }
                if (!value) {
                    isShowingMyLocationRotation = false
                }
                field = value
            }
        private var isShowingMyLocationRotation = false
            private set(value) {
                val lightGreen =
                    ContextCompat.getColor(requireContext(), R.color.colorPrimaryRipple)
                val clear = ContextCompat.getColor(requireContext(), android.R.color.transparent)
                if (value && field != value) {
                    animateColor(clear, lightGreen, 350) { color ->
                        val colorState = ColorStateList.valueOf(color)
                        binding.mapLocationButton.backgroundTintList = colorState
                        binding.mapCompass.foregroundTintList = colorState
                    }
                    val compass = binding.mapCompass

                    val green = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
                    val white = ContextCompat.getColor(requireContext(), android.R.color.white)

                    animateColor(compass.backgroundTintList, green, 350) { color ->
                        binding.mapCompass.backgroundTintList = ColorStateList.valueOf(color)
                    }
                    animateColor(binding.mapCompassImg.imageTintList, white, 350) { color ->
                        binding.mapCompassImg.imageTintList = ColorStateList.valueOf(color)
                    }
                } else if (!value && field != value) {
                    animateColor(lightGreen, clear, 350) { color ->
                        val colorState = ColorStateList.valueOf(color)
                        binding.mapLocationButton.backgroundTintList = colorState
                        binding.mapCompass.foregroundTintList = colorState
                    }

                    val compass = binding.mapCompass
                    val colorSurfaceTypedValue = TypedValue()
                    val colorOnSurfaceTypedValue = TypedValue()

                    requireContext().theme.resolveAttribute(
                        R.attr.colorSurface,
                        colorSurfaceTypedValue,
                        true
                    )
                    requireContext().theme.resolveAttribute(
                        R.attr.colorOnSurface,
                        colorOnSurfaceTypedValue,
                        true
                    )

                    val colorSurface = ContextCompat.getColor(requireContext(), colorSurfaceTypedValue.resourceId)
                    val colorOnSurface = ContextCompat.getColor(requireContext(), colorOnSurfaceTypedValue.resourceId)

                    animateColor(compass.backgroundTintList, colorSurface, 350) { color ->
                        binding.mapCompass.backgroundTintList = ColorStateList.valueOf(color)
                    }
                    animateColor(binding.mapCompassImg.imageTintList, colorOnSurface, 350) { color ->
                        binding.mapCompassImg.imageTintList = ColorStateList.valueOf(color)
                    }
                }
                field = value
            }
        private var isShowingCheckpoint = false
        @Volatile private var zoomStack = 0f

        private var isAnimating = false

        private var mapType: Int = map.mapType

        @Volatile private var targetPosition: CameraPosition = map.cameraPosition
        private val cameraPosition: CameraPosition
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
            }

            map.uiSettings.apply {
                isZoomControlsEnabled = false
                isMyLocationButtonEnabled = false
                isCompassEnabled = false
            }
        }

        /**
         * Changes the map type
         *
         * @param mapType either one of [GoogleMap.MAP_TYPE_NORMAL], [GoogleMap.MAP_TYPE_SATELLITE], [GoogleMap.MAP_TYPE_HYBRID]
         */
        fun changeMapType(mapType: Int) {
            if (this.mapType != mapType) {
                map.mapType = mapType
                this.mapType = mapType
                viewModel.mapType = mapType
            }
        }

        /**
         * Shows the live measurement bar and sets [isShowingMyLocation] to false
         *
         * Called when the user moves the map.
         *
         */
        fun onCameraMoveByUser() {
            if (isShowingPin) {
                return
            }

            mapMgr?.isShowingMyLocation = false
            toggleLiveMeasurement(true)
        }

        /**
         * Returns the target destination lat lng if [isShowingPin], otherwise returns the map camera target lat lng
         *
         * @return
         */
        fun getCameraTarget(): LatLng {
            return if (isShowingPin) {
                LatLng(targetPosition.target.latitude, targetPosition.target.longitude)
            } else {
                LatLng(map.cameraPosition.target.latitude, map.cameraPosition.target.longitude)
            }
        }

        fun getCameraBearing(): Float {
            return map.cameraPosition.bearing
        }

        fun getCameraTilt(): Float {
            return map.cameraPosition.tilt
        }

        /**
         * Animates the map camera to the target, bearing, tilt and zoom
         *
         * @param target
         * @param bearing
         * @param tilt
         * @param zoom
         * @param duration
         * @param onAnimFinish callback to execute when animation finishes
         * @param onAnimCancel callback to execute when animation is canceled
         */
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
            val zm = (zoom ?: if (map.cameraPosition.zoom < 10f) 15f else map.cameraPosition.zoom)
                .coerceAtLeast(map.minZoomLevel)
                .coerceAtMost(map.maxZoomLevel)

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

        /**
         * Animates map to a pin
         *
         * @param pin
         */
        fun moveTo(pin: Pin) {
            isShowingPin = true
            isShowingCheckpoint = false
            isShowingMyLocation = false
            moveTo(target = LatLng(pin.latitude, pin.longitude))
        }

        /**
         * Animates map to a route/area
         *
         * @param chain route or area
         */
        fun moveTo(chain: Chain) {
            isShowingCheckpoint = false
            isShowingPin = false
            isShowingMyLocation = false
            val nodes = chain.nodes
            if (chain.cyclical) {
                // Area
                val bounds = LatLngBounds.builder().apply {
                    for (node in nodes) {
                        include(node.position)
                    }
                }.build()
                val padding = resources.dpToPx(64f)
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

        /**
         * Animates the map to a [ChainNode] on a route or area
         *
         * @param node
         */
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
                val checkpointNode = ChainNode(marker.title ?: "", position, null)
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
                ) ?: continue
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

            for (chain in allChains) {
                val chainNodes = chain.nodes
                val points = chainNodes.map { it.position }
                if (chain.cyclical) {
                    val color =
                        ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[chain.color])
                    val polygonOptions = PolygonOptions()
                    points.forEach { polygonOptions.add(it) }
                    polygonOptions.apply {
                        strokeColor(color)
                        strokeWidth(8f)
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
                        width = resources.dpToPx(4f)
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
                        ) ?: continue
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
                ValueAnimator.ofObject(latLngEvaluator, myLocationMarker!!.position, position).apply {
                    duration = 750
                    addUpdateListener {
                        val newPosition = it.animatedValue as LatLng
                        if (isShowingMyLocation) {
                            moveTo(
                                target = newPosition,
                                duration = 0,
                                zoom = targetPosition.zoom
                            )
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
                currentPolyline?.apply {
                    pattern = listOf(
                        Dash(resources.dpToPx(8f)),
                        Gap(resources.dpToPx(8f))
                    )
                    width = resources.dpToPx(6f)
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
                        ) ?: continue
                        currentPolylineMarkers.add(marker)
                    }
                }
            }
        }

        private fun resetZoomStack() {
            zoomStack = 0f
        }

        fun zoomIn() {
            if (isShowingMyLocationRotation) {
                moveTo(
                    zoom = targetPosition.zoom + 1,
                    duration = 0,
                    onAnimFinish = this::resetZoomStack
                )
            } else {
                zoomStack = if (zoomStack <= 0) 1f else zoomStack + 1

                val currentZoomStack = zoomStack

                val onCancel = {
                    if (zoomStack == currentZoomStack) {
                        resetZoomStack()
                    }
                }
                moveTo(
                    zoom = targetPosition.zoom + zoomStack,
                    duration = 300,
                    onAnimFinish = this::resetZoomStack,
                    onAnimCancel = onCancel
                )
            }
        }

        fun zoomOut() {
            if (isShowingMyLocationRotation) {
                moveTo(
                    zoom = targetPosition.zoom - 1,
                    duration = 0,
                    onAnimFinish = this::resetZoomStack
                )
            } else {
                zoomStack = if (zoomStack >= 0) -1f else zoomStack - 1

                val currentZoomStack = zoomStack

                val onCancel = {
                    if (zoomStack == currentZoomStack) {
                        resetZoomStack()
                    }
                }
                moveTo(
                    zoom = targetPosition.zoom + zoomStack,
                    duration = 300,
                    onAnimFinish = this::resetZoomStack,
                    onAnimCancel = onCancel
                )
            }
        }

        private val goToMyLocationOnFinish = {
            toggleLiveMeasurement(false)
            isShowingMyLocation = true
        }

        fun goToMyLocation(resetRotation: Boolean) {

            var newBearing = map.cameraPosition.bearing

            if (isShowingMyLocationRotation) {
                isShowingMyLocationRotation = false
                newBearing = 0f
            } else if (isShowingMyLocation && !resetRotation) {
                isShowingMyLocationRotation = true
            }
            val location = viewModel.fusedLocationData.value
            if (location != null) {
                when {
                    resetRotation -> moveTo(
                        target = LatLng(location.latitude, location.longitude),
                        tilt = 0f,
                        bearing = 0f,
                        duration = 500,
                        onAnimFinish = goToMyLocationOnFinish
                    )
                    isShowingMyLocationRotation -> {
                        // Turn to direction of the myLocationDirection marker
                        val bearing = if (myLocationDirection != null) {
                            myLocationDirection!!.rotation + 90
                        } else {
                            null
                        }
                        moveTo(
                            target = LatLng(location.latitude, location.longitude),
                            bearing = bearing,
                            duration = 500,
                            onAnimFinish = goToMyLocationOnFinish
                        )
                    }
                    else -> {
                        moveTo(
                            target = LatLng(location.latitude, location.longitude),
                            duration = 500,
                            bearing = newBearing,
                            onAnimFinish = goToMyLocationOnFinish
                        )
                    }
                }
                removeFocus()
            }
        }

        fun getChain(polyline: Polyline): Chain? {
            return polylinesMap[polyline]
        }

        fun getChain(polygon: Polygon): Chain? {
            return polygonsMap[polygon]
        }

        fun resetMapRotation() {
            isShowingMyLocationRotation = false
            moveTo(tilt = 0f, bearing = 0f)
        }

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

        /**
         * Updates the rotation of [myLocationDirection]
         *
         * @param azimuth latest azimuth of the device
         */
        fun updateRotation(azimuth: Float) {

            val marker = this.myLocationDirection ?: return
            val currentTime = System.currentTimeMillis()

            // Add current update to the cumulative sum
            directionUpdateSum += azimuth
            directionUpdateCount++

            var newRotation = directionUpdateSum / directionUpdateCount * 180 / Math.PI.toFloat() - 90
            val currentRotation = marker.rotation
            val difference = newRotation - currentRotation

            // Update marker if this is the first time, or if 100ms has passed since last
            // update and difference is larger than .7 degrees
            if (currentTime == 0L || (currentTime - lastDirectionUpdate > 100 && abs(difference) > .7)) {
                directionUpdateSum = 0f
                directionUpdateCount = 0

                // Record tilt for updating map compass
                val tilt = this.cameraPosition.tilt

                // Correction for rotation past 0 or 360 degrees
                newRotation = when {
                    difference > 180 -> currentRotation - (360 - difference)
                    difference < -180 -> currentRotation + (360 + difference)
                    else -> newRotation
                }

                animate(currentRotation, newRotation, 150, LinearInterpolator()) { rotation ->
                    marker.rotation = rotation
                    if (isShowingMyLocationRotation) {
                        moveTo(
                            target = myLocationMarker?.position,
                            bearing = rotation + 90,
                            duration = 0,
                            zoom = targetPosition.zoom
                        )
                        // Update map compass
                        updateMapCompass(rotation + 90, tilt)
                    }
                }

                lastDirectionUpdate = currentTime
            } else if (currentTime - lastDirectionUpdate > 500) {
                // Dispose outdated rotation information
                directionUpdateSum = 0f
                directionUpdateCount = 0
                lastDirectionUpdate = currentTime
            }
        }

        fun stopShowingMyLocation() {
            isShowingMyLocation = false
        }
    }
}
