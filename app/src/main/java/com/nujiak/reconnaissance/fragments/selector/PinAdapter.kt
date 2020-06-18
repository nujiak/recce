package com.nujiak.reconnaissance.fragments.selector

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
 * @param onItemClick function to be invoked when an item is clicked)
 */
class PinAdapter(
    private val onItemClick: (Pin) -> Unit,
    private val onItemLongClick: (Pin) -> Boolean,
    private var coordSysId: Int
) : ListAdapter<SelectorItem, RecyclerView.ViewHolder>(PinDiffCallback()) {

    private val adapterScope = CoroutineScope(Dispatchers.Default)

    companion object {
        const val ITEM_VIEW_TYPE_PIN = 0
        const val ITEM_VIEW_TYPE_HEADER = 1
        const val SORT_BY_GROUP = 100
        const val SORT_BY_NAME = 101
        const val SORT_BY_TIME = 102
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is PinWrapper -> ITEM_VIEW_TYPE_PIN
            is HeaderItem -> ITEM_VIEW_TYPE_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_PIN -> PinViewHolder.from(parent)
            ITEM_VIEW_TYPE_HEADER -> HeaderViewHolder.from(parent)
            else -> throw IllegalArgumentException("Invalid viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PinViewHolder -> {
                holder.bind(
                    getItem(position) as PinWrapper,
                    onItemClick,
                    onItemLongClick,
                    coordSysId
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
        selectedPinIds: List<Long>,
        sortBy: Int,
        ascending: Boolean
    ) {
        adapterScope.launch {
            val newList =
                if (allPins != null) {
                    when (sortBy) {
                        SORT_BY_GROUP -> sortByGroup(allPins, selectedPinIds)
                        SORT_BY_NAME -> sortByName(allPins, selectedPinIds, ascending)
                        SORT_BY_TIME -> sortByTime(allPins, selectedPinIds, ascending)
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