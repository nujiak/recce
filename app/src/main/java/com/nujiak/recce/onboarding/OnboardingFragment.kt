package com.nujiak.recce.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment

class OnboardingFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val bundle = this.arguments
        val layout = bundle!!.getInt(BUNDLE_LAYOUT_KEY)
        return inflater.inflate(layout, container)
    }

    companion object {

        private const val BUNDLE_LAYOUT_KEY = "layout"

        fun newInstance(@LayoutRes layout: Int) : OnboardingFragment {
            val bundle = Bundle()
            bundle.putInt(BUNDLE_LAYOUT_KEY, layout)
            val fragment = OnboardingFragment()
            fragment.arguments = bundle
            return fragment
        }
    }
}
