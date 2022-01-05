package com.nujiak.recce.fragments

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.model.LatLng
import com.nujiak.recce.MainViewModel
import com.nujiak.recce.R
import com.nujiak.recce.databinding.DialogGoToBinding
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.mapping.Mapping
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GoToFragment : DialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: DialogGoToBinding

    private var coordSys = CoordinateSystem.atIndex(0)

    var initialLatLng: LatLng? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder =
            AlertDialog.Builder(requireActivity())
        val layoutInflater = LayoutInflater.from(context)
        binding = DialogGoToBinding.inflate(layoutInflater, null, false)
        builder.setView(binding.root)

        val dialog = builder.create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Fetch coordinate system setting
        coordSys = viewModel.coordinateSystem.value ?: CoordinateSystem.atIndex(0)

        setUp()

        initialLatLng?.let {
            val coordinates = Mapping.transformTo(coordSys, it)
            binding.newPinGridsEditText.setText(coordinates?.toString() ?: "")
        }

        return dialog
    }

    private fun setUp() {

        binding.newPinGridsInput.hint = getString(
            when (coordSys) {
                CoordinateSystem.UTM -> R.string.utm
                CoordinateSystem.MGRS -> R.string.mgrs
                CoordinateSystem.KERTAU -> R.string.kertau
                CoordinateSystem.WGS84 -> R.string.wgs_84
                CoordinateSystem.BNG -> R.string.bng
            }
        )

        binding.newPinGridsEditText.setOnKeyListener { _, _, _ ->
            binding.newPinGridsInput.error = null
            false
        }

        binding.goButton.setOnClickListener {
            onCompleted()
        }
    }

    private fun onCompleted() {
        val coordinate = Mapping.parse(binding.newPinGridsEditText.text.toString(), coordSys)

        if (coordinate == null) {
            binding.newPinGridsInput.error = getString(R.string.invalid_coordinate)
            return
        }

        viewModel.mapGoTo(coordinate.latLng)

        dismiss()
    }
}
