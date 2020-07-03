package com.nujiak.reconnaissance.fragments.selector

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.maps.android.SphericalUtil
import com.nujiak.reconnaissance.PIN_CARD_BACKGROUNDS
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.database.Chain
import com.nujiak.reconnaissance.database.Pin
import com.nujiak.reconnaissance.database.getParsedData
import com.nujiak.reconnaissance.databinding.PinListChainItemBinding
import com.nujiak.reconnaissance.databinding.PinListHeaderItemBinding
import com.nujiak.reconnaissance.databinding.PinListItemBinding
import com.nujiak.reconnaissance.formatAsDistanceString
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

        binding.pinGrid.text =
            getGridString(pin.latitude, pin.longitude, coordSysId, binding.root.resources)

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

class ChainViewHolder private constructor(private val binding: PinListChainItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

    companion object {
        fun from(parent: ViewGroup): ChainViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = PinListChainItemBinding.inflate(layoutInflater, parent, false)
            return ChainViewHolder(binding)
        }
    }

    @SuppressLint("SetTextI18n")
    fun bind(
        item: ChainWrapper,
        onItemClick: (Chain) -> Unit,
        onItemLongClick: (Chain) -> Boolean
    ) {
        val chain = item.chain
        if (chain.group != "") {
            binding.chainGroup.text = chain.group
            binding.chainGroup.visibility = View.VISIBLE
        } else {
            binding.chainGroup.visibility = View.INVISIBLE
        }
        binding.chainName.text = chain.name

        val context = binding.root.context
        val color = ContextCompat.getColor(context, PIN_CARD_BACKGROUNDS[chain.color])
        binding.pinListItemParent.setCardBackgroundColor(color)
        binding.chainGroup.setTextColor(color)

        binding.pinListItemParent.setOnClickListener { onItemClick(chain) }
        binding.pinListItemParent.setOnLongClickListener { onItemLongClick(chain) }

        val chainData = chain.getParsedData()
        val checkpoints = mutableListOf<String>()
        var distance = 0.0
        for ((index, point) in chainData.withIndex()) {
            if (point.second.isNotBlank()) {
                checkpoints.add(point.second)
            }
            if (index != chainData.size - 1) {
                distance += SphericalUtil.computeDistanceBetween(point.first, chainData[index+1].first)
            }
        }
        binding.chainCheckpoints.text = when (checkpoints.isEmpty()) {
            true -> binding.root.resources.getString(R.string.none)
            false -> checkpoints.joinToString()
        }

        binding.chainDistance.text = distance.formatAsDistanceString()

        if (item.isSelected) {
            binding.chainSelected.visibility = View.VISIBLE
            binding.chainSelected.setTextColor(color)
            binding.selectionShade.visibility = View.VISIBLE
        } else {
            binding.chainSelected.visibility = View.INVISIBLE
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