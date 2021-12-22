package com.nujiak.recce.fragments

import android.annotation.SuppressLint
import android.graphics.Matrix
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.nujiak.recce.MainViewModel
import com.nujiak.recce.R
import com.nujiak.recce.databinding.FragmentGpsBinding
import com.nujiak.recce.enums.AngleUnit
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.livedatas.FusedLocationLiveData
import com.nujiak.recce.livedatas.RotationLiveData
import com.nujiak.recce.utils.dpToPx
import com.nujiak.recce.utils.formatAsDistanceString
import com.nujiak.recce.utils.getAngleString
import com.nujiak.recce.utils.getGridString
import com.nujiak.recce.utils.radToDeg
import com.nujiak.recce.utils.spToPx
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.PI
import kotlin.math.cos

@AndroidEntryPoint
class GpsFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentGpsBinding

    private var lastRotationData: RotationLiveData.RotationData? = null
    private var lastLocationData: FusedLocationLiveData.LocationData? = null

    private var coordSys = CoordinateSystem.atIndex(0)
    private var angleUnit = AngleUnit.atIndex(0)

    private var lastUpdated = System.currentTimeMillis()

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentGpsBinding.inflate(inflater, container, false)

        binding.root.fitsSystemWindows = false

        // Listen for fused location updates
        viewModel.fusedLocationData.observe(viewLifecycleOwner, {
            updateLocationUI(it)
        })

        viewModel.rotationLiveData.observe(viewLifecycleOwner) {
            lastRotationData = it
            updateCompass()
            updateOrientationUI()
        }
        // Observe for preferences changes
        viewModel.coordinateSystem.observe(viewLifecycleOwner, {
            coordSys = it
            binding.gpsGridSystem.text =
                resources.getStringArray(R.array.coordinate_systems)[coordSys.index]
            updateLocationUI()
        })
        viewModel.angleUnit.observe(viewLifecycleOwner, {
            angleUnit = it
            updateOrientationUI()
        })

        // Change the layout column count depending on the screen width
        resources.displayMetrics.widthPixels.let { screenWidth ->
            binding.gpsParent.columnCount =
                if (screenWidth / 2 > resources.dpToPx(288f) && screenWidth / 2 > resources.spToPx(288f)) 2 else 1
        }
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    private fun updateOrientationUI() {

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdated < 100) {
            return
        }

        val rotationData = lastRotationData ?: return

        var (aziRad, pitRad, rolRad) = rotationData

        if (aziRad < 0) {
            aziRad += 2 * PI.toFloat()
        }

        binding.gpsAzimuth.text = getAngleString(aziRad, angleUnit, false)
        binding.gpsPitch.text = getAngleString(-pitRad, angleUnit, true)
        binding.gpsRoll.text = getAngleString(rolRad, angleUnit, true)
        lastUpdated = currentTime
    }

    private fun updateCompass() {

        val rotationData = lastRotationData ?: return

        val compassDrawable = binding.gpsCompassArrow.drawable

        val matrix = Matrix().apply {
            postRotate(
                -radToDeg(rotationData.azimuth),
                compassDrawable!!.intrinsicWidth / 2f,
                compassDrawable.intrinsicHeight / 2f
            )
            postScale(
                cos(rotationData.roll), cos(rotationData.pitch),
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
                getGridString(latitude, longitude, CoordinateSystem.WGS84, resources)
            binding.gpsGrids.text = getGridString(latitude, longitude, coordSys, resources)
        }
    }
}
