package com.nujiak.recce.modalsheets

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nujiak.recce.MainViewModel
import com.nujiak.recce.NoFilterArrayAdapter
import com.nujiak.recce.R
import com.nujiak.recce.database.Pin
import com.nujiak.recce.databinding.SheetPinCreatorBinding
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.mapping.Coordinate
import com.nujiak.recce.mapping.Mapping
import com.nujiak.recce.mapping.ZONE_BANDS
import com.nujiak.recce.mapping.getUtmZoneAndBand
import com.nujiak.recce.utils.COLORS
import com.nujiak.recce.utils.PIN_CARD_DARK_BACKGROUNDS
import com.nujiak.recce.utils.animateColor
import com.nujiak.recce.utils.dpToPx
import com.nujiak.recce.utils.wrapLngDeg
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import kotlin.math.floor

@AndroidEntryPoint
class PinCreatorSheet : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: SheetPinCreatorBinding
    private lateinit var pin: Pin
    private var updatedPin: Pin? = null

    private var isUpdate: Boolean = false
    private var isInputValid: Boolean = false

    private var coordSys = CoordinateSystem.atIndex(0)

    private lateinit var groupArrayAdapter: NoFilterArrayAdapter<String>

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SheetPinCreatorBinding.inflate(inflater, container, false)

        // Fetch coordinate system setting
        coordSys = viewModel.coordinateSystem.value ?: CoordinateSystem.atIndex(0)
        setUpTextFields()

        binding.newPinCustomGridsGroup.visibility = when (coordSys) {
            CoordinateSystem.WGS84 -> View.GONE
            else -> View.VISIBLE
        }

        // Set up exposed dropdown menus
        requireContext().let {
            binding.newPinColorDropdown.setAdapter(
                NoFilterArrayAdapter(
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
        pin = if (argPin != null) {
            argPin
        } else {
            Pin("", 0.0, 0.0)
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
            binding.newPinLatEditText.setText("%.6f".format(Locale.US, pin.latitude))
            binding.newPinLongEditText.setText("%.6f".format(Locale.US, pin.longitude))
        }
        binding.newPinColorDropdown.setText(COLORS[pin.color], false)
        binding.newPinGroupDropdown.setText(
            if (pin.group.isNotEmpty()) pin.group else getString(R.string.none),
            false
        )
        binding.newPinDescriptionEditText.setText(pin.description)

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
            peekHeight = (resources.dpToPx(144f)).toInt()
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
            updatedPin = pin.copy(
                name = binding.newPinNameEditText.text.toString(),
                latitude = binding.newPinLatEditText.text.toString().toDouble(),
                longitude = wrapLngDeg(binding.newPinLongEditText.text.toString().toDouble()),
                color = COLORS.indexOf(binding.newPinColorDropdown.text.toString()),
                group = group,
                description = binding.newPinDescriptionEditText.text
                    .toString()
                    .replace(Regex("\\n{3,}"), "\n\n")
                    .trim()
            )

            when (isUpdate) {
                true -> viewModel.updatePin(updatedPin!!)
                false -> viewModel.addPin(updatedPin!!)
            }

            // Open Pin Info
            when {
                isUpdate -> viewModel.showPinInfo(pin.pinId)
                updatedPin != null -> viewModel.showPinOnMap(updatedPin!!.copy(pinId = viewModel.lastAddedId))
            }

            dismiss()
        }
    }

    private fun onValidateInput() {

        isInputValid = true

        // Validate pin name
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
                CoordinateSystem.UTM -> R.string.utm
                CoordinateSystem.MGRS -> R.string.mgrs
                CoordinateSystem.KERTAU -> R.string.kertau
                CoordinateSystem.WGS84 -> R.string.lat_lng
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
            CoordinateSystem.UTM -> {
                val zoneBand = binding.newPinZoneDropdown.text.toString()
                val easting = binding.newPinEastingEditText.text.toString()
                val northing = binding.newPinNorthingEditText.text.toString()

                if (zoneBand.isBlank() || easting.isBlank() || northing.isBlank()) {
                    return
                }

                val zone = zoneBand.slice(if (zoneBand.length == 3) 0..1 else 0..0).toInt()
                val band = zoneBand[if (zoneBand.length == 3) 2 else 1]

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
            CoordinateSystem.MGRS -> {
                val mgrsCoord = Mapping.parseMgrs(binding.newPinMgrsEditText.text.toString())
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
                val easting = binding.newPinEastingEditText.text.toString()
                val northing = binding.newPinNorthingEditText.text.toString()

                if (!easting.isBlank() && !northing.isBlank()) {
                    val coord = Mapping.parseKertau1948(easting.toDouble(), northing.toDouble())
                    val latLng = coord.latLng
                    binding.newPinLatEditText.setText("%.6f".format(Locale.US, latLng.latitude))
                    binding.newPinLongEditText.setText("%.6f".format(Locale.US, latLng.longitude))
                }
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

        val coordinate = Mapping.transformTo(coordSys, LatLng(lat, lng)) ?: return

        when (coordSys) {
            CoordinateSystem.UTM -> {
                // UTM data is valid, set texts then return
                binding.newPinEastingEditText.setText(floor(coordinate.x).toInt().toString())
                binding.newPinNorthingEditText.setText(floor(coordinate.y).toInt().toString())

                // TODO: Replace this workaround
                val (zone, band) = getUtmZoneAndBand(lat, lng)
                binding.newPinZoneDropdown.setText("$zone$band", false)
                return
            }
            CoordinateSystem.MGRS -> {
                binding.newPinMgrsEditText.setText(Coordinate.toString())
                return
            }
            CoordinateSystem.KERTAU -> {
                binding.newPinEastingEditText.setText(floor(coordinate.x).toInt().toString())
                binding.newPinNorthingEditText.setText(floor(coordinate.y).toInt().toString())
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
                newGroup.toString().equals(getString(R.string.none), ignoreCase = true) -> {
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
        context?.let {
            val color = ContextCompat.getColor(it, PIN_CARD_DARK_BACKGROUNDS[colorIdx])

            animateColor(binding.root.background, color, 150) { intermediateColor ->
                binding.root.setBackgroundColor(intermediateColor)
                binding.newChainSave.setTextColor(intermediateColor)
                activity?.window?.navigationBarColor = intermediateColor // Set navigation bar color
            }
        }
    }

    override fun onStart() {
        super.onStart()
        updateSheetColor(COLORS.indexOf(binding.newPinColorDropdown.text.toString()))
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        // Reset navigation bar color
        activity?.window?.navigationBarColor =
            ContextCompat.getColor(requireContext(), android.R.color.transparent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("transient_group", binding.newPinGroupDropdown.text.toString().trim())
        super.onSaveInstanceState(outState)
    }
}
