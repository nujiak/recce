package com.nujiak.reconnaissance.fragments.selector

import android.content.res.Resources
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nujiak.reconnaissance.database.Chain
import com.nujiak.reconnaissance.database.Pin
import com.nujiak.reconnaissance.sortByGroup
import com.nujiak.reconnaissance.sortByName
import com.nujiak.reconnaissance.sortByTime
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
    private var coordSysId: Int,
    private val resources: Resources
) : ListAdapter<SelectorItem, RecyclerView.ViewHolder>(PinDiffCallback()) {

    private val adapterScope = CoroutineScope(Dispatchers.Default)

    companion object {
        const val ITEM_VIEW_TYPE_PIN = 0
        const val ITEM_VIEW_TYPE_CHAIN = 1
        const val ITEM_VIEW_TYPE_HEADER = 2
        const val SORT_BY_GROUP = 100
        const val SORT_BY_NAME = 101
        const val SORT_BY_TIME = 102
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is PinWrapper -> ITEM_VIEW_TYPE_PIN
            is ChainWrapper -> ITEM_VIEW_TYPE_CHAIN
            is HeaderItem -> ITEM_VIEW_TYPE_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_PIN -> PinViewHolder.from(parent)
            ITEM_VIEW_TYPE_CHAIN -> ChainViewHolder.from(parent)
            ITEM_VIEW_TYPE_HEADER -> HeaderViewHolder.from(parent)
            else -> throw IllegalArgumentException("Invalid viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PinViewHolder -> {
                holder.bind(
                    getItem(position) as PinWrapper,
                    onPinClick,
                    onPinLongClick,
                    coordSysId
                )
            }
            is ChainViewHolder -> {
                holder.bind(
                    getItem(position) as ChainWrapper,
                    onChainClick,
                    onChainLongClick
                )
            }
            is HeaderViewHolder -> {
                holder.bind(
                    getItem(position) as HeaderItem
                )
            }
        }
    }

    fun updateCoordSys(newCoordSysId: Int) {
        coordSysId = newCoordSysId
    }

    fun sortAndSubmitList(
        allPins: List<Pin>?,
        allChains: List<Chain>?,
        selectedPinIds: List<Long>,
        selectedChainIds: List<Long>,
        sortBy: Int,
        ascending: Boolean
    ) {
        adapterScope.launch {
            val newList =
                if (allPins != null && allChains != null) {
                    when (sortBy) {
                        SORT_BY_GROUP -> sortByGroup(
                            allPins,
                            allChains,
                            selectedPinIds,
                            selectedChainIds
                        )
                        SORT_BY_NAME -> sortByName(
                            allPins,
                            allChains,
                            selectedPinIds,
                            selectedChainIds,
                            ascending
                        )
                        SORT_BY_TIME -> sortByTime(
                            allPins,
                            allChains,
                            selectedPinIds,
                            selectedChainIds,
                            ascending,
                            resources
                        )
                        else -> throw IllegalArgumentException("Invalid sort: $sortBy")
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