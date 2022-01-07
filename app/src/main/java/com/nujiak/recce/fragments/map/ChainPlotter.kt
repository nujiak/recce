package com.nujiak.recce.fragments.map

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import com.nujiak.recce.database.Chain
import com.nujiak.recce.database.ChainNode

/**
 * Manages a list of [ChainNode]s for forming a [Chain]
 *
 */
class ChainPlotter() {

    private val nodes: MutableList<ChainNode> = mutableListOf()
    private val _checkpoints: MutableList<ChainNode> = mutableListOf()
    private val distances: MutableList<Double> = mutableListOf()

    /**
     * Number of nodes in the plot
     */
    val size: Int
        get() = nodes.size

    /**
     * Total cumulative distance between the nodes
     */
    val totalDistance: Double
        get() = if (distances.isEmpty()) Double.NaN else distances.last()

    /**
     * A list of named checkpoints in the plot
     */
    val checkpoints: List<ChainNode>
        get() = _checkpoints

    /**
     * A list of positions of all the nodes
     */
    val points: List<LatLng>
        get() = nodes.map { it.position }

    /**
     * Returns a [Chain] created from the nodes stored
     *
     * @param name
     * @return
     */
    fun getChain(name: String): Chain {
        return Chain(name, nodes)
    }

    /**
     * Add the point at [position] to the plot
     *
     * @param position
     * @param name
     */
    fun addPoint(position: LatLng, name: String = "") {
        val node = ChainNode(name, position)
        nodes.add(node)
        if (name.isNotBlank()) {
            _checkpoints.add(node)
        }
        if (distances.isEmpty()) {
            distances.add(0.0)
        } else {
            distances.add(
                distances.last() + SphericalUtil.computeDistanceBetween(
                    nodes.last().position,
                    node.position
                )
            )
        }
    }

    /**
     * Remove the last added point in the plot
     *
     */
    fun removeLastPoint() {
        val node = nodes.removeLast()
        if (_checkpoints.lastOrNull() == node) {
            _checkpoints.removeLast()
        }
        distances.removeLast()
    }

    /**
     * Resets the plot
     *
     */
    fun removeAll() {
        nodes.clear()
        distances.clear()
        _checkpoints.clear()
    }
}
