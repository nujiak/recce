package com.nujiak.reconnaissance.database

import com.google.android.gms.maps.model.LatLng

data class ChainNode(
    val name: String,
    val position: LatLng,
    val parentChain: Chain? = null
) {
    val isCheckpoint: Boolean
        get() = name.isNotBlank()
}