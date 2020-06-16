package com.nujiak.reconnaissance.fragments.ruler

import com.nujiak.reconnaissance.database.Pin

sealed class RulerItem {

    companion object {
        const val ITEM_ID_MEASUREMENT = -1L
        const val ITEM_ID_EMPTY = -2L
    }

    abstract val id: Long

    data class RulerPinItem(val pin: Pin) : RulerItem() {
        override val id = pin.pinId
    }

    object RulerMeasurementItem : RulerItem() {
        override val id = ITEM_ID_MEASUREMENT
    }

    object RulerEmptyItem : RulerItem() {
        override val id = ITEM_ID_EMPTY
    }
}