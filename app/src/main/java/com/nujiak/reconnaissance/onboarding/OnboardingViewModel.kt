package com.nujiak.reconnaissance.onboarding

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nujiak.reconnaissance.modalsheets.SettingsSheet
import com.nujiak.reconnaissance.modalsheets.SettingsSheet.Companion.ANGLE_UNIT_KEY
import com.nujiak.reconnaissance.modalsheets.SettingsSheet.Companion.COORD_SYS_KEY

class OnboardingViewModel : ViewModel() {

    companion object {
        const val TITLE_INDEX = 0
        const val COORD_SYS_INDEX = 1
        const val ANGLE_INDEX = 2
        const val ALL_SET_INDEX = 3
        const val NS_ALL_SET_INDEX = 4
    }

    lateinit var sharedPreference: SharedPreferences

    private val _changePage = MutableLiveData(TITLE_INDEX)
    val changePage: LiveData<Int>
        get() = _changePage
    fun toPage(page: Int) {
        _changePage.value = page
    }
    
    var coordSysId: Int
        get() = sharedPreference.getInt(COORD_SYS_KEY, SettingsSheet.COORD_SYS_ID_UTM)
        set(id) = sharedPreference.edit().putInt(COORD_SYS_KEY, id).apply()
    
    var angleUnitId: Int
        get() = sharedPreference.getInt(ANGLE_UNIT_KEY, SettingsSheet.ANGLE_UNIT_ID_DEG)
        set(id) = sharedPreference.edit().putInt(ANGLE_UNIT_KEY, id).apply()

    private val _endOnboarding = MutableLiveData(false)
    val endOnboarding: LiveData<Boolean>
        get() = _endOnboarding
    fun endOnboarding() {
        sharedPreference.edit().putBoolean(OnboardingActivity.ONBOARDING_COMPLETED_KEY, true).apply()
        _endOnboarding.value = true
    }
}