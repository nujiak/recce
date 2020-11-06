package com.nujiak.reconnaissance.fragments.ruler

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.nujiak.reconnaissance.MainViewModel
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.databinding.FragmentRulerBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RulerFragment : Fragment() {

    private val viewModel : MainViewModel by activityViewModels()
    lateinit var binding: FragmentRulerBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Set up view binding
        binding = FragmentRulerBinding.inflate(inflater, container, false)

        // Set up RecyclerView
        val rulerAdapter = RulerAdapter(viewModel.coordinateSystem.value ?: 0,
            viewModel.angleUnit.value ?: 0)
        binding.rulerList.adapter = rulerAdapter
        viewModel.rulerList.observe(viewLifecycleOwner, {
            rulerAdapter.submitList(it)

            // Scroll to bottom of ruler
            binding.rulerList.smoothScrollToPosition(it.size)
        })

        // Observe for changes to preferences
        viewModel.coordinateSystem.observe(viewLifecycleOwner, {
            rulerAdapter.updateCoordSys(it)
        })
        viewModel.angleUnit.observe(viewLifecycleOwner, {
            rulerAdapter.updateAngleUnit(it)
        })

        binding.rulerTopAppBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.ruler_clear -> viewModel.clearRuler()
            }
            true
        }

        return binding.root
    }
}
