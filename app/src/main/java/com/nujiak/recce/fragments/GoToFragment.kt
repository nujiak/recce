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
import com.nujiak.recce.NoFilterArrayAdapter
import com.nujiak.recce.R
import com.nujiak.recce.databinding.DialogGoToBinding
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.mapping.Coordinate
import com.nujiak.recce.mapping.Mapping
import com.nujiak.recce.mapping.ZONE_BANDS
import com.nujiak.recce.mapping.getUtmZoneAndBand
import com.nujiak.recce.mapping.parse
import dagger.hilt.android.AndroidEntryPoint
import java.util.*
import kotlin.math.floor


@AndroidEntryPoint
class GoToFragment : DialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: DialogGoToBinding

    private var coordSys = CoordinateSystem.atIndex(0)
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
        coordSys = viewModel.coordinateSystem.value ?: CoordinateSystem.atIndex(0)

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
            CoordinateSystem.WGS84 -> View.GONE
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
        if (coordSys == CoordinateSystem.UTM) {
            binding.newPinZoneDropdown.setOnItemClickListener { _, _, _, _ ->
                updateLatLng()
            }
        } else {
            binding.newPinZoneInput.visibility = View.GONE
        }

        // Set up easting/northing EditTexts for relevant coordinate systems
        if (coordSys == CoordinateSystem.UTM || coordSys == CoordinateSystem.KERTAU) {
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
        if (coordSys == CoordinateSystem.MGRS) {
            binding.newPinMgrsEditText.setOnKeyListener { _, _, _ ->
                updateLatLng()
                false
            }
            binding.newPinGridGroup.visibility = View.GONE
            binding.newPinMgrsGroup.visibility = View.VISIBLE
        }

        binding.newPinGridSystem.text = getString(
            when (coordSys) {
                CoordinateSystem.UTM -> R.string.utm
                CoordinateSystem.MGRS -> R.string.mgrs
                CoordinateSystem.KERTAU -> R.string.kertau
                CoordinateSystem.WGS84 -> R.string.lat_lng
            }
        )
        context?.let {
            binding.newPinZoneDropdown.setAdapter(
                NoFilterArrayAdapter(
                    it, R.layout.dropdown_menu_popup_item,
                    ZONE_BANDS
                )
            )
        }


        binding.goButton.setOnClickListener {
            onCompleted()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateLatLng() {
        when (coordSys) {
            CoordinateSystem.UTM -> {
                val zoneBand = binding.newPinZoneDropdown.text.toString()
                val easting = binding.newPinEastingEditText.text.toString()
                val northing = binding.newPinNorthingEditText.text.toString()
                if (!zoneBand.isBlank() && !easting.isBlank() && !northing.isBlank()) {
                    val zone = zoneBand.slice(if (zoneBand.length == 3) 0..1 else 0..0).toInt()
                    val band = zoneBand[(if (zoneBand.length == 3) 2 else 1)]

                    val utmCoord = Mapping.parseUtm(zone, band, easting.toDouble(), northing.toDouble())
                    val lat = utmCoord?.latLng?.latitude ?: Double.NaN
                    val lng = utmCoord?.latLng?.longitude ?: Double.NaN

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
            CoordinateSystem.MGRS -> {
                val mgrsCoord = parse(binding.newPinMgrsEditText.text.toString())
                val latLng = mgrsCoord?.latLng

                if (latLng != null) {
                    binding.newPinLatEditText.setText("%.6f".format(Locale.US, latLng.latitude))
                    binding.newPinLongEditText.setText("%.6f".format(Locale.US, latLng.longitude))
                } else {
                    binding.newPinLatEditText.setText("")
                    binding.newPinLongEditText.setText("")
                }
            }
            CoordinateSystem.KERTAU -> {
                val easting = binding.newPinEastingEditText.text.toString().toDoubleOrNull()
                val northing = binding.newPinNorthingEditText.text.toString().toDoubleOrNull()

                if (easting == null || northing == null) {
                    binding.newPinLatEditText.setText("")
                    binding.newPinLongEditText.setText("")
                    return
                }

                val coord = Mapping.parseKertau1948(easting, northing)
                val latLng = coord.latLng
                // Lat Lng are valid and kertau grids were within bounds
                binding.newPinLatEditText.setText("%.6f".format(Locale.US, latLng.latitude))
                binding.newPinLongEditText.setText("%.6f".format(Locale.US, latLng.longitude))
            }
            CoordinateSystem.WGS84 -> {
            }
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

        val latLng = LatLng(lat, lng)
        when (coordSys) {
            CoordinateSystem.UTM -> {
                val utmData = Mapping.toUtm(latLng)
                utmData?.let {
                    // UTM data is valid, set texts then return
                    binding.newPinEastingEditText.setText(floor(it.x).toInt().toString())
                    binding.newPinNorthingEditText.setText(floor(it.y).toInt().toString())

                    // TODO: Replace this workaround
                    val (zone, band) = getUtmZoneAndBand(lat, lng)
                    binding.newPinZoneDropdown.setText("${zone}${band}", false)
                    return
                }
            }
            CoordinateSystem.MGRS -> {
                binding.newPinMgrsEditText.setText(Mapping.toMgrs(lat, lng).toString())
                return
            }
            CoordinateSystem.KERTAU -> {
                val kertauCoordinate = Mapping.toKertau1948(Coordinate.of(latLng))
                binding.newPinEastingEditText.setText(floor(kertauCoordinate.x).toInt().toString())
                binding.newPinNorthingEditText.setText(floor(kertauCoordinate.y).toInt().toString())
                return
            }
            CoordinateSystem.WGS84 -> {
            }
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