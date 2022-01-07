package com.nujiak.recce

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.nujiak.recce.fragments.GpsFragment
import com.nujiak.recce.fragments.MapFragment
import com.nujiak.recce.fragments.ruler.RulerFragment
import com.nujiak.recce.fragments.saved.SavedFragment

class MainViewPagerAdapter(activity: AppCompatActivity) :
    FragmentStateAdapter(activity) {

    private val mapFragment = MapFragment()

    override fun getItemCount() = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> mapFragment
            1 -> SavedFragment()
            2 -> GpsFragment()
            else -> RulerFragment()
        }
    }
}
