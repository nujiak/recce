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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nujiak.recce.MainViewModel
import com.nujiak.recce.NoFilterArrayAdapter
import com.nujiak.recce.R
import com.nujiak.recce.database.Chain
import com.nujiak.recce.databinding.SheetChainCreatorBinding
import com.nujiak.recce.utils.COLORS
import com.nujiak.recce.utils.PIN_CARD_BACKGROUNDS
import com.nujiak.recce.utils.animateColor
import com.nujiak.recce.utils.dpToPx
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChainCreatorSheet : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: SheetChainCreatorBinding
    private lateinit var chain: Chain

    private var isUpdate: Boolean = false
    private var isInputValid: Boolean = false

    private lateinit var groupArrayAdapter: NoFilterArrayAdapter<String>

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SheetChainCreatorBinding.inflate(inflater, container, false)

        setUpTextFields()

        // Set up exposed dropdown menus
        requireContext().let {
            binding.newChainColorDropdown.setAdapter(
                NoFilterArrayAdapter(
                    it, R.layout.dropdown_menu_popup_item, COLORS
                )
            )
            binding.newChainTypeDropdown.setAdapter(
                NoFilterArrayAdapter(
                    it,
                    R.layout.dropdown_menu_popup_item,
                    arrayOf(R.string.route, R.string.area).map { res -> getString(res) }
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
            binding.newChainGroupDropdown.setAdapter(groupArrayAdapter)
        }

        // Get chain passed in as argument
        val argChain = arguments?.getParcelable<Chain>("chain")
        chain = argChain!!

        // Check if this is an update and pre set data fields
        if (!chain.name.isBlank()) {
            // Populate name field if this is an update
            binding.newChainNameEditText.setText(chain.name)
            binding.creatorSheetHeader.text = getString(R.string.edit)
            isUpdate = true
        }
        binding.newChainColorDropdown.setText(COLORS[chain.color], false)
        binding.newChainGroupDropdown.setText(
            if (chain.group.isNotEmpty()) chain.group else getString(R.string.none),
            false
        )
        binding.newChainTypeDropdown.setText(
            getString(if (chain.cyclical) R.string.area else R.string.route),
            false
        )
        binding.newChainDescriptionEditText.setText(chain.description)

        // Set up buttons
        binding.newChainSave.setOnClickListener { onCompleted() }
        if (isUpdate) {
            binding.newChainDelete.visibility = View.VISIBLE
            binding.newChainDelete.setOnClickListener {
                viewModel.deleteChain(chain)
                dismiss()
            }
        }
        updateSheetColor(chain.color)

        // Expand bottom sheet fully
        (dialog as BottomSheetDialog).behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            peekHeight = (resources.dpToPx(168f)).toInt()
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        updateSheetColor(COLORS.indexOf(binding.newChainColorDropdown.text.toString()))
    }

    private fun onCompleted() {
        onValidateInput()
        if (isInputValid) {

            var group = binding.newChainGroupDropdown.text.toString().trim()
            if (group == getString(R.string.none)) {
                group = ""
            }

            // Create new chain instead of modifying old pin
            val newChain = chain.copy(
                name = binding.newChainNameEditText.text.toString(),
                nodes = chain.nodes,
                color = COLORS.indexOf(binding.newChainColorDropdown.text.toString()),
                group = group,
                cyclical = binding.newChainTypeDropdown.text.toString() == getString(R.string.area),
                description = binding.newChainDescriptionEditText.text
                    .toString()
                    .replace(Regex("\\n{3,}"), "\n\n")
                    .trim()
            )

            when (isUpdate) {
                true -> viewModel.updateChain(newChain)
                false -> viewModel.addChain(newChain)
            }
            viewModel.clearChainPlot()

            // Open Chain Info
            when {
                isUpdate -> viewModel.showChainInfo(chain.chainId)
                else -> viewModel.showChainInfo(viewModel.lastAddedId)
            }

            dismiss()
        }
    }

    private fun onValidateInput() {

        isInputValid = true

        val name = binding.newChainNameEditText.text
        when {
            name.isNullOrBlank() -> {
                binding.newChainNameInput.error = getString(R.string.name_blank_error)
                isInputValid = false
            }
            name.length > 20 -> {
                binding.newChainNameInput.error = getString(R.string.name_too_long_error)
                isInputValid = false
            }
        }
    }

    private fun setUpTextFields() {
        // Set up fields' onKey listeners
        binding.newChainNameEditText.setOnKeyListener { _, _, _ ->
            if (binding.newChainNameEditText.length() <= 20) {
                binding.newChainNameInput.error = null
            }
            false
        }
        binding.newChainColorDropdown.apply {
            setOnItemClickListener { _, _, position, _ ->
                updateSheetColor(position)
                viewModel.hideKeyboardFromView(binding.root)
            }

            setOnClickListener {
                viewModel.hideKeyboardFromView(binding.root)
            }
        }

        binding.newChainGroupDropdown.apply {
            setOnClickListener {
                viewModel.hideKeyboardFromView(binding.root)
            }

            setOnItemClickListener { _, _, position, _ ->
                if (position == 0) {
                    onAddNewGroup()
                    binding.newChainGroupDropdown.setText(
                        if (chain.group != "") chain.group else getString(R.string.none)
                    )
                }
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
                    binding.newChainGroupDropdown.setText(newGroup.toString(), false)
                    alertDialog.dismiss()
                }
            }
        }
    }

    private fun updateSheetColor(colorIdx: Int = 0) {
        context?.let {
            val color = ContextCompat.getColor(it, PIN_CARD_BACKGROUNDS[colorIdx])

            animateColor(binding.root.background, color, 150) { intermediateColor ->
                binding.root.setBackgroundColor(intermediateColor)
                binding.newChainSave.setTextColor(intermediateColor)
                activity?.window?.navigationBarColor = intermediateColor // Set navigation bar color
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        // Reset navigation bar color
        activity?.window?.navigationBarColor =
            ContextCompat.getColor(requireContext(), android.R.color.transparent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("transient_group", binding.newChainGroupDropdown.text.toString().trim())
        super.onSaveInstanceState(outState)
    }
}
