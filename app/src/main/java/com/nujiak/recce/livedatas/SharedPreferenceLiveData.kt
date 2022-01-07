package com.nujiak.recce.livedatas

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import com.nujiak.recce.enums.SharedPrefsKey

/**
 * Updates listeners when an Int value shared preference changes
 *
 * @property sharedPreferences
 * @property sharedPrefsKey
 */
class SharedPreferenceLiveData(private val sharedPreferences: SharedPreferences, private val sharedPrefsKey: SharedPrefsKey) : LiveData<Int>(), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onActive() {
        super.onActive()
        value = this.sharedPreferences.getInt(this.sharedPrefsKey.key, 0)
        this.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key != this.sharedPrefsKey.key) {
            return
        }
        val currentValue = this.sharedPreferences.getInt(key, 0)
        if (currentValue != this.value) {
            this.value = currentValue
        }
    }

    override fun onInactive() {
        super.onInactive()
        this.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }
}
