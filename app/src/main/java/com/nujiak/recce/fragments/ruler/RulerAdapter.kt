package com.nujiak.recce.fragments.ruler

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.nujiak.recce.enums.CoordinateSystem

private enum class RulerItemViewType(val index: Int) {
    PIN(0),
    MEASUREMENT(1),
    EMPTY(2),
}

class RulerAdapter(
    private var coordSys: CoordinateSystem,
    private val formatAsAngle: (Float, Boolean) -> String,
    private val formatAsGrids: (Double, Double) -> String,
    private val formatAsDistance: (Double) -> String,
) : androidx.recyclerview.widget.ListAdapter<RulerItem, RecyclerView.ViewHolder>(RulerDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is RulerItem.RulerPointItem -> RulerItemViewType.PIN.index
            is RulerItem.RulerMeasurementItem -> RulerItemViewType.MEASUREMENT.index
            is RulerItem.RulerEmptyItem -> RulerItemViewType.EMPTY.index
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            RulerItemViewType.PIN.index -> RulerPinViewHolder.from(parent)
            RulerItemViewType.MEASUREMENT.index -> RulerMeasurementViewHolder.from(parent)
            RulerItemViewType.EMPTY.index -> RulerEmptyViewHolder.from(parent)
            else -> throw IllegalArgumentException("Invalid viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is RulerPinViewHolder -> {
                holder.bind(getItem(position) as RulerItem.RulerPointItem, coordSys, formatAsGrids)
            }
            is RulerMeasurementViewHolder -> holder.bind(
                getItem(position) as RulerItem.RulerMeasurementItem,
                formatAsAngle,
                formatAsDistance,
            )
            is RulerEmptyViewHolder -> holder.bind()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateCoordSys(newCoordSys: CoordinateSystem) {
        if (coordSys != newCoordSys) {
            coordSys = newCoordSys
            notifyDataSetChanged()
        }
    }
}

class RulerDiffCallback : DiffUtil.ItemCallback<RulerItem>() {
    override fun areItemsTheSame(oldItem: RulerItem, newItem: RulerItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: RulerItem, newItem: RulerItem): Boolean {
        if (oldItem is RulerItem.RulerMeasurementItem &&
            newItem is RulerItem.RulerMeasurementItem
        ) {
            return false
        }
        return oldItem == newItem
    }
}
