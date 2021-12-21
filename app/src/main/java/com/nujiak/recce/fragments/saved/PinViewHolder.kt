package com.nujiak.recce.fragments.saved

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.maps.android.SphericalUtil
import com.nujiak.recce.R
import com.nujiak.recce.database.Chain
import com.nujiak.recce.database.Pin
import com.nujiak.recce.databinding.PinListChainItemBinding
import com.nujiak.recce.databinding.PinListHeaderItemBinding
import com.nujiak.recce.databinding.PinListItemBinding
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.utils.PIN_CARD_BACKGROUNDS
import com.nujiak.recce.utils.dpToPx
import com.nujiak.recce.utils.formatAsAreaString
import com.nujiak.recce.utils.formatAsDistanceString
import com.nujiak.recce.utils.getGridString

private const val STROKE_SIZE_DP: Float = 2f
private const val STROKE_SIZE_SELECTED_DP: Float = 4f

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
        coordSys: CoordinateSystem
    ) {
        val pin = item.pin
        if (pin.group != "") {
            binding.pinGroup.text = pin.group
            binding.pinGroup.visibility = View.VISIBLE
        } else {
            binding.pinGroup.visibility = View.GONE
        }
        binding.pinName.text = pin.name

        binding.pinGridSystem.text =
            binding.root.resources.getStringArray(R.array.coordinate_systems)[coordSys.index]

        binding.pinGrid.text =
            getGridString(pin.latitude, pin.longitude, coordSys, binding.root.resources)

        val context = binding.root.context
        val color = ContextCompat.getColor(context, PIN_CARD_BACKGROUNDS[pin.color])
        binding.pinName.setTextColor(color)
        binding.pinListItemParent.strokeColor = color
        (binding.pinGroup.background as GradientDrawable).setStroke(context.resources.dpToPx(STROKE_SIZE_DP).toInt(), color)

        binding.pinListItemParent.setOnClickListener { onItemClick(pin) }
        binding.pinListItemParent.setOnLongClickListener { onItemLongClick(pin) }

        if (item.selectionIndex >= 0) {
            binding.pinSelectedIndex.visibility = View.VISIBLE
            binding.pinSelectedIndex.text = (item.selectionIndex + 1).toString()
            binding.pinSelectedIndex.setTextColor(color)
            binding.selectionShade.visibility = View.VISIBLE
            binding.pinListItemParent.cardElevation = 0f
            binding.pinListItemParent.strokeWidth = context.resources.dpToPx(STROKE_SIZE_SELECTED_DP).toInt()
        } else {
            binding.pinSelectedIndex.visibility = View.INVISIBLE
            binding.selectionShade.visibility = View.GONE
            binding.pinListItemParent.cardElevation = context.resources.dpToPx(8f)
            binding.pinListItemParent.strokeWidth = context.resources.dpToPx(STROKE_SIZE_DP).toInt()
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
            binding.chainGroup.visibility = View.GONE
        }
        binding.chainName.text = chain.name

        val context = binding.root.context
        val color = ContextCompat.getColor(context, PIN_CARD_BACKGROUNDS[chain.color])
        binding.pinListChainItemParent.strokeColor = color
        binding.chainName.setTextColor(color)
        (binding.chainGroup.background as GradientDrawable).setStroke(context.resources.dpToPx(STROKE_SIZE_DP).toInt(), color)

        binding.pinListChainItemParent.setOnClickListener { onItemClick(chain) }
        binding.pinListChainItemParent.setOnLongClickListener { onItemLongClick(chain) }

        val chainNodes = chain.nodes
        val checkpoints = mutableListOf<String>()
        var distance = 0.0
        for ((index, node) in chainNodes.withIndex()) {
            if (node.isCheckpoint) {
                checkpoints.add(node.name)
            }
            if (index != chainNodes.size - 1) {
                distance += SphericalUtil.computeDistanceBetween(
                    node.position,
                    chainNodes[index + 1].position
                )
            }
        }
        binding.chainCheckpoints.text = when (checkpoints.isEmpty()) {
            true -> binding.root.resources.getString(R.string.none)
            false -> checkpoints.joinToString()
        }

        if (chain.cyclical) {
            // Area
            binding.chainDistance.text =
                SphericalUtil.computeArea(chainNodes.map { it.position }).formatAsAreaString()
            binding.chainDistanceDesc.text = binding.root.resources.getString(R.string.area)
            binding.areaIcon.apply {
                visibility = View.VISIBLE
                imageTintList = ColorStateList.valueOf(color)
            }
            binding.routeIcon.visibility = View.INVISIBLE
        } else {
            // Route
            binding.chainDistance.text = distance.formatAsDistanceString()
            binding.chainDistanceDesc.text = binding.root.resources.getString(R.string.distance)
            binding.areaIcon.visibility = View.INVISIBLE
            binding.routeIcon.apply {
                visibility = View.VISIBLE
                imageTintList = ColorStateList.valueOf(color)
            }
        }

        if (item.selectionIndex >= 0) {
            binding.chainSelectedIndex.visibility = View.VISIBLE
            binding.chainSelectedIndex.setTextColor(color)
            binding.chainSelectedIndex.text = (item.selectionIndex + 1).toString()
            binding.selectionShade.visibility = View.VISIBLE
            binding.pinListChainItemParent.cardElevation = 0f
            binding.pinListChainItemParent.strokeWidth = context.resources.dpToPx(STROKE_SIZE_SELECTED_DP).toInt()
        } else {
            binding.chainSelectedIndex.visibility = View.INVISIBLE
            binding.selectionShade.visibility = View.GONE
            binding.pinListChainItemParent.cardElevation = context.resources.dpToPx(8f)
            binding.pinListChainItemParent.strokeWidth = context.resources.dpToPx(STROKE_SIZE_DP).toInt()
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
