package com.nujiak.reconnaissance.database

import com.google.android.gms.maps.model.LatLng

fun Chain.getNodes(): List<ChainNode> {
    val dataList = this.data.split(';')
    val nodeList = mutableListOf<ChainNode>()

    for (item in dataList) {
        if (item.isNotBlank()) {
            val (lat, lng, name) = item.split(',')
            val node = ChainNode(
                name = name,
                position = LatLng(lat.toDouble(), lng.toDouble()),
                parentChain = this
            )
            nodeList.add(node)
        }
    }
    return nodeList.toList()
}

fun Chain.withNodes(data: List<ChainNode>): Chain =
    this.copy(data = data.toChainDataString())

fun List<ChainNode>.toChainDataString(): String {
    val newDataBuilder = StringBuilder()

    for (node in this) {
        newDataBuilder.append(node.position.latitude)
        newDataBuilder.append(',')
        newDataBuilder.append(node.position.longitude)
        newDataBuilder.append(',')
        newDataBuilder.append(node.name.trim())
        newDataBuilder.append(';')
    }

    return newDataBuilder.toString()
}