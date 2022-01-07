package com.nujiak.recce

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.fragment.app.commit
import androidx.preference.PreferenceManager
import com.nujiak.recce.databinding.ActivityPreferenceBinding
import com.nujiak.recce.enums.SharedPrefsKey
import com.nujiak.recce.enums.ThemePreference
import com.nujiak.recce.livedatas.SharedPreferenceLiveData
import com.nujiak.recce.preference.PreferenceFragment

class PreferenceActivity() : AppCompatActivity() {
    private lateinit var binding: ActivityPreferenceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPreferenceBinding.inflate(layoutInflater)

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.preference_fragment_container, PreferenceFragment())
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
        SharedPreferenceLiveData(sharedPreferences, SharedPrefsKey.THEME_PREF).observe(this) {
            setDefaultNightMode(ThemePreference.atIndex(it).mode)
        }

        setContentView(binding.root)
    }
}
