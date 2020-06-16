package com.nujiak.reconnaissance.fragments.selector

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.nujiak.reconnaissance.MainViewModel
import com.nujiak.reconnaissance.MainViewModelFactory
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.database.Pin
import com.nujiak.reconnaissance.database.PinDatabase
import com.nujiak.reconnaissance.databinding.FragmentPinSelectorBinding
import com.nujiak.reconnaissance.modalsheets.SettingsSheet


class PinSelectorFragment : Fragment() {

    private lateinit var binding: FragmentPinSelectorBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var pinAdapter: PinAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentPinSelectorBinding.inflate(inflater, container, false)

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

        // Set up RecyclerView adapter
        pinAdapter = PinAdapter(
            { pin -> onPinClick(pin) },
            { pin -> onPinLongClick(pin) },
            viewModel.coordinateSystem.value ?: 0
        )

        // Observe for changes to pins
        viewModel.allPins.observe(viewLifecycleOwner, Observer {
            generateAndSubmitList(it)
        })

        // Observe for changes to preferred GCS
        viewModel.coordinateSystem.observe(viewLifecycleOwner, Observer {
            pinAdapter.updateCoordSys(it)
            pinAdapter.notifyDataSetChanged()
        })

        // Observe for changes to action/selection mode
        viewModel.isInSelectionMode.observe(viewLifecycleOwner, Observer {
            generateAndSubmitList()
        })
        binding.pinRecyclerview.adapter = pinAdapter
        binding.pinRecyclerview.layoutManager = GridLayoutManager(context, 2)

        // Set up FAB
        binding.pinFab.setOnClickListener { viewModel.openPinCreator(null) }

        // Set up app bar
        binding.pinAppBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.settings -> {
                    openSettings()
                    true
                }
                else -> false
            }
        }

        // Set up Selection Mode changes
        viewModel.isInSelectionMode.observe(viewLifecycleOwner, Observer { isInSelectionMode ->
            if (isInSelectionMode) {
                context?.let {
                    binding.pinFab.hide()
                }
            } else {
                context?.let {
                    binding.pinFab.show()
                }
            }
        })

        return binding.root
    }

    private fun onPinClick(pin: Pin) {
        if (viewModel.isInSelectionMode.value!!) {
            viewModel.toggleSelection(pin.pinId)
            generateAndSubmitList()
        } else {
            viewModel.showPinOnMap(pin)
        }
    }

    private fun onPinLongClick(pin: Pin): Boolean {
        if (!viewModel.isInSelectionMode.value!!) {
            viewModel.enterSelectionMode()
            viewModel.toggleSelection(pin.pinId)
            generateAndSubmitList()
        }
        return true
    }

    private fun openSettings() {
        val settingsSheet = SettingsSheet()
        settingsSheet.show(parentFragmentManager, settingsSheet.tag)
    }

    private fun generateAndSubmitList(newList: List<Pin>? = viewModel.allPins.value) {
        val newWrapperList = viewModel.generateWrapperList(newList)
        pinAdapter.submitList(newWrapperList)
    }
}
