package com.nujiak.reconnaissance.fragments.selector

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.nujiak.reconnaissance.MainViewModel
import com.nujiak.reconnaissance.MainViewModelFactory
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.database.Chain
import com.nujiak.reconnaissance.database.Pin
import com.nujiak.reconnaissance.database.ReconDatabase
import com.nujiak.reconnaissance.databinding.FragmentPinSelectorBinding
import com.nujiak.reconnaissance.fragments.selector.PinAdapter.Companion.ITEM_VIEW_TYPE_CHAIN
import com.nujiak.reconnaissance.fragments.selector.PinAdapter.Companion.ITEM_VIEW_TYPE_HEADER
import com.nujiak.reconnaissance.fragments.selector.PinAdapter.Companion.ITEM_VIEW_TYPE_PIN
import com.nujiak.reconnaissance.fragments.selector.PinAdapter.Companion.SORT_BY_GROUP
import com.nujiak.reconnaissance.fragments.selector.PinAdapter.Companion.SORT_BY_NAME
import com.nujiak.reconnaissance.fragments.selector.PinAdapter.Companion.SORT_BY_TIME
import com.nujiak.reconnaissance.modalsheets.SettingsSheet


class PinSelectorFragment : Fragment() {

    private lateinit var binding: FragmentPinSelectorBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var pinAdapter: PinAdapter

    private var sortBy = SORT_BY_GROUP
    private var sortAscending = false

    companion object {
        private const val SORT_ASCENDING_KEY = "sort_ascending"
        private const val SORT_BY_KEY = "sort_by"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentPinSelectorBinding.inflate(inflater, container, false)

        // Set up ViewModel
        val application = requireNotNull(this.activity).application
        val dataSource = ReconDatabase.getInstance(application).pinDatabaseDao
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
            { chain -> onChainClick(chain)},
            { chain -> onChainLongClick(chain)},
            viewModel.coordinateSystem.value ?: 0,
            resources
        )
        binding.pinRecyclerview.adapter = pinAdapter
        val gridLayoutManager = GridLayoutManager(context, 2)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (pinAdapter.getItemViewType(position)) {
                    ITEM_VIEW_TYPE_PIN -> 1
                    ITEM_VIEW_TYPE_CHAIN -> 2
                    ITEM_VIEW_TYPE_HEADER -> 2
                    else -> throw IllegalArgumentException(
                        "Invalid viewType: ${pinAdapter.getItemViewType(
                            position
                        )}"
                    )
                }
            }

        }
        binding.pinRecyclerview.layoutManager = gridLayoutManager


        // Observe for changes to pins and chains
        viewModel.allPins.observe(viewLifecycleOwner, Observer {
            refreshList(newPins = it)
        })
        viewModel.allChains.observe(viewLifecycleOwner, Observer {
            refreshList(newChains = it)
        })

        // Observe for changes to preferred GCS
        viewModel.coordinateSystem.observe(viewLifecycleOwner, Observer {
            pinAdapter.updateCoordSys(it)
            pinAdapter.notifyDataSetChanged()
        })

        // Observe for changes to action/selection mode
        viewModel.isInSelectionMode.observe(viewLifecycleOwner, Observer {
            refreshList()
        })

        // Observe for recent multiple deletions made through action mode
        viewModel.lastMultiDeletedItems.observe(viewLifecycleOwner, Observer {
            if (it != null) {
                val size = (it.first?.size ?: 0) + (it.second?.size ?: 0)
                val snackBar = Snackbar.make(
                    binding.pinAppBar,
                    resources.getQuantityString(R.plurals.number_deleted, size, size),
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.undo) { viewModel.onRestoreLastDeletedPins() }
                snackBar.show()
            }
        })

        // Fetch sorting parameters
        sortBy = viewModel.sharedPreference.getInt(SORT_BY_KEY, sortBy)
        sortAscending = viewModel.sharedPreference.getBoolean(SORT_ASCENDING_KEY, sortAscending)

        // Set up FAB
        binding.pinFab.setOnClickListener { viewModel.openPinCreator(null) }

        // Set up app bar
        binding.pinAppBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.settings -> {
                    openSettings()
                    true
                }
                R.id.sort_by_alphabetical_asc -> {
                    sortBy = SORT_BY_NAME
                    sortAscending = true
                    onSortList()
                    true
                }
                R.id.sort_by_alphabetical_dsc -> {
                    sortBy = SORT_BY_NAME
                    sortAscending = false
                    onSortList()
                    true
                }
                R.id.sort_by_time_asc -> {
                    sortBy = SORT_BY_TIME
                    sortAscending = true
                    onSortList()
                    true
                }
                R.id.sort_by_time_dsc -> {
                    sortBy = SORT_BY_TIME
                    sortAscending = false
                    onSortList()
                    true
                }
                R.id.sort_by_group -> {
                    sortBy = SORT_BY_GROUP
                    onSortList()
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
            viewModel.togglePinSelection(pin.pinId)
            refreshList()
        } else {
            viewModel.showPinOnMap(pin)
        }
    }

    private fun onPinLongClick(pin: Pin): Boolean {
        if (!viewModel.isInSelectionMode.value!!) {
            viewModel.enterSelectionMode()
            viewModel.togglePinSelection(pin.pinId)
            refreshList()
        }
        return true
    }

    private fun onChainClick(chain: Chain) {
        if (viewModel.isInSelectionMode.value!!) {
            viewModel.toggleChainSelection(chain.chainId)
            refreshList()
        } else {
            viewModel.showChainOnMap(chain)
        }
    }

    private fun onChainLongClick(chain: Chain): Boolean {
        if (!viewModel.isInSelectionMode.value!!) {
            viewModel.enterSelectionMode()
            viewModel.toggleChainSelection(chain.chainId)
            refreshList()
        }
        return true
    }

    private fun openSettings() {
        val settingsSheet = SettingsSheet()
        settingsSheet.show(parentFragmentManager, settingsSheet.tag)
    }

    private fun onSortList() {
        viewModel.sharedPreference.edit()
            .putInt(SORT_BY_KEY, sortBy)
            .putBoolean(SORT_ASCENDING_KEY, sortAscending)
            .apply()
        refreshList()
    }

    private fun refreshList(newPins: List<Pin>? = viewModel.allPins.value, newChains: List<Chain>? = viewModel.allChains.value) {
        pinAdapter.sortAndSubmitList(newPins, newChains, viewModel.selectedPinIds, viewModel.selectedChainIds, sortBy, sortAscending)
    }
}
