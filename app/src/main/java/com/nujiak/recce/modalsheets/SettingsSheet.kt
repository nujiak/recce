package com.nujiak.recce.modalsheets

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nujiak.recce.MainActivity
import com.nujiak.recce.MainViewModel
import com.nujiak.recce.NoFilterArrayAdapter
import com.nujiak.recce.R
import com.nujiak.recce.databinding.SheetSettingsBinding
import com.nujiak.recce.fragments.MapFragment
import com.nujiak.recce.onboarding.OnboardingActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsSheet : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: SheetSettingsBinding
    private val coordinateSystems: Array<String>
            by lazy { resources.getStringArray(R.array.coordinate_systems) }
    private val angleUnits: Array<String> by lazy { resources.getStringArray(R.array.angle_units) }
    private val themePrefs: Array<String> by lazy { resources.getStringArray(R.array.theme_prefs) }

    private var currentThemePrefId = THEME_PREF_AUTO
    private var newThemePrefId = THEME_PREF_AUTO

    companion object {
        const val COORD_SYS_KEY = "coordinate_system"
        const val COORD_SYS_ID_UTM = 0
        const val COORD_SYS_ID_MGRS = 1
        const val COORD_SYS_ID_KERTAU = 2
        const val COORD_SYS_ID_LATLNG = 3

        const val ANGLE_UNIT_KEY = "angle_unit"
        const val ANGLE_UNIT_ID_DEG = 0
        const val ANGLE_UNIT_ID_NATO_MILS = 1

        const val THEME_PREF_KEY = "theme_pref"
        const val THEME_PREF_AUTO = 0
        const val THEME_PREF_LIGHT = 1
        const val THEME_PREF_DARK = 2
    }

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
            viewModel.sharedPreference.edit().putInt(COORD_SYS_KEY, position).apply()
        }
        binding.settingsAngleDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateAngleUnit(position)
            viewModel.sharedPreference.edit().putInt(ANGLE_UNIT_KEY, position).apply()
        }
        binding.settingsThemeDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.sharedPreference.edit().putInt(THEME_PREF_KEY, position).apply()
            newThemePrefId = position
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
        val coordSysId = sharedPreferences.getInt(COORD_SYS_KEY, 0)
        binding.settingsCoordsysDropdown.setText(coordinateSystems[coordSysId], false)
        val angleUnitId = sharedPreferences.getInt(ANGLE_UNIT_KEY, 0)
        binding.settingsAngleDropdown.setText(angleUnits[angleUnitId], false)
        sharedPreferences.getInt(THEME_PREF_KEY, 0).let {
            binding.settingsThemeDropdown.setText(themePrefs[it], false)
            currentThemePrefId = it
            newThemePrefId = it
        }
    }

    private fun onResetPreferences() {
        viewModel.sharedPreference.edit().apply {
            putBoolean(MapFragment.CHAINS_GUIDE_SHOWN_KEY, false)
            putBoolean(OnboardingActivity.ONBOARDING_COMPLETED_KEY, false)
            remove(THEME_PREF_KEY)
            remove(ANGLE_UNIT_KEY)
            remove(COORD_SYS_KEY)
        }.apply()
        viewModel.chainsGuideShown = false

        // Restart Activity
        val intent = Intent(context, MainActivity::class.java)
        startActivity(intent)
        requireActivity().finish()

    }

    override fun onDismiss(dialog: DialogInterface) {
        if (newThemePrefId != currentThemePrefId) {
            AppCompatDelegate.setDefaultNightMode(
                when (newThemePrefId) {
                    THEME_PREF_AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    THEME_PREF_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    THEME_PREF_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> throw IllegalArgumentException("Invalid theme pref index: $newThemePrefId")
                }
            )
        }
        super.onDismiss(dialog)
    }
}