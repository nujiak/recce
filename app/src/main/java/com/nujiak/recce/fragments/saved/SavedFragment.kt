package com.nujiak.recce.fragments.saved

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nujiak.recce.MainViewModel
import com.nujiak.recce.R
import com.nujiak.recce.database.Chain
import com.nujiak.recce.database.Pin
import com.nujiak.recce.databinding.FragmentSavedBinding
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.enums.SortBy
import com.nujiak.recce.utils.animate
import com.nujiak.recce.utils.spToPx
import dagger.hilt.android.AndroidEntryPoint
import io.github.yavski.fabspeeddial.SimpleMenuListenerAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SavedFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentSavedBinding
    private lateinit var pinAdapter: PinAdapter

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentSavedBinding.inflate(inflater, container, false)

        // Set up RecyclerView adapter
        pinAdapter = PinAdapter(
            { pin -> onPinClick(pin) },
            { pin -> onPinLongClick(pin) },
            { chain -> onChainClick(chain) },
            { chain -> onChainLongClick(chain) },
            viewModel.coordinateSystem.value ?: CoordinateSystem.atIndex(0),
            viewModel::formatAsGrids,
            viewModel::formatAsDistance,
            viewModel::formatAsArea,
            resources,
        )
        binding.pinRecyclerview.adapter = pinAdapter
        val gridLayoutManager = StaggeredGridLayoutManager(getSpanCount(), Configuration.ORIENTATION_PORTRAIT)
        gridLayoutManager.gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
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
        viewModel.lastMultiDeletedItems.observe(viewLifecycleOwner) {
            if (it == null) {
                return@observe
            }
            val (pins, chains) = it
            val size = (pins?.size ?: 0) + (chains?.size ?: 0)
            val snackBar = Snackbar.make(
                binding.pinAppBar,
                resources.getQuantityString(R.plurals.number_deleted, size, size),
                Snackbar.LENGTH_LONG
            ).setAction(R.string.undo) { viewModel.onRestoreLastDeletedPins() }
            snackBar.view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(p0: View?) {}

                override fun onViewDetachedFromWindow(p0: View?) {
                    animate(binding.pinFab.translationY, 0f) { animatedValue ->
                        binding.pinFab.translationY = animatedValue
                    }
                }
            })
            snackBar.show()
        }

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
                    onSortList(SortBy.NAME, true)
                    true
                }
                R.id.sort_by_alphabetical_dsc -> {
                    onSortList(SortBy.NAME, false)
                    true
                }
                R.id.sort_by_time_asc -> {
                    onSortList(SortBy.TIME, true)
                    true
                }
                R.id.sort_by_time_dsc -> {
                    onSortList(SortBy.TIME, false)
                    true
                }
                R.id.sort_by_group -> {
                    onSortList(SortBy.GROUP, false)
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

    /**
     * Calculates the span count by dividing the screen width by the width of each item
     *
     * @return
     */
    private fun getSpanCount(): Int {
        val count = (resources.displayMetrics.widthPixels / resources.spToPx(196f)).toInt()
        return if (count > 0) count else 1
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
            viewModel.showChainInfo(chain.chainId)
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

    private fun onSortList(sortBy: SortBy, sortAscending: Boolean) {
        viewModel.sortBy = sortBy
        viewModel.sortAscending = sortAscending

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
            viewModel.sortBy,
            viewModel.sortAscending
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
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val clipboard =
                    requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val item = clipboard.primaryClip?.getItemAt(0)
                val pasteData = item?.text
                withContext(Dispatchers.Main) {
                    if (pasteData != null) {
                        shareCodeInput.setText(pasteData)
                    } else {
                        Toast.makeText(context, R.string.paste_error, Toast.LENGTH_SHORT).show()
                    }
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
