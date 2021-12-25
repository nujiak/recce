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
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.livedatas.FusedLocationLiveData
import com.nujiak.recce.utils.dpToPx
import com.nujiak.recce.utils.formatAsDistanceString
import com.nujiak.recce.utils.getGridString
import com.nujiak.recce.utils.radToDeg
import com.nujiak.recce.utils.spToPx
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.cos

@AndroidEntryPoint
class GpsFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentGpsBinding

    private var lastLocationData: FusedLocationLiveData.LocationData? = null

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

        viewModel.rotation.observe(viewLifecycleOwner) {
            val (aziRad, pitRad, rolRad) = it
            updateCompass(aziRad, pitRad, rolRad)
            updateOrientationUI()
        }
        // Observe for preferences changes
        viewModel.coordinateSystem.observe(viewLifecycleOwner, {
            binding.gpsGridSystem.text =
                resources.getStringArray(R.array.coordinate_systems)[it.index]
            updateLocationUI()
        })
        viewModel.angleUnit.observe(viewLifecycleOwner, {
            updateOrientationUI()
        })

        // Change the layout column count depending on the screen width
        resources.displayMetrics.widthPixels.let { screenWidth ->
            binding.gpsParent.columnCount =
                if (screenWidth / 2 > resources.dpToPx(288f) && screenWidth / 2 > resources.spToPx(344f)) 2 else 1
        }
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    private fun updateOrientationUI() {

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdated < 100) {
            return
        }

        val (azimuth, pitch, roll) = viewModel.rotation.value ?: return

        binding.gpsAzimuth.text = viewModel.formatAsAngle(azimuth, false)
        binding.gpsPitch.text = viewModel.formatAsAngle(pitch, true)
        binding.gpsRoll.text = viewModel.formatAsAngle(roll, true)
        lastUpdated = currentTime
    }

    private fun updateCompass(azimuth: Float, pitch: Float, roll: Float) {
        val compassDrawable = binding.gpsCompassArrow.drawable

        val matrix = Matrix().apply {
            postRotate(
                -radToDeg(azimuth),
                compassDrawable!!.intrinsicWidth / 2f,
                compassDrawable.intrinsicHeight / 2f
            )
            postScale(
                cos(roll), cos(pitch),
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
            binding.gpsGrids.text = getGridString(latitude, longitude, viewModel.coordinateSystem.value!!, resources)
        }
    }
}
