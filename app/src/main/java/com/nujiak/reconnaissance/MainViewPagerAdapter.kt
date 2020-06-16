package com.nujiak.reconnaissance

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.nujiak.reconnaissance.fragments.GpsFragment
import com.nujiak.reconnaissance.fragments.MapFragment
import com.nujiak.reconnaissance.fragments.ruler.RulerFragment
import com.nujiak.reconnaissance.fragments.selector.PinSelectorFragment

class MainViewPagerAdapter(activity: AppCompatActivity) :
    FragmentStateAdapter(activity) {

    private val mapFragment = MapFragment()

    override fun getItemCount() = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> mapFragment
            1 -> PinSelectorFragment()
            2 -> GpsFragment()
            else -> RulerFragment()
        }
    }
}