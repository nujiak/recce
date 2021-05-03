package com.nujiak.recce.utils

import android.content.res.Resources
import com.nujiak.recce.R
import com.nujiak.recce.database.Chain
import com.nujiak.recce.database.Pin
import com.nujiak.recce.fragments.saved.ChainWrapper
import com.nujiak.recce.fragments.saved.HeaderItem
import com.nujiak.recce.fragments.saved.PinWrapper
import com.nujiak.recce.fragments.saved.SelectorItem

/**
 * Sorts a List of Pin and Chain items by name
 *
 * @param pins List<Pin> to be processed with [chains] and submitted to adapter
 * @param chains List<Chain> to be processed with [pins] and submitted to adapter
 * @param selectedIds List<Long> containing IDs of currently selected pins and chains, where
 *                    negative ids represent Chain ids.
 * @param ascending Boolean, false to reverse the lexicographical order
 */
fun sortByName(
    pins: List<Pin>,
    chains: List<Chain>,
    selectedIds: List<Long>,
    ascending: Boolean
): List<SelectorItem> {

    if (pins.isEmpty() && chains.isEmpty()) {
        return listOf()
    }

    val pinChainList = mutableListOf<PinChain>()

    val pinMap = hashMapOf<PinChain, Pin>()
    val chainMap = hashMapOf<PinChain, Chain>()

    for (pin in pins) {
        val pinChain = PinChain(
            id = pin.pinId,
            name = pin.name,
            group = pin.group,
            isChain = false
        )
        pinChainList.add(pinChain)
        pinMap[pinChain] = pin
    }

    for (chain in chains) {
        val pinChain = PinChain(
            id = chain.chainId,
            name = chain.name,
            group = chain.group,
            isChain = true
        )
        pinChainList.add(pinChain)
        chainMap[pinChain] = chain
    }


    val sortedList = pinChainList.apply {
        sortBy { it.name }
    }

    val newList = sortedList.map {
        if (it.isChain) {
            ChainWrapper(chainMap[it]!!, selectedIds.indexOf(-it.id))
        } else {
            PinWrapper(pinMap[it]!!, selectedIds.indexOf(it.id))
        }
    }

    return if (ascending) newList else newList.reversed()
}

/**
 * Sorts a List of Pin and Chain items by group, then by name within each group
 *
 * @param pins List<Pin> to be processed with [chains] and submitted to adapter
 * @param chains List<Chain> to be processed with [pins] and submitted to adapter
 * @param selectedIds List<Long> containing IDs of currently selected pins and chains, where
 *                    negative ids represent Chain ids.
 */
fun sortByGroup(
    pins: List<Pin>,
    chains: List<Chain>,
    selectedIds: List<Long>
): List<SelectorItem> {

    if (pins.isEmpty() && chains.isEmpty()) {
        return listOf()
    }

    val pinChainList = mutableListOf<PinChain>()

    val pinMap = hashMapOf<PinChain, Pin>()
    val chainMap = hashMapOf<PinChain, Chain>()

    for (pin in pins) {
        val pinChain = PinChain(
            id = pin.pinId,
            name = pin.name,
            group = pin.group,
            isChain = false
        )
        pinChainList.add(pinChain)
        pinMap[pinChain] = pin
    }

    for (chain in chains) {
        val pinChain = PinChain(
            id = chain.chainId,
            name = chain.name,
            group = chain.group,
            isChain = true
        )
        pinChainList.add(pinChain)
        chainMap[pinChain] = chain
    }

    val sortedList = pinChainList.apply {
        sortBy { it.name }
        sortBy { it.group }
    }

    val newList = mutableListOf<SelectorItem>()
    var currentGroup = ""
    for ((index, pinChain) in sortedList.withIndex()) {
        if (index == 0 && currentGroup != "") {
            currentGroup = pinChain.group
            newList.add(HeaderItem(currentGroup))
        } else if (currentGroup != pinChain.group) {
            currentGroup = pinChain.group
            newList.add(HeaderItem(currentGroup))
        }
        if (pinChain.isChain) {
            newList.add(ChainWrapper(chainMap[pinChain]!!, selectedIds.indexOf(-pinChain.id)))
        } else {
            newList.add(PinWrapper(pinMap[pinChain]!!, selectedIds.indexOf(pinChain.id)))
        }
    }
    return newList
}

/**
 * Sorts a List of Pin and Chain items by their id, seperating Pin and Chain
 *
 * @param pins List<Pin> to be processed with [chains] and submitted to adapter
 * @param chains List<Chain> to be processed with [pins] and submitted to adapter
 * @param selectedIds List<Long> containing IDs of currently selected pins and chains, where
 *                    negative ids represent Chain ids.
 * @param ascending Boolean, false to reverse the numerical order
 */
fun sortByTime(
    pins: List<Pin>,
    chains: List<Chain>,
    selectedIds: List<Long>,
    ascending: Boolean,
    resources: Resources
): List<SelectorItem> {

    if (pins.isEmpty() && chains.isEmpty()) {
        return listOf()
    }

    val pinChainList = mutableListOf<PinChain>()

    val pinMap = hashMapOf<PinChain, Pin>()
    val chainMap = hashMapOf<PinChain, Chain>()

    for (pin in pins) {
        val pinChain = PinChain(
            id = pin.pinId,
            name = pin.name,
            group = pin.group,
            isChain = false
        )
        pinChainList.add(pinChain)
        pinMap[pinChain] = pin
    }

    for (chain in chains) {
        val pinChain = PinChain(
            id = chain.chainId,
            name = chain.name,
            group = chain.group,
            isChain = true
        )
        pinChainList.add(pinChain)
        chainMap[pinChain] = chain
    }

    val sortedList = pinChainList.apply {
        sortBy { if (ascending) it.id else -it.id }
        sortBy { it.isChain }
    }

    val newList = mutableListOf<SelectorItem>()
    var currentIsChain = sortedList[0].isChain
    for ((index, pinChain) in sortedList.withIndex()) {
        if (index == 0) {
            if (currentIsChain) {
                newList.add(HeaderItem(resources.getString(R.string.routes_areas)))
            } else {
                newList.add(HeaderItem(resources.getString(R.string.pins)))
            }
        }
        if (pinChain.isChain != currentIsChain) {
            currentIsChain = pinChain.isChain
            newList.add(HeaderItem(resources.getString(if (currentIsChain) R.string.routes_areas else R.string.pins)))
        }

        if (pinChain.isChain) {
            newList.add(ChainWrapper(chainMap[pinChain]!!, selectedIds.indexOf(-pinChain.id)))
        } else {
            newList.add(PinWrapper(pinMap[pinChain]!!, selectedIds.indexOf(pinChain.id)))
        }
    }

    return newList
}

private data class PinChain(
    val id: Long,
    val name: String,
    val group: String,
    val isChain: Boolean
)