package com.nujiak.recce.fragments.saved

import com.nujiak.recce.database.Chain
import com.nujiak.recce.database.Pin

sealed class SelectorItem(val id: Long)

data class PinWrapper(
    val pin: Pin,
    val selectionIndex: Int = -1
) : SelectorItem(pin.pinId)

data class ChainWrapper(
    val chain: Chain,
    val selectionIndex: Int = -1
) : SelectorItem(-chain.chainId)

data class HeaderItem(
    val headerName: String
) : SelectorItem(Long.MAX_VALUE)
