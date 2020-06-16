package com.nujiak.reconnaissance.modalsheets

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nujiak.reconnaissance.*
import com.nujiak.reconnaissance.database.Pin
import com.nujiak.reconnaissance.database.PinDatabase
import com.nujiak.reconnaissance.databinding.SheetPinCreatorBinding
import com.nujiak.reconnaissance.mapping.kertau.getKertauGrids
import com.nujiak.reconnaissance.mapping.kertau.getLatLngFromKertau
import com.nujiak.reconnaissance.mapping.utm.UtmData
import com.nujiak.reconnaissance.mapping.utm.ZONE_BANDS
import com.nujiak.reconnaissance.mapping.utm.getLatLngFromUtm
import com.nujiak.reconnaissance.mapping.utm.getUtmData
import com.nujiak.reconnaissance.modalsheets.SettingsSheet.Companion.COORD_SYS_ID_KERTAU
import com.nujiak.reconnaissance.modalsheets.SettingsSheet.Companion.COORD_SYS_ID_UTM
import kotlin.math.floor

class PinCreatorSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetPinCreatorBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var pin: Pin

    private var isUpdate: Boolean = false
    private var isInputValid: Boolean = false

    private var coordSys = 0

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = SheetPinCreatorBinding.inflate(inflater, container, false)

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

        // Fetch coordinate system setting
        coordSys = viewModel.coordinateSystem.value ?: 0
        setUpTextFields()

        // Set up exposed dropdown menu
        context?.let {
            binding.newPinColorDropdown.setAdapter(
                ArrayAdapter(
                    it, R.layout.dropdown_menu_popup_item, COLORS
                )
            )
            binding.newPinZoneDropdown.setAdapter(
                ArrayAdapter(
                    it, R.layout.dropdown_menu_popup_item, ZONE_BANDS
                )
            )
        }

        // Get pin passed in as argument
        val argPin = arguments?.getParcelable<Pin>("pin")
        if (argPin != null) {
            pin = argPin
        } else {
            pin = Pin("", 0.0, 0.0)
        }

        // Check if this is an update and pre set data fields
        if (!pin.name.isBlank()) {
            // Populate name field if this is an update
            binding.newPinNameEditText.setText(pin.name)
            binding.creatorSheetHeader.text = getString(R.string.edit_pin)
            isUpdate = true
        }
        if (pin.latitude != 0.0 || pin.longitude != 0.0) {
            // Populate lat long fields if available
            binding.newPinLatEditText.setText("%.6f".format(pin.latitude))
            binding.newPinLongEditText.setText("%.6f".format(pin.longitude))
        }
        binding.newPinColorDropdown.setText(COLORS[pin.color], false)

        // Set up buttons
        binding.newPinSave.setOnClickListener { onCompleted() }
        if (isUpdate) {
            binding.newPinDelete.visibility = View.VISIBLE
            binding.newPinDelete.setOnClickListener {
                viewModel.deletePin(pin)
                dismiss()
            }
        }
        updateGrids()
        updateSheetColor(pin.color)

        return binding.root
    }

    private fun onCompleted() {
        onValidateInput()
        if (isInputValid) {

            // Create new pin instead of modifying old pin
            val newPin = pin.copy(
                name = binding.newPinNameEditText.text.toString(),
                latitude = binding.newPinLatEditText.text.toString().toDouble(),
                longitude = wrapLngDeg(binding.newPinLongEditText.text.toString().toDouble()),
                color = COLORS.indexOf(binding.newPinColorDropdown.text.toString())
            )

            when (isUpdate) {
                true -> {
                    viewModel.updatePin(newPin)
                    viewModel.putPinInFocus(newPin)
                }
                false -> {
                    viewModel.addPin(newPin)
                    viewModel.putPinInFocus(newPin.copy(pinId = viewModel.lastAddedId))
                }
            }
            dismiss()
        }
    }

    private fun onValidateInput() {

        isInputValid = true

        val name = binding.newPinNameEditText.text
        when {
            name.isNullOrBlank() -> {
                binding.newPinNameInput.error = getString(R.string.name_blank_error)
                isInputValid = false
            }
            name.length > 20 -> {
                binding.newPinNameInput.error = getString(R.string.name_too_long_error)
                isInputValid = false
            }
        }

        val lat = binding.newPinLatEditText.text
        if (lat.isNullOrBlank()) {
            binding.newPinLatInput.error = getString(R.string.lat_empty_error)
            isInputValid = false
        } else {
            val latValue = lat.toString().toDouble()
            if (latValue < -90 || latValue > 90) {
                binding.newPinLatInput.error = getString(R.string.lat_out_of_range_error)
                isInputValid = false
            }
        }

        val long = binding.newPinLongEditText.text
        when {
            long.isNullOrBlank() -> {
                binding.newPinLongInput.error = getString(R.string.long_empty_error)
                isInputValid = false
            }
        }
    }

    private fun setUpTextFields() {
        // Set up fields' onKey listeners
        binding.newPinNameEditText.setOnKeyListener { _, _, _ ->
            if (binding.newPinNameEditText.length() <= 20) {
                binding.newPinNameInput.error = null
            }
            false
        }
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

        binding.newPinZoneDropdown.setOnItemClickListener { _, _, _, _ ->
            updateLatLng()
        }

        binding.newPinEastingEditText.setOnKeyListener { _, _, _ ->
            updateLatLng()
            false
        }
        binding.newPinNorthingEditText.setOnKeyListener { _, _, _ ->
            updateLatLng()
            false
        }

        binding.newPinColorDropdown.setOnItemClickListener { _, _, position, _ ->
            updateSheetColor(position)
        }

        if (coordSys != COORD_SYS_ID_UTM) {
            binding.newPinZoneInput.visibility = View.GONE
        }

        binding.newPinGridSystem.text = getString(
            when (coordSys) {
                COORD_SYS_ID_UTM -> R.string.utm
                COORD_SYS_ID_KERTAU -> R.string.kertau
                else -> throw IllegalArgumentException("Invalid coordinate system id: $coordSys")
            }
        )
    }

    @SuppressLint("SetTextI18n")
    private fun updateLatLng() {
        when (coordSys) {
            COORD_SYS_ID_UTM -> {
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
                                band
                            )
                        )

                    if (!lat.isNaN() && lat < 90 && lat > -90 && !lng.isNaN()) {
                        // Lat and Lng are valid numbers
                        binding.newPinLatEditText.setText("%.6f".format(lat))
                        binding.newPinLongEditText.setText("%.6f".format(lng))
                    } else {
                        // Lat and Lng are invalid (NaN / outside bounds)
                        binding.newPinLatEditText.setText("")
                        binding.newPinLongEditText.setText("")
                    }
                }
            }
            COORD_SYS_ID_KERTAU -> {
                val easting = binding.newPinEastingEditText.text.toString()
                val northing = binding.newPinNorthingEditText.text.toString()

                if (!easting.isBlank() && !northing.isBlank()) {
                    val latLngPair = getLatLngFromKertau(easting.toDouble(), northing.toDouble())
                    if (latLngPair != null) {
                        // Lat Lng are valid and kertau grids were within bounds
                        val (lat, lng) = latLngPair
                        binding.newPinLatEditText.setText("%.6f".format(lat))
                        binding.newPinLongEditText.setText("%.6f".format(lng))
                    } else {
                        // Lat and Lng are invalid (NaN / outside bounds)
                        binding.newPinLatEditText.setText("")
                        binding.newPinLongEditText.setText("")
                    }
                }

            }
            else -> throw IllegalArgumentException("Invalid coordinate system id: $coordSys")
        }

    }

    @SuppressLint("SetTextI18n")
    private fun updateGrids() {
        val lat = binding.newPinLatEditText.text.toString()
        val lng = binding.newPinLongEditText.text.toString()

        if (!lat.isBlank() && !lng.isBlank()) {
            when (coordSys) {
                COORD_SYS_ID_UTM -> {
                    val utmData = getUtmData(lat.toDouble(), lng.toDouble())
                    utmData?.let {
                        // UTM data is valid, set texts then return
                        binding.newPinEastingEditText.setText(floor(it.x).toInt().toString())
                        binding.newPinNorthingEditText.setText(floor(it.y).toInt().toString())
                        binding.newPinZoneDropdown.setText("${it.zone}${it.band}", false)
                        return
                    }
                }
                COORD_SYS_ID_KERTAU -> {
                    val kertauData = getKertauGrids(lat.toDouble(), lng.toDouble())
                    if (kertauData != null) {
                        // Kertau data is valid, set texts then return
                        val (easting, northing) = kertauData
                        binding.newPinEastingEditText.setText(floor(easting).toInt().toString())
                        binding.newPinNorthingEditText.setText(floor(northing).toInt().toString())
                        return
                    }
                }
                else -> throw IllegalArgumentException("Invalid coordinate system id: $coordSys")
            }
            // Grids are not valid for current coordinate system (out of bounds),
            // wipe zone, easting and northing fields
            binding.newPinEastingEditText.setText("")
            binding.newPinNorthingEditText.setText("")
            binding.newPinZoneDropdown.setText("", false)


        }
    }

    private fun updateSheetColor(colorIdx: Int = 0) {
        val color = ContextCompat.getColor(requireContext(), PIN_CARD_DARK_BACKGROUNDS[colorIdx])
        // binding.creatorSheetRoot.setBackgroundColor(color)
        val bgDraw = ContextCompat.getDrawable(requireContext(), R.drawable.bottom_sheet)
        bgDraw?.setTint(color)
        binding.creatorSheetRoot.background = bgDraw
    }
}
