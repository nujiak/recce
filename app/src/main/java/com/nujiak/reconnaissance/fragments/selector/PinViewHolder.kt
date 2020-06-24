package com.nujiak.reconnaissance.fragments.selector

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.nujiak.reconnaissance.PIN_CARD_BACKGROUNDS
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.database.Pin
import com.nujiak.reconnaissance.databinding.PinListHeaderItemBinding
import com.nujiak.reconnaissance.databinding.PinListItemBinding
import com.nujiak.reconnaissance.getGridString

class PinViewHolder private constructor(private val binding: PinListItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

    companion object {
        fun from(parent: ViewGroup): PinViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = PinListItemBinding.inflate(layoutInflater, parent, false)
            return PinViewHolder(binding)
        }
    }

    fun bind(
        item: PinWrapper,
        onItemClick: (Pin) -> Unit,
        onItemLongClick: (Pin) -> Boolean,
        coordSysId: Int
    ) {
        val pin = item.pin
        if (pin.group != "") {
            binding.pinGroup.text = pin.group
            binding.pinGroup.visibility = View.VISIBLE
        } else {
            binding.pinGroup.visibility = View.INVISIBLE
        }
        binding.pinName.text = pin.name
        binding.pinLatLng.text = String.format("%.6f, %.6f", pin.latitude, pin.longitude)

        binding.pinGridSystem.text =
            binding.root.resources.getStringArray(R.array.coordinate_systems)[coordSysId]

        binding.pinGrid.text = getGridString(pin.latitude, pin.longitude, coordSysId, binding.root.resources)

        val context = binding.root.context
        val color = ContextCompat.getColor(context, PIN_CARD_BACKGROUNDS[pin.color])
        binding.pinListItemParent.setCardBackgroundColor(color)
        binding.pinGroup.setTextColor(color)

        binding.pinListItemParent.setOnClickListener { onItemClick(pin) }
        binding.pinListItemParent.setOnLongClickListener { onItemLongClick(pin) }

        if (item.selectionIndex >= 0) {
            binding.pinSelectedIndex.visibility = View.VISIBLE
            binding.pinSelectedIndex.text = (item.selectionIndex + 1).toString()
            binding.pinSelectedIndex.setTextColor(color)
            binding.selectionShade.visibility = View.VISIBLE
        } else {
            binding.pinSelectedIndex.visibility = View.INVISIBLE
            binding.selectionShade.visibility = View.GONE
        }
    }
}

class HeaderViewHolder private constructor(private val binding: PinListHeaderItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

    companion object {
        fun from(parent: ViewGroup): HeaderViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = PinListHeaderItemBinding.inflate(layoutInflater, parent, false)
            return HeaderViewHolder(binding)
        }
    }

    fun bind(item: HeaderItem) {
        val header = item.headerName
        binding.pinHeaderText.text = header
    }
}