package com.nujiak.reconnaissance.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.modalsheets.SettingsSheet

class OnboardingTitleFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val viewModel: OnboardingViewModel by requireActivity().viewModels()
        val view = inflater.inflate(R.layout.onboarding_title, container)

        view.findViewById<Button>(R.id.onboarding_title_next).setOnClickListener {
            viewModel.toPage(OnboardingViewModel.COORD_SYS_INDEX)
        }

        view.findViewById<Button>(R.id.onboarding_title_ns).setOnClickListener {
            viewModel.coordSysId = SettingsSheet.COORD_SYS_ID_KERTAU
            viewModel.angleUnitId = SettingsSheet.ANGLE_UNIT_ID_NATO_MILS
            viewModel.toPage(OnboardingViewModel.NS_ALL_SET_INDEX)
        }

        return view
    }
}

class OnboardingGridsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val viewModel: OnboardingViewModel by requireActivity().viewModels()

        val view = inflater.inflate(R.layout.onboarding_grids, container)

        val utmBtn = view.findViewById<RadioButton>(R.id.onboarding_radio_utm)
        val mgrsBtn = view.findViewById<RadioButton>(R.id.onboarding_radio_mgrs)
        val kertauBtn = view.findViewById<RadioButton>(R.id.onboarding_radio_kertau)

        when (viewModel.coordSysId) {
            SettingsSheet.COORD_SYS_ID_UTM ->
                utmBtn.isChecked = true
            SettingsSheet.COORD_SYS_ID_MGRS ->
                mgrsBtn.isChecked = true
            SettingsSheet.COORD_SYS_ID_KERTAU ->
                kertauBtn.isChecked = true
        }

        view.findViewById<Button>(R.id.onboarding_grids_next).setOnClickListener {
            viewModel.coordSysId = when {
                    utmBtn.isChecked -> SettingsSheet.COORD_SYS_ID_UTM
                    mgrsBtn.isChecked -> SettingsSheet.COORD_SYS_ID_MGRS
                    kertauBtn.isChecked -> SettingsSheet.COORD_SYS_ID_KERTAU
                    else -> SettingsSheet.COORD_SYS_ID_UTM
            }
            viewModel.toPage(OnboardingViewModel.ANGLE_INDEX)
        }
        view.findViewById<Button>(R.id.onboarding_grids_prev).setOnClickListener {
            viewModel.coordSysId = when {
                utmBtn.isChecked -> SettingsSheet.COORD_SYS_ID_UTM
                mgrsBtn.isChecked -> SettingsSheet.COORD_SYS_ID_MGRS
                kertauBtn.isChecked -> SettingsSheet.COORD_SYS_ID_KERTAU
                else -> SettingsSheet.COORD_SYS_ID_UTM
            }
            viewModel.toPage(OnboardingViewModel.TITLE_INDEX)
        }

        return view
    }
}

class OnboardingAnglesFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val viewModel: OnboardingViewModel by requireActivity().viewModels()

        val view = inflater.inflate(R.layout.onboarding_angles, container)

        val degBtn = view.findViewById<RadioButton>(R.id.onboarding_radio_deg)
        val milsBtn = view.findViewById<RadioButton>(R.id.onboarding_radio_mils)

        when (viewModel.angleUnitId) {
            SettingsSheet.ANGLE_UNIT_ID_DEG -> degBtn.isChecked = true
            SettingsSheet.ANGLE_UNIT_ID_NATO_MILS -> milsBtn.isChecked = true
        }

        view.findViewById<Button>(R.id.onboarding_angles_next).setOnClickListener {
            viewModel.angleUnitId = when {
                    degBtn.isChecked -> SettingsSheet.ANGLE_UNIT_ID_DEG
                    milsBtn.isChecked -> SettingsSheet.ANGLE_UNIT_ID_NATO_MILS
                    else -> SettingsSheet.ANGLE_UNIT_ID_DEG
            }
            viewModel.toPage(OnboardingViewModel.ALL_SET_INDEX)
        }
        view.findViewById<Button>(R.id.onboarding_angles_prev).setOnClickListener {
            viewModel.angleUnitId = when {
                degBtn.isChecked -> SettingsSheet.ANGLE_UNIT_ID_DEG
                milsBtn.isChecked -> SettingsSheet.ANGLE_UNIT_ID_NATO_MILS
                else -> SettingsSheet.ANGLE_UNIT_ID_DEG
            }
            viewModel.toPage(OnboardingViewModel.COORD_SYS_INDEX)
        }

        return view
    }
}

class OnboardingEndFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val viewModel: OnboardingViewModel by requireActivity().viewModels()

        val view = inflater.inflate(R.layout.onboarding_end, container)

        view.findViewById<Button>(R.id.onboarding_end_start).setOnClickListener {
            viewModel.endOnboarding()
        }
        view.findViewById<Button>(R.id.onboarding_end_back).setOnClickListener {
            viewModel.toPage(OnboardingViewModel.ANGLE_INDEX)
        }

        return view
    }
}

class OnboardingNSFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val viewModel: OnboardingViewModel by requireActivity().viewModels()

        val view = inflater.inflate(R.layout.onboarding_ns, container)

        view.findViewById<Button>(R.id.onboarding_ns_start).setOnClickListener {
            viewModel.endOnboarding()
        }
        view.findViewById<Button>(R.id.onboarding_ns_back).setOnClickListener {
            viewModel.toPage(OnboardingViewModel.TITLE_INDEX)
        }

        return view
    }
}