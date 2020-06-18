package com.nujiak.reconnaissance

import com.nujiak.reconnaissance.database.Pin
import com.nujiak.reconnaissance.fragments.selector.HeaderItem
import com.nujiak.reconnaissance.fragments.selector.PinWrapper
import com.nujiak.reconnaissance.fragments.selector.SelectorItem

/**
 * Sorts a List of Pin items by name
 *
 * @param pins List<Pin> to be processed and submitted to adapter
 * @param selectedPinIds List<Long> containing IDs of currently selected pins
 * @param ascending Boolean, false to reverse the lexicographical order
 */
fun sortByName(pins: List<Pin>, selectedPinIds: List<Long>, ascending: Boolean): List<SelectorItem> {
    val selectedIds = selectedPinIds.toList()
    val newList = pins.toMutableList().sortedBy { it.name }.map{ PinWrapper(it, selectedIds.indexOf(it.pinId)) }
    return if (ascending) newList else newList.reversed()
}

/**
 * Sorts a List of Pin items by group, then by name within each group
 *
 * @param pins List<Pin> to be processed and submitted to adapter
 * @param selectedPinIds List<Long> containing IDs of currently selected pins
 */
fun sortByGroup(pins: List<Pin>, selectedPinIds: List<Long>): List<SelectorItem> {
    val sortedList = pins.toMutableList().apply{
        sortBy { it.name }
        sortBy { it.group }
    }

    val newList = mutableListOf<SelectorItem>()
    var currentGroup = ""
    for ((index, pin) in sortedList.withIndex()) {
        if (index == 0 && currentGroup != "") {
            currentGroup = pin.group
            newList.add(HeaderItem(currentGroup))
        } else if (currentGroup != pin.group) {
            currentGroup = pin.group
            newList.add(HeaderItem(currentGroup))
        }
        newList.add(PinWrapper(pin, selectedPinIds.indexOf(pin.pinId)))
    }
    return newList
}

/**
 * Sorts a List of Pin items by their id
 *
 * @param pins List<Pin> to be processed and submitted to adapter
 * @param selectedPinIds List<Long> containing IDs of currently selected pins
 * @param ascending Boolean, false to reverse the numerical order
 */
fun sortByTime(pins: List<Pin>, selectedPinIds: List<Long>, ascending: Boolean): List<SelectorItem> {
    val newList = pins.toMutableList().sortedBy { it.pinId }.map{ PinWrapper(it, selectedPinIds.indexOf(it.pinId)) }
    return if (ascending) newList else newList.reversed()
}