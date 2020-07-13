package com.nujiak.reconnaissance.fragments.ruler

import android.content.res.Resources
import com.google.android.gms.maps.model.LatLng
import com.nujiak.reconnaissance.R
import com.nujiak.reconnaissance.database.*

fun generateRulerList(oldList: List<RulerItem>?, items: List<ReconData>, resources: Resources): MutableList<RulerItem> {

    val newList = oldList?.toMutableList() ?: mutableListOf()
    if (newList.size == 1 && newList.first() is RulerItem.RulerEmptyItem) {
        newList.clear()
    }

    for (item in items) {
        if (item is Pin) {

            /* Pin */

            if (newList.isNotEmpty()) {
                // Add a MeasurementItem if this is not the first point in the list.
                val prevPointItem = newList.last() as RulerItem.RulerPointItem
                newList.add(
                    RulerItem.RulerMeasurementItem(
                        listOf(
                            prevPointItem.position,
                            LatLng(item.latitude, item.longitude)
                        ),
                        prevPointItem.name,
                        item.name
                    )
                )
            }
            // Add a PointItem to represent the Pin
            newList.add(
                RulerItem.RulerPointItem(
                    item.pinId,
                    item.name,
                    LatLng(item.latitude, item.longitude),
                    item.color
                )
            )
        } else if (item is Chain) {

            /* Chain */

            val nodes = item.getNodes().toMutableList()
            if (!nodes.first().isCheckpoint) {
                nodes[0] = nodes.first().copy(name = resources.getString(R.string.start))
            }
            if (!nodes.last().isCheckpoint) {
                nodes[nodes.lastIndex] = nodes.last().copy(name = resources.getString(R.string.end))
            }

            val currentSegment = mutableListOf<ChainNode>()
            for ((index, node) in nodes.withIndex()) {
                if (index == 0) {
                    // First node of Chain
                    val name =
                        if (node.isCheckpoint) node.name else resources.getString(R.string.start)

                    if (newList.isNotEmpty()) {
                        // Add a MeasurementItem if this is not the first point in the list.
                        val prevPointItem = newList.last() as RulerItem.RulerPointItem

                        currentSegment.add(node.copy(name = name))
                        newList.add(
                            RulerItem.RulerMeasurementItem(
                                listOf(prevPointItem.position, node.position),
                                prevPointItem.name,
                                "${node.name} (${item.name})"
                            )
                        )
                    }

                    newList.add(
                        RulerItem.RulerPointItem(
                            item.chainId * Long.MAX_VALUE / 5000 + index,
                            "$name (${item.name})",
                            node.position,
                            item.color
                        )
                    )
                    currentSegment.add(node)
                } else if (!node.isCheckpoint) {
                    currentSegment.add(node)
                } else {
                    // Node is a new checkpoint, put in MeasurementItem and PointItem for current
                    // segment then restart
                    currentSegment.add(node)
                    val prevPointItem = newList.last() as RulerItem.RulerPointItem
                    newList.add(
                        RulerItem.RulerMeasurementItem(
                            currentSegment.map { it.position },
                            prevPointItem.name,
                            "${node.name} (${item.name})"
                        )
                    )
                    newList.add(
                        RulerItem.RulerPointItem(
                            item.chainId * Long.MAX_VALUE / 5000 + index,
                            "${node.name} (${item.name})",
                            node.position,
                            item.color
                        )
                    )
                    currentSegment.clear()
                    currentSegment.add(node)
                }
            }
        }
    }

    return newList
}