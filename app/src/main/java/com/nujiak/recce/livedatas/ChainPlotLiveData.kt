package com.nujiak.recce.livedatas

import androidx.lifecycle.MutableLiveData
import com.google.android.gms.maps.model.LatLng
import com.nujiak.recce.database.Chain
import com.nujiak.recce.database.ChainNode
import com.nujiak.recce.fragments.map.ChainPlotter

class ChainPlotLiveData : MutableLiveData<ChainPlotLiveData.ChainPlot>() {
    data class ChainPlot(
        val size: Int,
        val distance: Double,
        val checkpoints: List<ChainNode>,
        val points: List<LatLng>,
    )

    private val chainPlotter = ChainPlotter()

    override fun onActive() {
        super.onActive()
        updateValue()
    }

    private fun updateValue() {
        value = ChainPlot(
            chainPlotter.size,
            chainPlotter.totalDistance,
            chainPlotter.checkpoints,
            chainPlotter.points,
        )
    }

    /**
     * Adds the point at [position] to the plot
     *
     * @param position
     * @param name
     */
    fun addPoint(position: LatLng, name: String) {
        chainPlotter.addPoint(position, name)
        updateValue()
    }

    /**
     * Removes the last added point
     *
     */
    fun removeLastPoint() {
        chainPlotter.removeLastPoint()
        updateValue()
    }

    /**
     * Removes all points and clears the plot
     *
     */
    fun removeAll() {
        chainPlotter.removeAll()
        updateValue()
    }

    /**
     * Returns the [Chain] created from all the nodes stored
     *
     * @param name
     * @return
     */
    fun getChain(name: String): Chain {
        return chainPlotter.getChain(name)
    }
}
