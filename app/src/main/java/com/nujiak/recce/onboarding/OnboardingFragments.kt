package com.nujiak.recce.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.nujiak.recce.*

class OnboardingTitleFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val viewModel: OnboardingViewModel by activityViewModels()
        val view = inflater.inflate(R.layout.onboarding_title, container)

        view.findViewById<Button>(R.id.onboarding_title_next).setOnClickListener {
            viewModel.toPage(OnboardingViewModel.COORD_SYS_INDEX)
        }

        view.findViewById<Button>(R.id.onboarding_title_ns).setOnClickListener {
            viewModel.coordSysId = COORD_SYS_ID_KERTAU
            viewModel.angleUnitId = ANGLE_UNIT_ID_NATO_MILS
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

        val viewModel: OnboardingViewModel by activityViewModels()

        val view = inflater.inflate(R.layout.onboarding_grids, container)

        val utmBtn = view.findViewById<RadioButton>(R.id.onboarding_radio_utm)
        val mgrsBtn = view.findViewById<RadioButton>(R.id.onboarding_radio_mgrs)
        val kertauBtn = view.findViewById<RadioButton>(R.id.onboarding_radio_kertau)
        val latlngBtn = view.findViewById<RadioButton>(R.id.onboarding_radio_latlng)

        when (viewModel.coordSysId) {
            COORD_SYS_ID_UTM -> utmBtn.isChecked = true
            COORD_SYS_ID_MGRS -> mgrsBtn.isChecked = true
            COORD_SYS_ID_KERTAU -> kertauBtn.isChecked = true
            COORD_SYS_ID_LATLNG -> latlngBtn.isChecked = true
        }

        view.findViewById<Button>(R.id.onboarding_grids_next).setOnClickListener {
            viewModel.coordSysId = when {
                utmBtn.isChecked -> COORD_SYS_ID_UTM
                mgrsBtn.isChecked -> COORD_SYS_ID_MGRS
                kertauBtn.isChecked -> COORD_SYS_ID_KERTAU
                latlngBtn.isChecked -> COORD_SYS_ID_LATLNG
                else -> COORD_SYS_ID_UTM
            }
            viewModel.toPage(OnboardingViewModel.ANGLE_INDEX)
        }
        view.findViewById<Button>(R.id.onboarding_grids_prev).setOnClickListener {
            viewModel.coordSysId = when {
                utmBtn.isChecked -> COORD_SYS_ID_UTM
                mgrsBtn.isChecked -> COORD_SYS_ID_MGRS
                kertauBtn.isChecked -> COORD_SYS_ID_KERTAU
                latlngBtn.isChecked -> COORD_SYS_ID_LATLNG
                else -> COORD_SYS_ID_UTM
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
        val viewModel: OnboardingViewModel by activityViewModels()

        val view = inflater.inflate(R.layout.onboarding_angles, container)

        val degBtn = view.findViewById<RadioButton>(R.id.onboarding_radio_deg)
        val milsBtn = view.findViewById<RadioButton>(R.id.onboarding_radio_mils)

        when (viewModel.angleUnitId) {
            ANGLE_UNIT_ID_DEG -> degBtn.isChecked = true
            ANGLE_UNIT_ID_NATO_MILS -> milsBtn.isChecked = true
        }

        view.findViewById<Button>(R.id.onboarding_angles_next).setOnClickListener {
            viewModel.angleUnitId = when {
                degBtn.isChecked -> ANGLE_UNIT_ID_DEG
                milsBtn.isChecked -> ANGLE_UNIT_ID_NATO_MILS
                else -> ANGLE_UNIT_ID_DEG
            }
            viewModel.toPage(OnboardingViewModel.ALL_SET_INDEX)
        }
        view.findViewById<Button>(R.id.onboarding_angles_prev).setOnClickListener {
            viewModel.angleUnitId = when {
                degBtn.isChecked -> ANGLE_UNIT_ID_DEG
                milsBtn.isChecked -> ANGLE_UNIT_ID_NATO_MILS
                else -> ANGLE_UNIT_ID_DEG
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
        val viewModel: OnboardingViewModel by activityViewModels()

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
        val viewModel: OnboardingViewModel by activityViewModels()

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