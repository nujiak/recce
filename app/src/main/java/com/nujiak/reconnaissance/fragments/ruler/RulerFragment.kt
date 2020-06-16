package com.nujiak.reconnaissance.fragments.ruler

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.nujiak.reconnaissance.MainViewModel
import com.nujiak.reconnaissance.MainViewModelFactory
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.database.PinDatabase
import com.nujiak.reconnaissance.databinding.FragmentRulerBinding

/**
 * A simple [Fragment] subclass.
 */
class RulerFragment : Fragment() {

    lateinit var binding: FragmentRulerBinding
    lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Set up view binding
        binding = FragmentRulerBinding.inflate(inflater, container, false)


        // Set up ViewModel
        val application = requireNotNull(this.activity).application
        val dataSource = PinDatabase.getInstance(application).pinDatabaseDao
        val viewModelFactory = MainViewModelFactory(dataSource, application)
        viewModel = activity?.let {
            ViewModelProvider(
                it,
                viewModelFactory
            ).get(MainViewModel::class.java)
        }!!

        // Set up RecyclerView
        val rulerAdapter = RulerAdapter()
        binding.rulerList.adapter = rulerAdapter
        viewModel.rulerList.observe(viewLifecycleOwner, Observer {
            rulerAdapter.submitList(it)

            // Scroll to bottom of ruler
            binding.rulerList.smoothScrollToPosition(it.size)
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
