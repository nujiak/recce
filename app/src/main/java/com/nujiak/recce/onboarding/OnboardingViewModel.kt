package com.nujiak.recce.onboarding

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nujiak.recce.enums.AngleUnit
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.enums.SharedPrefsKey

class OnboardingViewModel : ViewModel() {

    companion object {
    }

    lateinit var sharedPreference: SharedPreferences

    private val _changePage = MutableLiveData(OnboardingPage.TITLE)
    val changePage: LiveData<OnboardingPage>
        get() = _changePage

    fun toPage(page: OnboardingPage) {
        _changePage.value = page
    }

    var coordSysId: CoordinateSystem
        get() {
            val index = sharedPreference.getInt(SharedPrefsKey.COORDINATE_SYSTEM.key, CoordinateSystem.UTM.index)
            return CoordinateSystem.atIndex(index)
        }
        set(id) = sharedPreference.edit().putInt(SharedPrefsKey.COORDINATE_SYSTEM.key, id.index).apply()

    var angleUnitId: AngleUnit
        get() {
            val index = sharedPreference.getInt(SharedPrefsKey.ANGLE_UNIT.key, AngleUnit.DEGREE.index)
            return AngleUnit.atIndex(index)
        }
        set(id) = sharedPreference.edit().putInt(SharedPrefsKey.ANGLE_UNIT.key, id.index).apply()

    private val _endOnboarding = MutableLiveData(false)
    val endOnboarding: LiveData<Boolean>
        get() = _endOnboarding

    fun endOnboarding() {
        sharedPreference.edit().putBoolean(SharedPrefsKey.ONBOARDING_COMPLETED.key, true)
            .apply()
        _endOnboarding.value = true
    }
}