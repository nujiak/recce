package com.nujiak.reconnaissance.fragments.selector

import com.nujiak.reconnaissance.database.Pin

sealed class SelectorItem(val id: Long)

data class PinWrapper(
    val pin: Pin,
    val selectionIndex: Int = -1
): SelectorItem(pin.pinId)

data class HeaderItem(
    val headerName: String
) : SelectorItem(-1)