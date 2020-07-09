package com.nujiak.reconnaissance.onboarding

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.modalsheets.SettingsSheet

class OnboardingTitleFragment(private val sharedPrefs: SharedPreferences, private val viewpager: ViewPager2): Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.onboarding_title, container)

        view.findViewById<Button>(R.id.onboarding_title_next).setOnClickListener {
            viewpager.currentItem += 1
        }
        view.findViewById<Button>(R.id.onboarding_title_ns).setOnClickListener {
            sharedPrefs.edit().apply {
                putInt(SettingsSheet.COORD_SYS_KEY, SettingsSheet.COORD_SYS_ID_KERTAU)
                putInt(SettingsSheet.ANGLE_UNIT_KEY, SettingsSheet.ANGLE_UNIT_ID_NATO_MILS)
            }.apply()
            viewpager.currentItem = 4
        }

        return view
    }
}

class OnboardingGridsFragment(private val sharedPrefs: SharedPreferences, private val viewpager: ViewPager2): Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.onboarding_grids, container)

        view.findViewById<Button>(R.id.onboarding_grids_next).setOnClickListener {
            viewpager.currentItem += 1
        }
        view.findViewById<Button>(R.id.onboarding_grids_prev).setOnClickListener {
            viewpager.currentItem -= 1
        }

        when (sharedPrefs.getInt(SettingsSheet.COORD_SYS_KEY, SettingsSheet.COORD_SYS_ID_UTM)) {
            SettingsSheet.COORD_SYS_ID_UTM ->
                view.findViewById<RadioButton>(R.id.onboarding_radio_utm).isChecked = true
            SettingsSheet.COORD_SYS_ID_MGRS ->
                view.findViewById<RadioButton>(R.id.onboarding_radio_mgrs).isChecked = true
            SettingsSheet.COORD_SYS_ID_KERTAU ->
                view.findViewById<RadioButton>(R.id.onboarding_radio_kertau).isChecked = true
        }

        val radioGroup = view.findViewById<RadioGroup>(R.id.onboarding_grids_radio_group)
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val prefValue = when (checkedId) {
                R.id.onboarding_radio_utm -> SettingsSheet.COORD_SYS_ID_UTM
                R.id.onboarding_radio_mgrs -> SettingsSheet.COORD_SYS_ID_MGRS
                R.id.onboarding_radio_kertau -> SettingsSheet.COORD_SYS_ID_KERTAU
                else -> SettingsSheet.COORD_SYS_ID_UTM
            }
            sharedPrefs.edit().putInt(SettingsSheet.COORD_SYS_KEY, prefValue).apply()
        }

        return view
    }
}

class OnboardingAnglesFragment(private val sharedPrefs: SharedPreferences, private val viewpager: ViewPager2): Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.onboarding_angles, container)

        view.findViewById<Button>(R.id.onboarding_angles_next).setOnClickListener {
            viewpager.currentItem += 1
        }
        view.findViewById<Button>(R.id.onboarding_angles_prev).setOnClickListener {
            viewpager.currentItem -= 1
        }

        when (sharedPrefs.getInt(SettingsSheet.ANGLE_UNIT_KEY, SettingsSheet.ANGLE_UNIT_ID_DEG)) {
            SettingsSheet.ANGLE_UNIT_ID_DEG ->
                view.findViewById<RadioButton>(R.id.onboarding_radio_deg).isChecked = true
            SettingsSheet.ANGLE_UNIT_ID_NATO_MILS ->
                view.findViewById<RadioButton>(R.id.onboarding_radio_mils).isChecked = true
        }

        val radioGroup = view.findViewById<RadioGroup>(R.id.onboarding_angles_radio_group)
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val prefValue = when (checkedId) {
                R.id.onboarding_radio_deg -> SettingsSheet.ANGLE_UNIT_ID_DEG
                R.id.onboarding_radio_mils -> SettingsSheet.ANGLE_UNIT_ID_NATO_MILS
                else -> SettingsSheet.ANGLE_UNIT_ID_DEG
            }
            sharedPrefs.edit().putInt(SettingsSheet.ANGLE_UNIT_KEY, prefValue).apply()
        }

        return view
    }
}

class OnboardingEndFragment(private val viewpager: ViewPager2, val endOnClick: (View) -> Unit): Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.onboarding_end, container)

        view.findViewById<Button>(R.id.onboarding_end_start).setOnClickListener {
            endOnClick(it)
        }
        view.findViewById<Button>(R.id.onboarding_end_back).setOnClickListener {
            viewpager.currentItem -= 1
        }

        return view
    }
}

class OnboardingNSFragment(private val viewpager: ViewPager2, val endOnClick: (View) -> Unit): Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.onboarding_ns, container)

        view.findViewById<Button>(R.id.onboarding_ns_start).setOnClickListener {
            endOnClick(it)
        }
        view.findViewById<Button>(R.id.onboarding_ns_back).setOnClickListener {
            viewpager.currentItem = 0
        }

        return view
    }
}