package com.nujiak.recce.fragments.saved

import android.content.res.Resources
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.nujiak.recce.database.Chain
import com.nujiak.recce.database.Pin
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.enums.SortBy
import com.nujiak.recce.utils.sortByGroup
import com.nujiak.recce.utils.sortByName
import com.nujiak.recce.utils.sortByTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adapter for RecyclerView in PinEditorFragment
 *
 * @param onPinClick function to be invoked when a pin item is clicked
 * @param onPinLongClick function to be invoked when a pin item is long-clicked
 * @param onChainClick function to be invoked when a chain item is clicked
 * @param onChainLongClick function to be invoked when a chain item is long-clicked
 * @param coordSysId coordinate system ID
 * @param resources Resources
 *
 */
class PinAdapter(
    private val onPinClick: (Pin) -> Unit,
    private val onPinLongClick: (Pin) -> Boolean,
    private val onChainClick: (Chain) -> Unit,
    private val onChainLongClick: (Chain) -> Boolean,
    private var coordSysId: CoordinateSystem,
    private val formatAsGrids: (Double, Double) -> String,
    private val formatAsDistance: (Double) -> String,
    private val formatAsArea: (Double) -> String,
    private val resources: Resources,
) : ListAdapter<SelectorItem, RecyclerView.ViewHolder>(PinDiffCallback()) {

    private val adapterScope = CoroutineScope(Dispatchers.Default)

    companion object {
        enum class ItemViewType(val index: Int) {
            PIN(0),
            CHAIN(1),
            HEADER(2),
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is PinWrapper -> ItemViewType.PIN.index
            is ChainWrapper -> ItemViewType.CHAIN.index
            is HeaderItem -> ItemViewType.HEADER.index
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ItemViewType.PIN.index -> PinViewHolder.from(parent)
            ItemViewType.CHAIN.index -> ChainViewHolder.from(parent)
            ItemViewType.HEADER.index -> HeaderViewHolder.from(parent)
            else -> throw IllegalArgumentException("Invalid viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // Set item to take up the full span if it is a chain or header (not a pin)
        val layoutParams = holder.itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams
        layoutParams.isFullSpan = holder is HeaderViewHolder

        when (holder) {
            is PinViewHolder -> {
                holder.bind(getItem(position) as PinWrapper, onPinClick, onPinLongClick, coordSysId, formatAsGrids)
            }
            is ChainViewHolder -> {
                holder.bind(getItem(position) as ChainWrapper, onChainClick, onChainLongClick, formatAsDistance, formatAsArea)
            }
            is HeaderViewHolder -> {
                holder.bind(getItem(position) as HeaderItem)
            }
        }
    }

    fun updateCoordSys(newCoordSys: CoordinateSystem) {
        coordSysId = newCoordSys
    }

    fun sortAndSubmitList(
        allPins: List<Pin>?,
        allChains: List<Chain>?,
        selectedIds: List<Long>,
        sortBy: SortBy,
        ascending: Boolean
    ) {
        adapterScope.launch {
            val newList =
                if (allPins != null && allChains != null) {
                    when (sortBy) {
                        SortBy.GROUP -> sortByGroup(
                            allPins,
                            allChains,
                            selectedIds
                        )
                        SortBy.NAME -> sortByName(
                            allPins,
                            allChains,
                            selectedIds,
                            ascending
                        )
                        SortBy.TIME -> sortByTime(
                            allPins,
                            allChains,
                            selectedIds,
                            ascending,
                            resources
                        )
                    }
                } else {
                    listOf()
                }

            withContext(Dispatchers.Main) {
                submitList(newList)
            }
        }
    }
}

class PinDiffCallback : DiffUtil.ItemCallback<SelectorItem>() {

    override fun areItemsTheSame(oldItem: SelectorItem, newItem: SelectorItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: SelectorItem, newItem: SelectorItem): Boolean {
        return oldItem == newItem
    }
}
