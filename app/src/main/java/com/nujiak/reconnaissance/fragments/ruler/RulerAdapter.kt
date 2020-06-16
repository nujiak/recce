package com.nujiak.reconnaissance.fragments.ruler

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

const val ITEM_VIEW_TYPE_PIN = 0
const val ITEM_VIEW_TYPE_MEASUREMENT = 1
const val ITEM_VIEW_TYPE_EMPTY = 2

class RulerAdapter :
    androidx.recyclerview.widget.ListAdapter<RulerItem, RecyclerView.ViewHolder>(RulerDiffCallback()) {

    private val adapterScope = CoroutineScope(Dispatchers.Default)

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is RulerItem.RulerPinItem -> ITEM_VIEW_TYPE_PIN
            is RulerItem.RulerMeasurementItem -> ITEM_VIEW_TYPE_MEASUREMENT
            is RulerItem.RulerEmptyItem -> ITEM_VIEW_TYPE_EMPTY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_PIN -> RulerPinViewHolder.from(parent)
            ITEM_VIEW_TYPE_MEASUREMENT -> RulerMeasurementViewHolder.from(parent)
            ITEM_VIEW_TYPE_EMPTY -> RulerEmptyViewHolder.from(parent)
            else -> throw IllegalArgumentException("Invalid viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is RulerPinViewHolder -> {
                holder.bind(getItem(position) as RulerItem.RulerPinItem)
            }
            is RulerMeasurementViewHolder -> holder.bind(
                getItem(position - 1) as RulerItem.RulerPinItem,
                getItem(position + 1) as RulerItem.RulerPinItem
            )
            is RulerEmptyViewHolder -> holder.bind()
        }
    }
}

class RulerDiffCallback : DiffUtil.ItemCallback<RulerItem>() {
    override fun areItemsTheSame(oldItem: RulerItem, newItem: RulerItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: RulerItem, newItem: RulerItem): Boolean {
        if (oldItem is RulerItem.RulerMeasurementItem
            && newItem is RulerItem.RulerMeasurementItem
        ) {
            return false
        }
        return oldItem == newItem
    }

}