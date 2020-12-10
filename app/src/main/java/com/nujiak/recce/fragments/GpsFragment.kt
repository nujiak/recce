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
import com.nujiak.recce.livedatas.FusedLocationLiveData
import com.nujiak.recce.livedatas.RotationLiveData
import com.nujiak.recce.utils.formatAsDistanceString
import com.nujiak.recce.utils.getAngleString
import com.nujiak.recce.utils.getGridString
import com.nujiak.recce.utils.radToDeg
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.PI
import kotlin.math.cos


@AndroidEntryPoint
class GpsFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentGpsBinding

    private var lastRotationData: RotationLiveData.RotationData? = null
    private var lastLocationData: FusedLocationLiveData.LocationData? = null

    private var coordSysId = 0
    private var angleUnitId = 0

    private var lastUpdated = System.currentTimeMillis()

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

        viewModel.rotationLiveData.observe (viewLifecycleOwner) {
            lastRotationData = it
            updateCompass()
            updateOrientationUI()
        }
        // Observe for preferences changes
        viewModel.coordinateSystem.observe(viewLifecycleOwner, {
            coordSysId = it
            binding.gpsGridSystem.text =
                resources.getStringArray(R.array.coordinate_systems)[coordSysId]
            updateLocationUI()
        })
        viewModel.angleUnit.observe(viewLifecycleOwner, {
            angleUnitId = it
            updateOrientationUI()
        })

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

        binding.gpsAzimuth.text = getAngleString(aziRad, angleUnitId, false)
        binding.gpsPitch.text = getAngleString(-pitRad, angleUnitId, true)
        binding.gpsRoll.text = getAngleString(rolRad, angleUnitId, true)
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
                "%.6f, %.6f".format(latitude, longitude)
            binding.gpsGrids.text = getGridString(latitude, longitude, coordSysId, resources)
        }

    }
}