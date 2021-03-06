package com.nujiak.recce.fragments

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import com.nujiak.recce.MainViewModel
import com.nujiak.recce.R
import com.nujiak.recce.database.Pin
import com.nujiak.recce.databinding.DialogPinInfoBinding
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.mapping.Mapping
import com.nujiak.recce.utils.PIN_CARD_BACKGROUNDS
import com.nujiak.recce.utils.dpToPx
import com.nujiak.recce.utils.spToPx
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PinInfoFragment : DialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: DialogPinInfoBinding
    var pinId = -1L

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder =
            AlertDialog.Builder(requireActivity(), R.style.Theme_Recce_InfoDialogs)
        val layoutInflater = LayoutInflater.from(context)
        binding = DialogPinInfoBinding.inflate(layoutInflater, null, false)
        builder.setView(binding.root)

        val dialog = builder.create()

        viewModel.allPins.observe(this, { allPins ->
            allPins.find { pin -> pin.pinId == pinId }.let {
                if (it != null) {
                    update(it)
                } else {
                    dismiss()
                }
            }
        })

        return dialog
    }

    private fun update(pin: Pin) {
        binding.pinName.text = pin.name

        // Color elements
        ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[pin.color]).let { color ->
            binding.pinName.setTextColor(color)
            binding.root.strokeColor = color
            (binding.pinGroup.background as GradientDrawable).setStroke(resources.dpToPx(2f).toInt(), color)

            val colorStateList = ColorStateList.valueOf(color)
            (binding.pinOpenIn as MaterialButton).iconTint = colorStateList
            (binding.pinMap as MaterialButton).iconTint = colorStateList
            (binding.pinEdit as MaterialButton).iconTint = colorStateList
        }

        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        if (pin.group.isBlank()) {
            binding.pinGroup.visibility = View.GONE
        } else {
            binding.pinGroup.visibility = View.VISIBLE
            binding.pinGroup.text = pin.group
        }

        if (pin.description.isNotEmpty()) {
            binding.pinDescriptionHeading.visibility = View.VISIBLE
            binding.pinDescription.visibility = View.VISIBLE
            binding.pinDescription.text = pin.description
        } else {
            binding.pinDescriptionHeading.visibility = View.GONE
            binding.pinDescription.visibility = View.GONE
        }

        binding.pinOpenIn.setOnClickListener {
            val gmmIntentUri = Uri.parse(
                "geo:<${pin.latitude}>,<${pin.longitude}>?q=<${pin.latitude}>,<${pin.longitude}>(${pin.name})"
            )
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")

            activity?.let {
                if (mapIntent.resolveActivity(it.packageManager) != null) {
                    startActivity(mapIntent)
                }
            }
        }
        binding.pinMap.setOnClickListener {
            viewModel.showPinOnMap(pin)
            dismiss()
        }
        binding.pinEdit.setOnClickListener {
            viewModel.openPinCreator(pin)
            dismiss()
        }

        binding.pinGridLayout.columnCount =
            (resources.displayMetrics.widthPixels / resources.spToPx(196f))
                .toInt()
                .coerceAtMost(4)

        val tinyPadding = resources.getDimension(R.dimen.tiny_padding).toInt()
        val smallPadding = resources.getDimension(R.dimen.small_padding).toInt()
        val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layoutParams.setMargins(smallPadding, tinyPadding, smallPadding, tinyPadding)

        for (coordinateSystem in CoordinateSystem.values()) {
            val coordinate = Mapping.transformTo(coordinateSystem, LatLng(pin.latitude, pin.longitude))

            val view = layoutInflater.inflate(R.layout.pin_info_grids_item, binding.pinGridLayout, false)
            view.findViewById<TextView>(R.id.pin_grid_system).setText(coordinateSystem.shortName)
            view.findViewById<TextView>(R.id.pin_grid).text = coordinate?.toString() ?: getString(R.string.not_available)
            view.layoutParams = layoutParams
            binding.pinGridLayout.addView(view)
        }
    }
}
