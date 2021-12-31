package com.nujiak.recce

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.nujiak.recce.databinding.ActivityPreferenceBinding
import com.nujiak.recce.preference.PreferenceFragment

class PreferenceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPreferenceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPreferenceBinding.inflate(layoutInflater)

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.preference_fragment_container, PreferenceFragment())
        }

        setContentView(binding.root)
    }
}
