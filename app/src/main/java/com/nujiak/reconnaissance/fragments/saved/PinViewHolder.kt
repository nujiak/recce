package com.nujiak.reconnaissance.fragments.saved

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.maps.android.SphericalUtil
import com.nujiak.reconnaissance.*
import com.nujiak.reconnaissance.database.Chain
import com.nujiak.reconnaissance.database.Pin
import com.nujiak.reconnaissance.database.getNodes
import com.nujiak.reconnaissance.databinding.PinListChainItemBinding
import com.nujiak.reconnaissance.databinding.PinListHeaderItemBinding
import com.nujiak.reconnaissance.databinding.PinListItemBinding

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

        val chainNodes = chain.getNodes()
        val checkpoints = mutableListOf<String>()
        var distance = 0.0
        for ((index, node) in chainNodes.withIndex()) {
            if (node.isCheckpoint) {
                checkpoints.add(node.name)
            }
            if (index != chainNodes.size - 1) {
                distance += SphericalUtil.computeDistanceBetween(node.position, chainNodes[index+1].position)
            }
        }
        binding.chainCheckpoints.text = when (checkpoints.isEmpty()) {
            true -> binding.root.resources.getString(R.string.none)
            false -> checkpoints.joinToString()
        }

        if (chain.cyclical) {
            // Area
            binding.chainDistance.text = SphericalUtil.computeArea(chainNodes.map { it.position }).formatAsAreaString()
            binding.chainDistanceDesc.text = binding.root.resources.getString(R.string.area)
            binding.areaIcon.visibility = View.VISIBLE
            binding.routeIcon.visibility = View.INVISIBLE
        } else {
            // Route
            binding.chainDistance.text = distance.formatAsDistanceString()
            binding.chainDistanceDesc.text = binding.root.resources.getString(R.string.distance)
            binding.areaIcon.visibility = View.INVISIBLE
            binding.routeIcon.visibility = View.VISIBLE
        }

        if (item.selectionIndex >= 0) {
            binding.chainSelectedIndex.visibility = View.VISIBLE
            binding.chainSelectedIndex.setTextColor(color)
            binding.chainSelectedIndex.text = (item.selectionIndex + 1).toString()
            binding.selectionShade.visibility = View.VISIBLE
        } else {
            binding.chainSelectedIndex.visibility = View.INVISIBLE
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