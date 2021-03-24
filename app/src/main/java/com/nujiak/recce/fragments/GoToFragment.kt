package com.nujiak.recce.fragments

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.model.LatLng
import com.nujiak.recce.MainViewModel
import com.nujiak.recce.R
import com.nujiak.recce.databinding.DialogGoToBinding
import com.nujiak.recce.mapping.*
import com.nujiak.recce.modalsheets.SettingsSheet
import dagger.hilt.android.AndroidEntryPoint
import java.util.*
import kotlin.math.floor


@AndroidEntryPoint
class GoToFragment : DialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: DialogGoToBinding

    private var coordSys = 0
    private var isInputValid = false

    var initialLatLng: LatLng? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder =
            AlertDialog.Builder(requireActivity())
        val layoutInflater = LayoutInflater.from(context)
        binding = DialogGoToBinding.inflate(layoutInflater, null, false)
        builder.setView(binding.root)

        val dialog = builder.create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Fetch coordinate system setting
        coordSys = viewModel.coordinateSystem.value ?: 0

        setUp()

        initialLatLng?.let {
            binding.newPinLatEditText.setText("%.6f".format(Locale.US, initialLatLng!!.latitude))
            binding.newPinLongEditText.setText("%.6f".format(Locale.US, initialLatLng!!.longitude))
            updateGrids()
        }

        return dialog
    }

    private fun setUp() {

        binding.newPinCustomGridsGroup.visibility = when (coordSys) {
            SettingsSheet.COORD_SYS_ID_LATLNG -> View.GONE
            else -> View.VISIBLE
        }


        // Set up fields' onKey listeners
        binding.newPinLatEditText.setOnKeyListener { _, _, _ ->
            updateGrids()
            binding.newPinLatInput.error = null
            false
        }
        binding.newPinLongEditText.setOnKeyListener { _, _, _ ->
            updateGrids()
            binding.newPinLongInput.error = null
            false
        }

        // Set up zone dropdown for UTM, or else hide the dropdown
        if (coordSys == SettingsSheet.COORD_SYS_ID_UTM) {
            binding.newPinZoneDropdown.setOnItemClickListener { _, _, _, _ ->
                updateLatLng()
            }
        } else {
            binding.newPinZoneInput.visibility = View.GONE
        }

        // Set up easting/northing EditTexts for relevant coordinate systems
        if (coordSys == SettingsSheet.COORD_SYS_ID_UTM || coordSys == SettingsSheet.COORD_SYS_ID_KERTAU) {
            binding.newPinEastingEditText.setOnKeyListener { _, _, _ ->
                updateLatLng()
                false
            }
            binding.newPinNorthingEditText.setOnKeyListener { _, _, _ ->
                updateLatLng()
                false
            }
        }

        // Set up MGRS String EditText for MGRS
        if (coordSys == SettingsSheet.COORD_SYS_ID_MGRS) {
            binding.newPinMgrsEditText.setOnKeyListener { _, _, _ ->
                updateLatLng()
                false
            }
            binding.newPinGridGroup.visibility = View.GONE
            binding.newPinMgrsGroup.visibility = View.VISIBLE
        }

        binding.newPinGridSystem.text = getString(
            when (coordSys) {
                SettingsSheet.COORD_SYS_ID_UTM -> R.string.utm
                SettingsSheet.COORD_SYS_ID_MGRS -> R.string.mgrs
                SettingsSheet.COORD_SYS_ID_KERTAU -> R.string.kertau
                SettingsSheet.COORD_SYS_ID_LATLNG -> R.string.lat_lng
                else -> throw IllegalArgumentException("Invalid coordinate system id: $coordSys")
            }
        )

        binding.goButton.setOnClickListener {
            onCompleted()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateLatLng() {
        when (coordSys) {
            SettingsSheet.COORD_SYS_ID_UTM -> {
                val zoneBand = binding.newPinZoneDropdown.text.toString()
                val easting = binding.newPinEastingEditText.text.toString()
                val northing = binding.newPinNorthingEditText.text.toString()
                if (!zoneBand.isBlank() && !easting.isBlank() && !northing.isBlank()) {
                    val zone = zoneBand.slice(if (zoneBand.length == 3) 0..1 else 0..0).toInt()
                    val band = zoneBand.slice(if (zoneBand.length == 3) 2..2 else 1..1)
                    val (lat, lng) =
                        getLatLngFromUtm(
                            UtmData(
                                easting.toDouble(),
                                northing.toDouble(),
                                zone,
                                band.single()
                            )
                        )

                    if (!lat.isNaN() && lat < 90 && lat > -90 && !lng.isNaN()) {
                        // Lat and Lng are valid numbers
                        binding.newPinLatEditText.setText("%.6f".format(Locale.US, lat))
                        binding.newPinLongEditText.setText("%.6f".format(Locale.US, lng))
                    } else {
                        // Lat and Lng are invalid (NaN / outside bounds)
                        binding.newPinLatEditText.setText("")
                        binding.newPinLongEditText.setText("")
                    }
                }
            }
            SettingsSheet.COORD_SYS_ID_MGRS -> {
                val mgrsData = parseMgrsFrom(binding.newPinMgrsEditText.text.toString())
                val latLng = mgrsData?.toUtmData()?.toLatLng()

                if (latLng != null) {
                    binding.newPinLatEditText.setText("%.6f".format(Locale.US, latLng.first))
                    binding.newPinLongEditText.setText("%.6f".format(Locale.US, latLng.second))
                } else {
                    binding.newPinLatEditText.setText("")
                    binding.newPinLongEditText.setText("")
                }
            }
            SettingsSheet.COORD_SYS_ID_KERTAU -> {
                val easting = binding.newPinEastingEditText.text.toString()
                val northing = binding.newPinNorthingEditText.text.toString()

                if (!easting.isBlank() && !northing.isBlank()) {
                    val latLngPair =
                        getLatLngFromKertau(
                            easting.toDouble(),
                            northing.toDouble()
                        )
                    // Lat Lng are valid and kertau grids were within bounds
                    val (lat, lng) = latLngPair
                    binding.newPinLatEditText.setText("%.6f".format(Locale.US, lat))
                    binding.newPinLongEditText.setText("%.6f".format(Locale.US, lng))
                }
            }
            SettingsSheet.COORD_SYS_ID_LATLNG -> {
            }
            else -> throw IllegalArgumentException("Invalid coordinate system id: $coordSys")
        }

    }

    @SuppressLint("SetTextI18n")
    private fun updateGrids() {
        val lat: Double
        val lng: Double
        try {
            lat = binding.newPinLatEditText.text.toString().toDouble()
            lng = binding.newPinLongEditText.text.toString().toDouble()
        } catch (e: Exception) {
            clearGrids()
            return
        }

        when (coordSys) {
            SettingsSheet.COORD_SYS_ID_UTM -> {
                val utmData = getUtmData(lat, lng)
                utmData?.let {
                    // UTM data is valid, set texts then return
                    binding.newPinEastingEditText.setText(floor(it.x).toInt().toString())
                    binding.newPinNorthingEditText.setText(floor(it.y).toInt().toString())
                    binding.newPinZoneDropdown.setText("${it.zone}${it.band}", false)
                    return
                }
            }
            SettingsSheet.COORD_SYS_ID_MGRS -> {
                binding.newPinMgrsEditText.setText(
                    getMgrsData(lat, lng)?.toSingleLine(includeWhitespace = true)
                )
                return
            }
            SettingsSheet.COORD_SYS_ID_KERTAU -> {
                val kertauData =
                    getKertauGrids(
                        lat,
                        lng
                    )
                if (kertauData != null) {
                    // Kertau data is valid, set texts then return
                    val (easting, northing) = kertauData
                    binding.newPinEastingEditText.setText(floor(easting).toInt().toString())
                    binding.newPinNorthingEditText.setText(floor(northing).toInt().toString())
                    return
                }
            }
            SettingsSheet.COORD_SYS_ID_LATLNG -> {
            }
            else -> throw IllegalArgumentException("Invalid coordinate system id: $coordSys")
        }
        // Grids are not valid for current coordinate system (out of bounds),
        // wipe zone, easting and northing fields
        clearGrids()
    }

    private fun clearGrids() {
        binding.newPinEastingEditText.setText("")
        binding.newPinNorthingEditText.setText("")
        binding.newPinZoneDropdown.setText("", false)
    }


    private fun onValidateInput() {

        isInputValid = true

        // Validate latitude
        val lat = binding.newPinLatEditText.text.toString().toDoubleOrNull()
        if (lat == null) {
            binding.newPinLatInput.error = getString(R.string.lat_empty_error)
            isInputValid = false
        } else if (lat < -90 || lat > 90) {
            binding.newPinLatInput.error = getString(R.string.lat_out_of_range_error)
            isInputValid = false
        }

        // Validate longitude
        val lng = binding.newPinLongEditText.text.toString().toDoubleOrNull()
        if (lng == null) {
            binding.newPinLongInput.error = getString(R.string.long_empty_error)
            isInputValid = false
        }
    }

    private fun onCompleted() {
        onValidateInput()

        if(!isInputValid) {
            return
        }

        viewModel.mapGoTo(
            LatLng(binding.newPinLatEditText.text.toString().toDoubleOrNull()!!,
                binding.newPinLongEditText.text.toString().toDoubleOrNull()!!)
        )

        dismiss()
    }

}