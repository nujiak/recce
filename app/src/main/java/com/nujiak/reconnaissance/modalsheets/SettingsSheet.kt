package com.nujiak.reconnaissance.modalsheets

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nujiak.reconnaissance.MainViewModel
import com.nujiak.reconnaissance.MainViewModelFactory
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.database.ReconDatabase
import com.nujiak.reconnaissance.databinding.SheetSettingsBinding
import com.nujiak.reconnaissance.fragments.MapFragment
import com.nujiak.reconnaissance.onboarding.OnboardingActivity

class SettingsSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetSettingsBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var coordinateSystems: Array<String>
    private lateinit var angleUnits: Array<String>
    private lateinit var themePrefs: Array<String>

    private var currentThemePrefId = THEME_PREF_AUTO
    private var newThemePrefId = THEME_PREF_AUTO

    companion object {
        const val COORD_SYS_KEY = "coordinate_system"
        const val COORD_SYS_ID_UTM = 0
        const val COORD_SYS_ID_MGRS = 1
        const val COORD_SYS_ID_KERTAU = 2

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
    ): View? {

        // Set up View Binding
        binding = SheetSettingsBinding.inflate(inflater, container, false)

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

        // Set up exposed dropdown menus content
        coordinateSystems = resources.getStringArray(R.array.coordinate_systems)
        binding.settingsCoordsysDropdown.setAdapter(
            ArrayAdapter(
                requireContext(), R.layout.dropdown_menu_popup_item, coordinateSystems
            )
        )
        angleUnits = resources.getStringArray(R.array.angle_units)
        binding.settingsAngleDropdown.setAdapter(
            ArrayAdapter(
                requireContext(), R.layout.dropdown_menu_popup_item, angleUnits
            )
        )
        themePrefs = resources.getStringArray(R.array.theme_prefs)
        binding.settingsThemeDropdown.setAdapter(
            ArrayAdapter(requireContext(), R.layout.dropdown_menu_popup_item, themePrefs)
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
            viewModel.sharedPreference.edit().apply {
                putBoolean(MapFragment.CHAINS_GUIDE_SHOWN_KEY, false)
                putBoolean(OnboardingActivity.ONBOARDING_COMPLETED_KEY, false)
            }.apply()
            viewModel.chainsGuideShown = false
            binding.settingsResetGuides.isEnabled = false
            binding.settingsResetGuides.backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_dark)
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