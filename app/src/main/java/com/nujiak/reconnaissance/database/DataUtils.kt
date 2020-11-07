package com.nujiak.reconnaissance.database

import com.nujiak.reconnaissance.utils.round

fun toPinsAndChains(shareCode: String): Pair<List<Pin>, List<Chain>> {
    val pins = mutableListOf<Pin>()
    val chains = mutableListOf<Chain>()

    val substrings = shareCode.split('\n')
    for (substring in substrings) {
        val split = substring.split('|')
        if (split.size != 6) {
            continue
        }
        when (split[0]) {
            "p" -> {
                toPin(split)?.let {
                    pins.add(it)
                }
            }
            "c" -> {
                toChain(split)?.let {
                    chains.add(it)
                }
            }
        }
    }

    return Pair(pins, chains)
}

private fun toChain(shareCodeSplit: List<String>): Chain? {
    val name = shareCodeSplit[1]
    val data = shareCodeSplit[2]
    val color = shareCodeSplit[3].toIntOrNull()
    val group = shareCodeSplit[4]
    val cyclical = shareCodeSplit[5] == "1"
    return if (color == null || color < 0 || color > 4 || name.isBlank()) {
        null
    } else {
        Chain(
            name = name,
            data = data,
            color = color,
            group = group,
            cyclical = cyclical
        )
    }
}

private fun toPin(shareCodeSplit: List<String>): Pin? {
    val name = shareCodeSplit[1]
    val lat = shareCodeSplit[2].toDoubleOrNull()
    val lng = shareCodeSplit[3].toDoubleOrNull()
    val color = shareCodeSplit[4].toIntOrNull()
    val group = shareCodeSplit[5]
    return if (lat == null || lng == null
               || color == null || color < 0 || color > 4
               || name.isBlank()) {
        null
    } else {
        Pin(
            name = name,
            latitude = lat,
            longitude = lng,
            color = color,
            group = group
        )
    }
}

fun toShareCode(pins: List<Pin>?, chains: List<Chain>?): String {
    val shareCodeBuilder = StringBuilder()
    if (pins != null) {
        for (pin in pins) {
            shareCodeBuilder.append(
                "p|${pin.name}|${pin.latitude.round(6)}|${pin.longitude.round(6)}|${pin.color}|${pin.group}\n"
            )
        }
    }

    if (chains != null) {
        for (chain in chains) {
            shareCodeBuilder.append(
                "c|${chain.name}|${
                    withTruncatedCoordinates(
                        chain.data,
                        6
                    )
                }|${chain.color}|${chain.group}|${if (chain.cyclical) 1 else 0}\n"
            )
        }
    }

    return shareCodeBuilder.toString().trim()
}