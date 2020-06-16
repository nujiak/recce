package com.nujiak.reconnaissance.fragments.selector

import com.nujiak.reconnaissance.database.Pin

data class PinWrapper(
    val pin: Pin,
    val selectionIndex: Int = -1,
    val id: Long = pin.pinId
)