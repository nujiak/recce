package com.nujiak.reconnaissance.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.nujiak.reconnaissance.MainActivity
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.fragments.ruler.RulerFragment

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewpager: ViewPager2

    companion object {
        const val ONBOARDING_COMPLETED_KEY = "onboarding_complete"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        val viewModel: OnboardingViewModel by viewModels()
        viewModel.sharedPreference =
            getSharedPreferences("com.nujiak.reconnaissance", Context.MODE_PRIVATE)

        viewpager = findViewById(R.id.onboarding_viewpager)
        viewpager.apply {
            adapter = OnboardingViewPagerAdapter(this@OnboardingActivity)
            isUserInputEnabled = false
        }

        Log.i(this::class.simpleName, "Setting sharedPreferences")


        Log.i(this::class.simpleName, "Start observe")
        viewModel.changePage.observe(this, {
            Log.i(this::class.simpleName, "Changing page...")
            viewpager.currentItem = it
        })

        viewModel.endOnboarding.observe(this, { endOnboarding ->
            if(endOnboarding) {
                onCompleted()
            }
        })
    }

    private inner class OnboardingViewPagerAdapter(activity: AppCompatActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount() = 5

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> OnboardingTitleFragment()
                1 -> OnboardingGridsFragment()
                2 -> OnboardingAnglesFragment()
                3 -> OnboardingEndFragment()
                4 -> OnboardingNSFragment()
                else -> RulerFragment()
            }
        }
    }

    private fun onCompleted() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onBackPressed() {
        if (viewpager.currentItem != 0) {
            viewpager.currentItem = viewpager.currentItem - 1
        } else {
            finish()
        }
    }
}