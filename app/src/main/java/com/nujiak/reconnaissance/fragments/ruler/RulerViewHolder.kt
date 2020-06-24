package com.nujiak.reconnaissance.fragments.ruler

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import com.nujiak.reconnaissance.PIN_CARD_BACKGROUNDS
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.databinding.RulerEmptyItemBinding
import com.nujiak.reconnaissance.databinding.RulerMeasurementItemBinding
import com.nujiak.reconnaissance.databinding.RulerPinItemBinding
import com.nujiak.reconnaissance.getGridString

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
    fun bind(rulerPinItem: RulerItem.RulerPinItem, coordSysId: Int) {
        val pin = rulerPinItem.pin

        // Pin name
        binding.rulerPinName.text = pin.name
        binding.rulerPinLatLng.text = "%.6f, %.6f".format(pin.latitude, pin.longitude)
        binding.rulerPinName.isSelected = true

        // Grid System
        binding.rulerPinGridSystem.text =
            binding.root.resources.getStringArray(R.array.coordinate_systems)[coordSysId]
        binding.rulerPinGrid.text =
            getGridString(pin.latitude, pin.longitude, coordSysId, binding.root.resources)

        val context = binding.root.context
        val color = ContextCompat.getColor(context, PIN_CARD_BACKGROUNDS[pin.color])
        binding.rulerPinItemCardView.setCardBackgroundColor(color)
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
    fun bind(fromItem: RulerItem.RulerPinItem, toItem: RulerItem.RulerPinItem) {
        val fromPin = fromItem.pin
        val toPin = toItem.pin
        binding.rulerFrom.text = fromPin.name
        binding.rulerTo.text = toPin.name

        val fromLatLng = LatLng(fromPin.latitude, fromPin.longitude)
        val toLatLng = LatLng(toPin.latitude, toPin.longitude)

        val distance = SphericalUtil.computeDistanceBetween(fromLatLng, toLatLng)
        val heading = SphericalUtil.computeHeading(fromLatLng, toLatLng)

        binding.rulerDist.text = "%.2fm".format(distance)
        binding.rulerDir.text = "%.2f deg".format(heading)
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