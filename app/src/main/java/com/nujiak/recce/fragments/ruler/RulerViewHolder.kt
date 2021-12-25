package com.nujiak.recce.fragments.ruler

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.maps.android.SphericalUtil
import com.nujiak.recce.R
import com.nujiak.recce.databinding.RulerEmptyItemBinding
import com.nujiak.recce.databinding.RulerMeasurementItemBinding
import com.nujiak.recce.databinding.RulerPinItemBinding
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.utils.PIN_CARD_BACKGROUNDS
import com.nujiak.recce.utils.degToRad
import com.nujiak.recce.utils.getGridString

/**
 * ViewHolder for Pins in the Ruler fragment
 */
class RulerPinViewHolder private constructor(private val binding: RulerPinItemBinding) :
    RecyclerView.ViewHolder(binding.root) {
    companion object {
        fun from(parent: ViewGroup): RulerPinViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = RulerPinItemBinding.inflate(layoutInflater, parent, false)
            return RulerPinViewHolder(binding)
        }
    }

    @SuppressLint("SetTextI18n")
    fun bind(rulerPointItem: RulerItem.RulerPointItem, coordSys: CoordinateSystem) {
        val location = rulerPointItem.position

        // Pin name
        binding.rulerPinName.text = rulerPointItem.name
        binding.rulerPinName.isSelected = true

        // Grid System
        binding.rulerPinGridSystem.text =
            binding.root.resources.getStringArray(R.array.coordinate_systems)[coordSys.index]
        binding.rulerPinGrid.text =
            getGridString(location.latitude, location.longitude, coordSys, binding.root.resources)

        val context = binding.root.context
        val color = ContextCompat.getColor(context, PIN_CARD_BACKGROUNDS[rulerPointItem.colorId])
        binding.rulerPinItemCardView.strokeColor = color
        binding.rulerPinName.setTextColor(color)
    }
}

/**
 * ViewHolder for Measurements in the Ruler Fragment
 */
class RulerMeasurementViewHolder private constructor(private val binding: RulerMeasurementItemBinding) :
    RecyclerView.ViewHolder(binding.root) {
    companion object {
        fun from(parent: ViewGroup): RulerMeasurementViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = RulerMeasurementItemBinding.inflate(layoutInflater, parent, false)
            return RulerMeasurementViewHolder(binding)
        }
    }

    @SuppressLint("SetTextI18n")
    fun bind(rulerMeasurementItem: RulerItem.RulerMeasurementItem, formatAsAngle: (Float, Boolean) -> String) {
        val points = rulerMeasurementItem.points
        binding.rulerFrom.text = rulerMeasurementItem.startName
        binding.rulerTo.text = rulerMeasurementItem.endName

        var distance = 0.0
        for (index in 0 until (points.size - 1)) {
            distance += SphericalUtil.computeDistanceBetween(points[index], points[index + 1])
        }

        var heading = SphericalUtil.computeHeading(points.first(), points.last())
        if (heading < 0) {
            heading += 360
        }

        binding.rulerDist.text = "%.2fm".format(distance)
        binding.rulerDir.text = formatAsAngle(degToRad(heading).toFloat(), false)
        if (points.size > 2) {
            binding.rulerIntermediate.text = binding.root.resources.getQuantityString(
                R.plurals.intermediate_nodes,
                points.size - 2,
                points.size - 2
            )
            binding.rulerIntermediate.visibility = View.VISIBLE
        } else {
            binding.rulerIntermediate.visibility = View.GONE
        }
    }
}

class RulerEmptyViewHolder private constructor(binding: RulerEmptyItemBinding) :
    RecyclerView.ViewHolder(binding.root) {
    companion object {
        fun from(parent: ViewGroup): RulerEmptyViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = RulerEmptyItemBinding.inflate(layoutInflater, parent, false)
            return RulerEmptyViewHolder(binding)
        }
    }

    fun bind() {}
}
