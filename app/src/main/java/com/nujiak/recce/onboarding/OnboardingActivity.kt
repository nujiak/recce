package com.nujiak.recce.onboarding

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.nujiak.recce.MainActivity
import com.nujiak.recce.R
import com.nujiak.recce.databinding.ActivityOnboardingBinding
import com.nujiak.recce.preference.PreferenceFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewpager: ViewPager2
    private lateinit var binding: ActivityOnboardingBinding

    val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewpager = binding.onboardingViewpager
        viewpager.adapter = OnboardingViewPagerAdapter(this@OnboardingActivity)
        viewpager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewModel.gotoPage(position)
            }

            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)

                // Animate back button
                if (position == 0) {
                    binding.onboardingBack.layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        positionOffset
                    )
                    binding.onboardingButtonsDivider.alpha = positionOffset
                }
            }
        })

        viewModel.currentPage.observe(this) { page ->
            viewpager.currentItem = page

            binding.onboardingNext.text = if (page == 2) {
                getString(R.string.start)
            } else {
                getString(R.string.next)
            }
        }

        viewModel.endOnboarding.observe(this, { endOnboarding ->
            if (endOnboarding) {
                onCompleted()
            }
        })

        binding.onboardingBack.setOnClickListener {
            viewModel.previousPage()
        }

        binding.onboardingNext.setOnClickListener {
            viewModel.nextPage()
        }
    }

    private inner class OnboardingViewPagerAdapter(activity: AppCompatActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount() = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> OnboardingFragment(R.layout.onboarding_title)
                1 -> PreferenceFragment()
                2 -> OnboardingFragment(R.layout.onboarding_end)
                else -> throw IllegalStateException("Invalid onboarding page: $position")
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
