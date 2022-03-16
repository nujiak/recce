package com.nujiak.recce.fragments

import android.app.AlertDialog
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.maps.android.SphericalUtil
import com.nujiak.recce.MainViewModel
import com.nujiak.recce.R
import com.nujiak.recce.database.Chain
import com.nujiak.recce.databinding.DialogChainInfoBinding
import com.nujiak.recce.utils.PIN_CARD_BACKGROUNDS
import com.nujiak.recce.utils.dpToPx
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChainInfoFragment : DialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: DialogChainInfoBinding
    var chainId = -1L

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder =
            AlertDialog.Builder(requireActivity(), R.style.Theme_Recce_InfoDialogs)
        val layoutInflater = LayoutInflater.from(context)
        binding = DialogChainInfoBinding.inflate(layoutInflater, null, false)
        builder.setView(binding.root)

        val dialog = builder.create()

        viewModel.allChains.observe(this, { allPins ->
            allPins.find { chain -> chain.chainId == chainId }.let {
                if (it != null) {
                    update(it)
                } else {
                    dismiss()
                }
            }
        })

        return dialog
    }

    private fun update(chain: Chain) {
        binding.pinName.text = chain.name

        ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[chain.color]).let { color ->
            binding.pinName.setTextColor(color)
            binding.root.strokeColor = color
            (binding.pinGroup.background as GradientDrawable).setStroke(resources.dpToPx(2f).toInt(), color)

            val colorStateList = ColorStateList.valueOf(color)
            (binding.pinMap as MaterialButton).iconTint = colorStateList
            (binding.pinEdit as MaterialButton).iconTint = colorStateList

            binding.areaIcon.imageTintList = colorStateList
            binding.routeIcon.imageTintList = colorStateList
        }

        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        if (chain.group.isBlank()) {
            binding.pinGroup.visibility = View.GONE
        } else {
            binding.pinGroup.visibility = View.VISIBLE
            binding.pinGroup.text = chain.group
        }

        binding.pinMap.setOnClickListener {
            viewModel.showChainOnMap(chain)
            dismiss()
        }
        binding.pinEdit.setOnClickListener {
            viewModel.openChainCreator(chain)
            dismiss()
        }

        if (chain.description.isNotEmpty()) {
            binding.chainDescriptionHeading.visibility = View.VISIBLE
            binding.chainDescription.visibility = View.VISIBLE
            binding.chainDescription.text = chain.description
        } else {
            binding.chainDescriptionHeading.visibility = View.GONE
            binding.chainDescription.visibility = View.GONE
        }

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
                viewModel.formatAsArea(SphericalUtil.computeArea(chainNodes.map { it.position }))
            binding.chainDistanceDesc.text = binding.root.resources.getString(R.string.area)
            binding.areaIcon.visibility = View.VISIBLE
            binding.routeIcon.visibility = View.INVISIBLE
        } else {
            // Route
            binding.chainDistance.text = viewModel.formatAsDistance(distance)
            binding.chainDistanceDesc.text = binding.root.resources.getString(R.string.distance)
            binding.areaIcon.visibility = View.INVISIBLE
            binding.routeIcon.visibility = View.VISIBLE
        }
    }
}
