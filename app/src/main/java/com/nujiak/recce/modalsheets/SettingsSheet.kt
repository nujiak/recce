package com.nujiak.recce.modalsheets

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nujiak.recce.*
import com.nujiak.recce.databinding.SheetSettingsBinding
import com.nujiak.recce.enums.SharedPrefsKey
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

    private var currentThemePref = ThemePreference.AUTO

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
            viewModel.updateCoordinateSystem(position)
            viewModel.sharedPreference.edit().putInt(SharedPrefsKey.COORDINATE_SYSTEM.key, position).apply()
        }
        binding.settingsAngleDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateAngleUnit(position)
            viewModel.sharedPreference.edit().putInt(SharedPrefsKey.ANGLE_UNIT.key, position).apply()
        }
        binding.settingsThemeDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.sharedPreference.edit().putInt(SharedPrefsKey.THEME_PREF.key, position).apply()

            binding.settingsThemeDropdown.dismissDropDown()
            if (position != currentThemePref.index) {
                currentThemePref = ThemePreference.atIndex(position)
                AppCompatDelegate.setDefaultNightMode(
                    when (position) {
                        ThemePreference.AUTO.index -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        ThemePreference.LIGHT.index -> AppCompatDelegate.MODE_NIGHT_NO
                        ThemePreference.DARK.index -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> throw IllegalArgumentException("Invalid theme pref index: $position")
                    }
                )
            }
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
        val sharedPreferences = viewModel.sharedPreference
        val coordSysId = sharedPreferences.getInt(SharedPrefsKey.COORDINATE_SYSTEM.key, 0)
        binding.settingsCoordsysDropdown.setText(coordinateSystems[coordSysId], false)
        val angleUnitId = sharedPreferences.getInt(SharedPrefsKey.ANGLE_UNIT.key, 0)
        binding.settingsAngleDropdown.setText(angleUnits[angleUnitId], false)
        sharedPreferences.getInt(SharedPrefsKey.THEME_PREF.key, 0).let { themePrefIndex ->
            binding.settingsThemeDropdown.setText(themePrefs[themePrefIndex], false)
            currentThemePref = ThemePreference.atIndex(themePrefIndex)
        }
    }

    private fun onResetPreferences() {
        viewModel.sharedPreference.edit {
            clear()
        }
        viewModel.chainsGuideShown = false

        // Restart Activity
        val intent = Intent(context, MainActivity::class.java)
        startActivity(intent)
        requireActivity().finish()

    }
}