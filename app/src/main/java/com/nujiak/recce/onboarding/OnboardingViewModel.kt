package com.nujiak.recce.onboarding

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nujiak.recce.enums.SharedPrefsKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val sharedPreference: SharedPreferences) : ViewModel() {
    private val _endOnboarding = MutableLiveData(false)
    val endOnboarding: LiveData<Boolean>
        get() = _endOnboarding

    private fun endOnboarding() {
        sharedPreference.edit().putBoolean(SharedPrefsKey.ONBOARDING_COMPLETED.key, true)
            .apply()
        _endOnboarding.value = true
    }

    private val _currentPage = MutableLiveData(0)
    val currentPage: LiveData<Int>
        get() = _currentPage

    fun nextPage() {
        when (val page = _currentPage.value) {
            null -> _currentPage.value = 1
            2 -> endOnboarding()
            else -> _currentPage.value = page + 1
        }
    }

    fun previousPage() {
        when (val page = _currentPage.value) {
            null -> _currentPage.value = 0
            0 -> return
            else -> _currentPage.value = page - 1
        }
    }

    fun gotoPage(page: Int) {
        if (page < 0 || page > 2) {
            return
        }
        if (page != _currentPage.value) {
            _currentPage.value = page
        }
    }
}
