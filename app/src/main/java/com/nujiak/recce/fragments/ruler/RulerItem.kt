package com.nujiak.recce.fragments.ruler

import com.google.android.gms.maps.model.LatLng

sealed class RulerItem {

    companion object {
        const val ITEM_ID_MEASUREMENT = Long.MAX_VALUE
        const val ITEM_ID_EMPTY = Long.MIN_VALUE
    }

    abstract val id: Long

    data class RulerPointItem(
        val adjustedId: Long,
        val name: String,
        val position: LatLng,
        val colorId: Int
    ) : RulerItem() {
        override val id = adjustedId
    }

    data class RulerMeasurementItem(
        val points: List<LatLng>,
        val startName: String,
        val endName: String
    ) : RulerItem() {
        override val id = ITEM_ID_MEASUREMENT
    }

    object RulerEmptyItem : RulerItem() {
        override val id = ITEM_ID_EMPTY
    }
}