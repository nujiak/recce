package com.nujiak.reconnaissance.modalsheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nujiak.reconnaissance.MainViewModel
import com.nujiak.reconnaissance.MainViewModelFactory
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.database.ReconDatabase
import com.nujiak.reconnaissance.databinding.SheetSettingsBinding
import com.nujiak.reconnaissance.fragments.MapFragment

class SettingsSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetSettingsBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var coordinateSystems: Array<String>
    private lateinit var angleUnits: Array<String>

    companion object {
        const val COORD_SYS_KEY = "coordinate_system"
        const val COORD_SYS_ID_UTM = 0
        const val COORD_SYS_ID_MGRS = 1
        const val COORD_SYS_ID_KERTAU = 2

        const val ANGLE_UNIT_KEY = "angle_unit"
        const val ANGLE_UNIT_ID_DEG = 0
        const val ANGLE_UNIT_ID_NATO_MILS = 1
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

        // Set up exposed dropdown menus
        coordinateSystems = resources.getStringArray(R.array.coordinate_systems)
        binding.settingsCoordsysDropdown.setAdapter(
            ArrayAdapter(
                requireContext(),
                R.layout.dropdown_menu_popup_item,
                coordinateSystems
            )
        )
        angleUnits = resources.getStringArray(R.array.angle_units)
        binding.settingsAngleDropdown.setAdapter(
            ArrayAdapter(
                requireContext(),
                R.layout.dropdown_menu_popup_item,
                angleUnits
            )
        )

        binding.settingsCoordsysDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateCoordinateSystem(position)
            viewModel.sharedPreference.edit().putInt(COORD_SYS_KEY, position).apply()
        }
        binding.settingsAngleDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateAngleUnit(position)
            viewModel.sharedPreference.edit().putInt(ANGLE_UNIT_KEY, position).apply()
        }

        // Set up reset guides
        binding.settingsResetGuides.setOnClickListener {
            viewModel.sharedPreference.edit().apply {
                putBoolean(MapFragment.CHAINS_GUIDE_SHOWN_KEY, false)
            }.apply()
            viewModel.chainsGuideShown = false
            binding.settingsResetGuides.isEnabled = false
        }

        setUpPreferences()

        return binding.root
    }

    private fun setUpPreferences() {
        val sharedPreferences = viewModel.sharedPreference
        val coordSysId = sharedPreferences.getInt(COORD_SYS_KEY, 0)
        binding.settingsCoordsysDropdown.setText(coordinateSystems[coordSysId], false)
        val angleUnitId = sharedPreferences.getInt(ANGLE_UNIT_KEY, 0)
        binding.settingsAngleDropdown.setText(angleUnits[angleUnitId], false)
    }
}