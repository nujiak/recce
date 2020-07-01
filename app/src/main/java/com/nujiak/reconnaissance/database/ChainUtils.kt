package com.nujiak.reconnaissance.database

import com.google.android.gms.maps.model.LatLng

fun Chain.getParsedData(): List<Pair<LatLng, String>> {
    val dataList = this.data.split(';')
    val output = mutableListOf<Pair<LatLng, String>>()

    for (item in dataList) {
        if (item.isNotBlank()) {
            val itemSplit = item.split(',')
            output.add(LatLng(itemSplit[0].toDouble(), itemSplit[1].toDouble()) to itemSplit[2])
        }
    }
    return output.toList()
}

fun Chain.withData(data: List<Pair<LatLng, String>>): Chain = this.copy(data = data.toChainDataString())

fun List<Pair<LatLng, String>>.toChainDataString(): String {
    val newDataBuilder = StringBuilder()

    for (item in this) {
        newDataBuilder.append(item.first.latitude)
        newDataBuilder.append(',')
        newDataBuilder.append(item.first.longitude)
        newDataBuilder.append(',')
        newDataBuilder.append(item.second.trim())
        newDataBuilder.append(';')
    }

    return newDataBuilder.toString()
}