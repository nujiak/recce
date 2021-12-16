package com.nujiak.recce.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.nujiak.recce.R
import com.nujiak.recce.enums.AngleUnit
import com.nujiak.recce.enums.CoordinateSystem

class OnboardingTitleFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val viewModel: OnboardingViewModel by activityViewModels()
        val view = inflater.inflate(R.layout.onboarding_title, container)

        view.findViewById<Button>(R.id.onboarding_title_next).setOnClickListener {
            viewModel.toPage(OnboardingPage.COORD_SYS)
        }

        view.findViewById<Button>(R.id.onboarding_title_ns).setOnClickListener {
            viewModel.coordSysId = CoordinateSystem.KERTAU
            viewModel.angleUnitId = AngleUnit.NATO_MIL
            viewModel.toPage(OnboardingPage.NS_ALL_SET)
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
            CoordinateSystem.UTM -> utmBtn.isChecked = true
            CoordinateSystem.MGRS -> mgrsBtn.isChecked = true
            CoordinateSystem.KERTAU -> kertauBtn.isChecked = true
            CoordinateSystem.WGS84 -> latlngBtn.isChecked = true
        }

        view.findViewById<Button>(R.id.onboarding_grids_next).setOnClickListener {
            viewModel.coordSysId = when {
                utmBtn.isChecked -> CoordinateSystem.UTM
                mgrsBtn.isChecked -> CoordinateSystem.MGRS
                kertauBtn.isChecked -> CoordinateSystem.KERTAU
                latlngBtn.isChecked -> CoordinateSystem.WGS84
                else -> CoordinateSystem.UTM
            }
            viewModel.toPage(OnboardingPage.ANGLE)
        }
        view.findViewById<Button>(R.id.onboarding_grids_prev).setOnClickListener {
            viewModel.coordSysId = when {
                utmBtn.isChecked -> CoordinateSystem.UTM
                mgrsBtn.isChecked -> CoordinateSystem.MGRS
                kertauBtn.isChecked -> CoordinateSystem.KERTAU
                latlngBtn.isChecked -> CoordinateSystem.WGS84
                else -> CoordinateSystem.UTM
            }
            viewModel.toPage(OnboardingPage.TITLE)
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
            AngleUnit.DEGREE -> degBtn.isChecked = true
            AngleUnit.NATO_MIL -> milsBtn.isChecked = true
        }

        view.findViewById<Button>(R.id.onboarding_angles_next).setOnClickListener {
            viewModel.angleUnitId = when {
                degBtn.isChecked -> AngleUnit.DEGREE
                milsBtn.isChecked -> AngleUnit.NATO_MIL
                else -> AngleUnit.DEGREE
            }
            viewModel.toPage(OnboardingPage.ALL_SET)
        }
        view.findViewById<Button>(R.id.onboarding_angles_prev).setOnClickListener {
            viewModel.angleUnitId = when {
                degBtn.isChecked -> AngleUnit.DEGREE
                milsBtn.isChecked -> AngleUnit.NATO_MIL
                else -> AngleUnit.DEGREE
            }
            viewModel.toPage(OnboardingPage.COORD_SYS)
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
            viewModel.toPage(OnboardingPage.ANGLE)
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
            viewModel.toPage(OnboardingPage.TITLE)
        }

        return view
    }
}
