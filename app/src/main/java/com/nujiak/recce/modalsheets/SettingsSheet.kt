package com.nujiak.recce.modalsheets

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nujiak.recce.MainActivity
import com.nujiak.recce.MainViewModel
import com.nujiak.recce.NoFilterArrayAdapter
import com.nujiak.recce.R
import com.nujiak.recce.databinding.SheetSettingsBinding
import com.nujiak.recce.enums.AngleUnit
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.enums.ThemePreference
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsSheet : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: SheetSettingsBinding
    private val coordinateSystems: Array<String>
        by lazy { resources.getStringArray(R.array.coordinate_systems) }
    private val angleUnits: Array<String> by lazy { resources.getStringArray(R.array.angle_units) }
    private val themePrefs: Array<String> by lazy { resources.getStringArray(R.array.theme_prefs) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        // Set up View Binding
        binding = SheetSettingsBinding.inflate(inflater, container, false)

        // Set up exposed dropdown menus content
        binding.settingsCoordsysDropdown.setAdapter(
            NoFilterArrayAdapter(
                requireContext(), R.layout.dropdown_menu_popup_item, coordinateSystems
            )
        )
        binding.settingsAngleDropdown.setAdapter(
            NoFilterArrayAdapter(
                requireContext(), R.layout.dropdown_menu_popup_item, angleUnits
            )
        )
        binding.settingsThemeDropdown.setAdapter(
            NoFilterArrayAdapter(requireContext(), R.layout.dropdown_menu_popup_item, themePrefs)
        )

        // Set up exposed dropdown menus on-click
        binding.settingsCoordsysDropdown.setOnItemClickListener { _, _, position, _ ->
            val coordSys = CoordinateSystem.atIndex(position)
            viewModel.updateCoordinateSystem(coordSys)
        }

        binding.settingsAngleDropdown.setOnItemClickListener { _, _, position, _ ->
            val angleUnit = AngleUnit.atIndex(position)
            viewModel.updateAngleUnit(angleUnit)
        }

        binding.settingsThemeDropdown.setOnItemClickListener { _, _, position, _ ->
            binding.settingsThemeDropdown.dismissDropDown()
            viewModel.theme = ThemePreference.atIndex(position)
        }

        // Set up reset guides
        binding.settingsResetGuides.setOnClickListener {
            onResetPreferences()
        }

        setUpPreferences()

        // Expand bottom sheet fully
        (dialog as BottomSheetDialog).behavior.apply {
            state = STATE_EXPANDED
            skipCollapsed = true
        }

        return binding.root
    }

    private fun setUpPreferences() {
        val coordSysId = viewModel.coordinateSystem.value?.index ?: 0
        binding.settingsCoordsysDropdown.setText(coordinateSystems[coordSysId], false)

        val angleUnitId = viewModel.angleUnit.value?.index ?: 0
        binding.settingsAngleDropdown.setText(angleUnits[angleUnitId], false)

        val themePrefId = viewModel.theme.index
        binding.settingsThemeDropdown.setText(themePrefs[themePrefId], false)

    }

    private fun onResetPreferences() {
        viewModel.clearSharedPreferences()

        // Restart Activity
        val intent = Intent(context, MainActivity::class.java)
        startActivity(intent)
        requireActivity().finish()
    }
}
