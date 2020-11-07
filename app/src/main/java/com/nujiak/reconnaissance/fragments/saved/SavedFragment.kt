package com.nujiak.reconnaissance.fragments.saved

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nujiak.reconnaissance.MainViewModel
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.database.Chain
import com.nujiak.reconnaissance.database.Pin
import com.nujiak.reconnaissance.databinding.FragmentSavedBinding
import com.nujiak.reconnaissance.fragments.saved.PinAdapter.Companion.ITEM_VIEW_TYPE_CHAIN
import com.nujiak.reconnaissance.fragments.saved.PinAdapter.Companion.ITEM_VIEW_TYPE_HEADER
import com.nujiak.reconnaissance.fragments.saved.PinAdapter.Companion.ITEM_VIEW_TYPE_PIN
import com.nujiak.reconnaissance.fragments.saved.PinAdapter.Companion.SORT_BY_GROUP
import com.nujiak.reconnaissance.fragments.saved.PinAdapter.Companion.SORT_BY_NAME
import com.nujiak.reconnaissance.fragments.saved.PinAdapter.Companion.SORT_BY_TIME
import dagger.hilt.android.AndroidEntryPoint
import io.github.yavski.fabspeeddial.SimpleMenuListenerAdapter

@AndroidEntryPoint
class SavedFragment : Fragment() {

    private val viewModel : MainViewModel by activityViewModels()
    private lateinit var binding: FragmentSavedBinding
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
        binding = FragmentSavedBinding.inflate(inflater, container, false)

        // Set up RecyclerView adapter
        pinAdapter = PinAdapter(
            { pin -> onPinClick(pin) },
            { pin -> onPinLongClick(pin) },
            { chain -> onChainClick(chain) },
            { chain -> onChainLongClick(chain) },
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
                        "Invalid viewType: ${
                            pinAdapter.getItemViewType(
                                position
                            )
                        }"
                    )
                }
            }

        }
        binding.pinRecyclerview.layoutManager = gridLayoutManager


        // Observe for changes to pins and chains
        viewModel.allPins.observe(viewLifecycleOwner, { allPins ->
            refreshList(newPins = allPins)
            viewModel.allChains.value?.let { allChains ->
                if (allChains.isEmpty() && allPins.isEmpty()) {
                    binding.pinRecyclerview.visibility = View.GONE
                    binding.pinEmptyView.visibility = View.VISIBLE
                } else {
                    binding.pinRecyclerview.visibility = View.VISIBLE
                    binding.pinEmptyView.visibility = View.GONE
                }
            }
        })
        viewModel.allChains.observe(viewLifecycleOwner, { allChains ->
            refreshList(newChains = allChains)
            viewModel.allPins.value?.let { allPins ->
                if (allChains.isEmpty() && allPins.isEmpty()) {
                    binding.pinRecyclerview.visibility = View.GONE
                    binding.pinEmptyView.visibility = View.VISIBLE
                } else {
                    binding.pinRecyclerview.visibility = View.VISIBLE
                    binding.pinEmptyView.visibility = View.GONE
                }
            }
        })

        // Observe for changes to preferred GCS
        viewModel.coordinateSystem.observe(viewLifecycleOwner, {
            pinAdapter.updateCoordSys(it)
            pinAdapter.notifyDataSetChanged()
        })

        // Observe for changes to action/selection mode
        viewModel.isInSelectionMode.observe(viewLifecycleOwner, {
            refreshList()
        })

        // Observe for recent multiple deletions made through action mode
        viewModel.lastMultiDeletedItems.observe(viewLifecycleOwner, {
            if (it != null) {
                val size = (it.first?.size ?: 0) + (it.second?.size ?: 0)
                val snackBar = Snackbar.make(
                    binding.pinAppBar,
                    resources.getQuantityString(R.plurals.number_deleted, size, size),
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.undo) { viewModel.onRestoreLastDeletedPins() }
                snackBar.show()
            }
        })

        // Fetch sorting parameters
        sortBy = viewModel.sharedPreference.getInt(SORT_BY_KEY, sortBy)
        sortAscending = viewModel.sharedPreference.getBoolean(SORT_ASCENDING_KEY, sortAscending)

        // Set up FAB
        binding.pinFab.setMenuListener(object : SimpleMenuListenerAdapter() {
            override fun onMenuItemSelected(menuItem: MenuItem?): Boolean {
                return when (menuItem?.itemId) {
                    R.id.fab_new_pin -> {
                        viewModel.openPinCreator(null)
                        true
                    }
                    R.id.fab_from_code -> {
                        openShareCode()
                        true
                    }
                    else -> false
                }
            }
        })

        // Set up app bar
        binding.pinAppBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.settings -> {
                    viewModel.openSettings()
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
        viewModel.isInSelectionMode.observe(viewLifecycleOwner, { isInSelectionMode ->
            if (isInSelectionMode) {
                binding.pinFab.hide()
            } else {
                binding.pinFab.show()
            }
        })

        return binding.root
    }

    private fun onPinClick(pin: Pin) {
        if (viewModel.isInSelectionMode.value!!) {
            viewModel.toggleSelection(pin.pinId, isChain = false)
            refreshList()
        } else {
            viewModel.showPinInfo(pin.pinId)
        }
    }

    private fun onPinLongClick(pin: Pin): Boolean {
        if (!viewModel.isInSelectionMode.value!!) {
            viewModel.enterSelectionMode()
            viewModel.toggleSelection(pin.pinId, isChain = false)
            refreshList()
        }
        return true
    }

    private fun onChainClick(chain: Chain) {
        if (viewModel.isInSelectionMode.value!!) {
            viewModel.toggleSelection(chain.chainId, true)
            refreshList()
        } else {
            viewModel.showChainOnMap(chain)
        }
    }

    private fun onChainLongClick(chain: Chain): Boolean {
        if (!viewModel.isInSelectionMode.value!!) {
            viewModel.enterSelectionMode()
            viewModel.toggleSelection(chain.chainId, true)
            refreshList()
        }
        return true
    }

    private fun onSortList() {
        viewModel.sharedPreference.edit()
            .putInt(SORT_BY_KEY, sortBy)
            .putBoolean(SORT_ASCENDING_KEY, sortAscending)
            .apply()
        refreshList()
        binding.pinRecyclerview.smoothScrollToPosition(0)
    }

    private fun refreshList(
        newPins: List<Pin>? = viewModel.allPins.value,
        newChains: List<Chain>? = viewModel.allChains.value
    ) {
        pinAdapter.sortAndSubmitList(
            newPins,
            newChains,
            viewModel.selectedIds,
            sortBy,
            sortAscending
        )
    }

    private fun openShareCode() {
        val alertDialog = AlertDialog.Builder(context)
            .setView(R.layout.dialog_share_input)
            .create()

        alertDialog.show()

        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val shareCodeInput = alertDialog.findViewById<TextInputEditText>(R.id.share_code_edit)

        alertDialog.findViewById<Button>(R.id.paste)?.setOnClickListener {
            activity?.let { activity ->
                val clipboard =
                    activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val item = clipboard.primaryClip?.getItemAt(0)
                val pasteData = item?.text
                if (pasteData != null) {
                    shareCodeInput.setText(pasteData)
                } else {
                    Toast.makeText(context, R.string.paste_error, Toast.LENGTH_SHORT).show()
                }
            }
        }

        alertDialog.findViewById<Button>(R.id.done).setOnClickListener {
            val shareCode = shareCodeInput.text.toString()
            when {
                shareCode.isBlank() -> alertDialog.dismiss()
                viewModel.processShareCode(shareCode) -> alertDialog.dismiss()
                else -> {
                    alertDialog.findViewById<TextInputLayout>(R.id.share_code_layout).error =
                        getString(R.string.share_code_error)
                }
            }
        }
    }
}
