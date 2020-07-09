package com.nujiak.reconnaissance.onboarding

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.nujiak.reconnaissance.MainActivity
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.fragments.ruler.RulerFragment

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewpager: ViewPager2
    private lateinit var sharedPrefs: SharedPreferences

    companion object {
        const val ONBOARDING_COMPLETED_KEY = "onboarding_complete"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewpager = findViewById(R.id.onboarding_viewpager)
        viewpager.apply {
            adapter = OnboardingViewPagerAdapter(this@OnboardingActivity)
            isUserInputEnabled = false
        }

        sharedPrefs = getSharedPreferences("com.nujiak.reconnaissance", Context.MODE_PRIVATE)
    }

    private inner class OnboardingViewPagerAdapter(activity: AppCompatActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount() = 5

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> OnboardingTitleFragment(sharedPrefs, viewpager)
                1 -> OnboardingGridsFragment(sharedPrefs, viewpager)
                2 -> OnboardingAnglesFragment(sharedPrefs, viewpager)
                3 -> OnboardingEndFragment(viewpager) {
                    sharedPrefs.edit().putBoolean(ONBOARDING_COMPLETED_KEY, true).apply()
                    startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
                    finish()
                }
                4 -> OnboardingNSFragment(viewpager) {
                    sharedPrefs.edit().putBoolean(ONBOARDING_COMPLETED_KEY, true).apply()
                    startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
                    finish()
                }
                else -> RulerFragment()
            }
        }
    }

    override fun onBackPressed() {
        if (viewpager.currentItem != 0) {
            viewpager.currentItem = viewpager.currentItem - 1
        } else {
            finish()
        }
    }
}