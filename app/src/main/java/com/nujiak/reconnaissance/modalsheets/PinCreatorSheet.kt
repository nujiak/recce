package com.nujiak.reconnaissance.modalsheets

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Filter
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nujiak.reconnaissance.*
import com.nujiak.reconnaissance.database.Pin
import com.nujiak.reconnaissance.database.ReconDatabase
import com.nujiak.reconnaissance.databinding.SheetPinCreatorBinding
import com.nujiak.reconnaissance.mapping.*
import com.nujiak.reconnaissance.modalsheets.SettingsSheet.Companion.COORD_SYS_ID_KERTAU
import com.nujiak.reconnaissance.modalsheets.SettingsSheet.Companion.COORD_SYS_ID_MGRS
import com.nujiak.reconnaissance.modalsheets.SettingsSheet.Companion.COORD_SYS_ID_UTM
import java.util.*
import kotlin.math.floor

class PinCreatorSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetPinCreatorBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var pin: Pin

    private var isUpdate: Boolean = false
    private var isInputValid: Boolean = false

    private var coordSys = 0

    private lateinit var groupArrayAdapter: NoFilterArrayAdapter<String>

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = SheetPinCreatorBinding.inflate(inflater, container, false)

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

        // Fetch coordinate system setting
        coordSys = viewModel.coordinateSystem.value ?: 0
        setUpTextFields()

        // Set up exposed dropdown menus
        requireContext().let {
            binding.newPinColorDropdown.setAdapter(
                // Unable to use NoFilterArrayAdapter as it only supports List (not Array)
                ArrayAdapter(
                    it, R.layout.dropdown_menu_popup_item, COLORS
                )
            )
            binding.newPinZoneDropdown.setAdapter(
                NoFilterArrayAdapter(
                    it, R.layout.dropdown_menu_popup_item,
                    ZONE_BANDS
                )
            )
            groupArrayAdapter = NoFilterArrayAdapter(
                it,
                R.layout.dropdown_menu_popup_item,
                viewModel.getGroupNames().apply {
                    add(0, getString(R.string.new_group_plus))
                    add(1, getString(R.string.none))
                }
            )
            // Restore newly added group after configuration change (ie rotations)
            savedInstanceState?.getString("transient_group")?.let { currentGroup ->
                if (currentGroup !in groupArrayAdapter.items && currentGroup.isNotEmpty()) {
                    groupArrayAdapter.add(currentGroup)
                }
            }
            binding.newPinGroupDropdown.setAdapter(groupArrayAdapter)
        }

        // Get pin passed in as argument
        val argPin = arguments?.getParcelable<Pin>("pin")
        if (argPin != null) {
            pin = argPin
        } else {
            pin = Pin("", 0.0, 0.0)
        }

        // Check if this is an update and pre set data fields
        isUpdate = pin.pinId != 0L
        // Populate name field if this is an update
        binding.newPinNameEditText.setText(pin.name)
        if (isUpdate) {
            binding.creatorSheetHeader.text = getString(R.string.edit_pin)
        }
        if (pin.latitude != 0.0 || pin.longitude != 0.0) {
            // Populate lat long fields if available
            binding.newPinLatEditText.setText("%.6f".format(pin.latitude))
            binding.newPinLongEditText.setText("%.6f".format(pin.longitude))
        }
        binding.newPinColorDropdown.setText(COLORS[pin.color], false)
        binding.newPinGroupDropdown.setText(
            if (pin.group.isNotEmpty()) pin.group else getString(R.string.none),
            false
        )

        // Set up buttons
        binding.newChainSave.setOnClickListener { onCompleted() }
        if (isUpdate) {
            binding.newChainDelete.visibility = View.VISIBLE
            binding.newChainDelete.setOnClickListener {
                viewModel.deletePin(pin)
                dismiss()
            }
        }
        updateGrids()
        updateSheetColor(pin.color)

        // Expand bottom sheet fully
        (dialog as BottomSheetDialog).behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            peekHeight = (resources.displayMetrics.density * 144).toInt()
        }


            return binding.root
    }

    private fun onCompleted() {
        onValidateInput()
        if (isInputValid) {

            var group = binding.newPinGroupDropdown.text.toString().trim()
            if (group == getString(R.string.none)) {
                group = ""
            }

            // Create new pin instead of modifying old pin
            val newPin = pin.copy(
                name = binding.newPinNameEditText.text.toString(),
                latitude = binding.newPinLatEditText.text.toString().toDouble(),
                longitude = wrapLngDeg(binding.newPinLongEditText.text.toString().toDouble()),
                color = COLORS.indexOf(binding.newPinColorDropdown.text.toString()),
                group = group
            )

            when (isUpdate) {
                true -> {
                    viewModel.updatePin(newPin)
                    viewModel.showPinOnMap(newPin)
                }
                false -> {
                    viewModel.addPin(newPin)
                    viewModel.showPinOnMap(newPin.copy(pinId = viewModel.lastAddedId))
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
            name.contains(',', true) || name.contains(';', true) -> {
                binding.newPinNameInput.error = getString(R.string.pin_invalid_error)
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

        // Set up zone dropdown for UTM, or else hide the dropdown
        if (coordSys == COORD_SYS_ID_UTM) {
            binding.newPinZoneDropdown.setOnItemClickListener { _, _, _, _ ->
                updateLatLng()
            }
        } else {
            binding.newPinZoneInput.visibility = View.GONE
        }

        // Set up easting/northing EditTexts for relevant coordinate systems
        if (coordSys == COORD_SYS_ID_UTM || coordSys == COORD_SYS_ID_KERTAU) {
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
        if (coordSys == COORD_SYS_ID_MGRS) {
            binding.newPinMgrsEditText.setOnKeyListener { _, _, _ ->
                updateLatLng()
                false
            }
            binding.newPinGridGroup.visibility = View.GONE
            binding.newPinMgrsGroup.visibility = View.VISIBLE
        }

        binding.newPinColorDropdown.setOnItemClickListener { _, _, position, _ ->
            updateSheetColor(position)
        }

        binding.newPinColorDropdown.setOnClickListener {
            viewModel.hideKeyboardFromView(binding.root)
        }

        binding.newPinGroupDropdown.setOnClickListener {
            viewModel.hideKeyboardFromView(binding.root)
        }

        binding.newPinGridSystem.text = getString(
            when (coordSys) {
                COORD_SYS_ID_UTM -> R.string.utm
                COORD_SYS_ID_MGRS -> R.string.mgrs
                COORD_SYS_ID_KERTAU -> R.string.kertau
                else -> throw IllegalArgumentException("Invalid coordinate system id: $coordSys")
            }
        )

        binding.newPinGroupDropdown.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                onAddNewGroup()
                binding.newPinGroupDropdown.setText(
                    if (pin.group != "") pin.group else getString(
                        R.string.none
                    )
                )
            }
        }
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
                                band.single()
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
            COORD_SYS_ID_MGRS -> {
                val mgrsData = parseMgrsFrom(binding.newPinMgrsEditText.text.toString())
                val latLng = mgrsData?.toUtmData()?.toLatLng()

                if (latLng != null) {
                    binding.newPinLatEditText.setText("%.6f".format(latLng.first))
                    binding.newPinLongEditText.setText("%.6f".format(latLng.second))
                } else {
                    binding.newPinLatEditText.setText("")
                    binding.newPinLongEditText.setText("")
                }
            }
            COORD_SYS_ID_KERTAU -> {
                val easting = binding.newPinEastingEditText.text.toString()
                val northing = binding.newPinNorthingEditText.text.toString()

                if (!easting.isBlank() && !northing.isBlank()) {
                    val latLngPair =
                        getLatLngFromKertau(
                            easting.toDouble(),
                            northing.toDouble()
                        )
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
                COORD_SYS_ID_MGRS -> {
                    binding.newPinMgrsEditText.setText(
                        getMgrsData(
                            lat.toDouble(),
                            lng.toDouble()
                        )?.toSingleLine(includeWhitespace = true)
                    )
                    return
                }
                COORD_SYS_ID_KERTAU -> {
                    val kertauData =
                        getKertauGrids(
                            lat.toDouble(),
                            lng.toDouble()
                        )
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

    private fun onAddNewGroup() {
        val alertDialog = AlertDialog.Builder(requireActivity())
            .setView(R.layout.dialog_new_group)
            .create()
        alertDialog.show()

        // Set up layout and interactions of the dialog
        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val inputLayout = alertDialog.findViewById<TextInputLayout>(R.id.new_pin_group_input)
        val editText = alertDialog.findViewById<TextInputEditText>(R.id.new_pin_group_edit_text)
        editText.setOnKeyListener { _, _, _ ->
            editText.text?.let {
                if (it.length <= 12) {
                    inputLayout.error = null
                }
            }
            true
        }
        val posBtn = alertDialog.findViewById<Button>(R.id.new_pin_group_add_button)
        posBtn.setOnClickListener {
            val newGroup = editText.text?.trim()
            when {
                newGroup.isNullOrEmpty() -> {
                    inputLayout.error = getString(R.string.group_empty_error)
                }
                newGroup.length > 12 -> {
                    inputLayout.error = getString(R.string.group_too_long_error)
                }
                newGroup.toString().toLowerCase(Locale.ROOT)
                        == getString(R.string.none).toLowerCase(Locale.ROOT) -> {
                    inputLayout.error = getString(R.string.group_invalid_error)
                }
                else -> {
                    // Group name is valid, add to ArrayAdapter and set in AutoCompleteTextView
                    groupArrayAdapter.add(newGroup.toString())
                    binding.newPinGroupDropdown.setText(newGroup.toString(), false)
                    alertDialog.dismiss()
                }
            }
        }
    }

    private fun updateSheetColor(colorIdx: Int = 0) {
        val color = ContextCompat.getColor(requireContext(), PIN_CARD_DARK_BACKGROUNDS[colorIdx])
        val bgDraw = ContextCompat.getDrawable(requireContext(), R.drawable.bottom_sheet)
        bgDraw?.setTint(color)
        binding.root.background = bgDraw
    }

    /**
     * Custom ArrayAdapter for no filtering
     */
    private class NoFilterArrayAdapter<T>(context: Context, resource: Int, val items: List<T>) :
        ArrayAdapter<T>(context, resource, items) {

        private val filter = NoFilter()

        override fun getFilter(): Filter = filter

        private inner class NoFilter : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                return FilterResults().apply {
                    values = items
                    count = items.size
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                notifyDataSetChanged()
            }

        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("transient_group", binding.newPinGroupDropdown.text.toString().trim())
        super.onSaveInstanceState(outState)
    }
}
