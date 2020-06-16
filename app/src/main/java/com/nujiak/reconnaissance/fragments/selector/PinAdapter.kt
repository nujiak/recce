package com.nujiak.reconnaissance.fragments.selector

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.nujiak.reconnaissance.database.Pin

/**
 * Adapter for RecyclerView in PinEditorFragment
 *
 * @param onItemClick function to be invoked when an item is clicked)
 */
class PinAdapter(
    private val onItemClick: (Pin) -> Unit,
    private val onItemLongClick: (Pin) -> Boolean,
    private var coordSysId: Int
) : ListAdapter<PinWrapper, PinViewHolder>(PinDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).pin.pinId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinViewHolder {
        return PinViewHolder.from(parent)
    }

    override fun onBindViewHolder(holder: PinViewHolder, position: Int) {
        val pin = getItem(position)
        holder.bind(
            pin,
            onItemClick,
            onItemLongClick,
            coordSysId
        )
    }

    fun updateCoordSys(newCoordSysId: Int) {
        coordSysId = newCoordSysId
    }
}

class PinDiffCallback : DiffUtil.ItemCallback<PinWrapper>() {

    override fun areItemsTheSame(oldItem: PinWrapper, newItem: PinWrapper): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: PinWrapper, newItem: PinWrapper): Boolean {
        return oldItem == newItem
    }

}