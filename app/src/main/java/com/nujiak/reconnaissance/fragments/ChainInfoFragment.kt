package com.nujiak.reconnaissance.fragments

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.maps.android.SphericalUtil
import com.nujiak.reconnaissance.MainViewModel
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.database.Chain
import com.nujiak.reconnaissance.database.getNodes
import com.nujiak.reconnaissance.databinding.DialogChainInfoBinding
import com.nujiak.reconnaissance.utils.PIN_CARD_BACKGROUNDS
import com.nujiak.reconnaissance.utils.formatAsAreaString
import com.nujiak.reconnaissance.utils.formatAsDistanceString
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChainInfoFragment : DialogFragment() {

    private val viewModel : MainViewModel by activityViewModels()
    private lateinit var binding: DialogChainInfoBinding
    var chainId = -1L

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity(), R.style.Theme_Reconnaissance_InfoDialogs)
        val layoutInflater = LayoutInflater.from(context)
        binding = DialogChainInfoBinding.inflate(layoutInflater, null, false)
        builder.setView(binding.root)

        val dialog = builder.create()

        viewModel.allChains.observe(this, { allPins ->
            allPins.find { chain -> chain.chainId == chainId }.let{
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
        dialog?.let { dialog ->
            binding.pinName.text = chain.name
            val color = ContextCompat.getColor(requireContext(), PIN_CARD_BACKGROUNDS[chain.color])
            binding.root.setCardBackgroundColor(color)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            if (chain.group.isBlank()) {
                binding.pinGroup.visibility = View.GONE
            } else {
                binding.pinGroup.text = chain.group
                binding.pinGroup.setTextColor(color)
            }

            binding.pinMap.setOnClickListener {
                viewModel.showChainOnMap(chain)
                dismiss()
            }
            binding.pinEdit.setOnClickListener {
                viewModel.openChainCreator(chain)
                dismiss()
            }

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

        }
    }
}