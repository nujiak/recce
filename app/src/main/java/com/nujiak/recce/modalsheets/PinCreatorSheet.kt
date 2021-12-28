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
import com.nujiak.recce.mapping.Mapping
import com.nujiak.recce.utils.COLORS
import com.nujiak.recce.utils.PIN_CARD_DARK_BACKGROUNDS
import com.nujiak.recce.utils.animateColor
import com.nujiak.recce.utils.dpToPx
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PinCreatorSheet : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: SheetPinCreatorBinding
    private lateinit var pin: Pin
    private var updatedPin: Pin? = null

    private var isUpdate: Boolean = false

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

        // Set up exposed dropdown menus
        binding.newPinColorDropdown.setAdapter(
            NoFilterArrayAdapter(requireContext(), R.layout.dropdown_menu_popup_item, COLORS)
        )
        groupArrayAdapter = NoFilterArrayAdapter(
            requireContext(),
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

        // Get pin passed in as argument
        pin = arguments?.getParcelable("pin") ?: Pin("", 0.0, 0.0)

        // Check if this is an update and pre set data fields
        isUpdate = pin.pinId != 0L
        // Populate name field if this is an update
        binding.newPinNameEditText.setText(pin.name)
        if (isUpdate) {
            binding.creatorSheetHeader.text = getString(R.string.edit_pin)
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
        updateSheetColor(pin.color)

        val coordinate = Mapping.transformTo(coordSys, LatLng(pin.latitude, pin.longitude))
        binding.newPinGridsEditText.setText(coordinate?.toString() ?: "")

        // Expand bottom sheet fully
        (dialog as BottomSheetDialog).behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            peekHeight = (resources.dpToPx(144f)).toInt()
        }

        return binding.root
    }

    private fun onCompleted() {
        // Validate pin name
        val name = binding.newPinNameEditText.text
        var isInputValid = true
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

        // Validate coordinate
        val coordinate = Mapping.parse(binding.newPinGridsEditText.text.toString(), coordSys)

        if (coordinate == null) {
            binding.newPinGridsInput.error = getString(R.string.invalid_coordinate)
        }

        if (!isInputValid || coordinate == null) {
            return
        }

        var group = binding.newPinGroupDropdown.text.toString().trim()
        if (group == getString(R.string.none)) {
            group = ""
        }

        // Create new pin instead of modifying old pin
        updatedPin = pin.copy(
            name = binding.newPinNameEditText.text.toString(),
            latitude = coordinate.latLng.latitude,
            longitude = coordinate.latLng.longitude,
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

    private fun setUpTextFields() {
        // Set up fields' onKey listeners
        binding.newPinNameEditText.setOnKeyListener { _, _, _ ->
            if (binding.newPinNameEditText.length() <= 20) {
                binding.newPinNameInput.error = null
            }
            false
        }

        binding.newPinGridsEditText.setOnKeyListener { _, _, _ ->
            binding.newPinGridsInput.error = null
            false
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
                CoordinateSystem.BNG -> R.string.bng
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
