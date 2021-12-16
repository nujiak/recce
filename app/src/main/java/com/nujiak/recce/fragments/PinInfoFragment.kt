package com.nujiak.recce.fragments

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.nujiak.recce.*
import com.nujiak.recce.database.Pin
import com.nujiak.recce.databinding.DialogPinInfoBinding
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.utils.PIN_CARD_BACKGROUNDS
import com.nujiak.recce.utils.getGridString
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
        dialog?.let { dialog ->
            binding.pinName.text = pin.name

            val color = ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[pin.color])
            binding.root.setCardBackgroundColor(color)

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            if (pin.group.isBlank()) {
                binding.pinGroup.visibility = View.GONE
            } else {
                binding.pinGroup.text = pin.group
                binding.pinGroup.setTextColor(color)
            }

            binding.pinLatLng.text =
                getGridString(pin.latitude, pin.longitude, CoordinateSystem.WGS84, resources)
            binding.pinUtmGrid.text =
                getGridString(pin.latitude, pin.longitude, CoordinateSystem.UTM, resources)
            binding.pinMgrsGrid.text =
                getGridString(pin.latitude, pin.longitude, CoordinateSystem.MGRS, resources)
            binding.pinKertauGrid.text =
                getGridString(pin.latitude, pin.longitude, CoordinateSystem.KERTAU, resources)

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
                    "geo:<${pin.latitude}>,<${pin.longitude}>?q=<${pin.latitude}>,<${pin.longitude}>(${pin.name})")
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

        }
    }
}