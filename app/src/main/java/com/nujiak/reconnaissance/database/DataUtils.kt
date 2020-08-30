package com.nujiak.reconnaissance.database

import com.nujiak.reconnaissance.round

fun toChainAndPins(shareCode: String): Pair<List<Pin>, List<Chain>> {
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
                pins.add(
                    Pin(
                        name = split[1],
                        latitude = split[2].toDouble(),
                        longitude = split[3].toDouble(),
                        color = split[4].toInt(),
                        group = split[5]
                    )
                )
            }
            "c" -> {
                chains.add(
                    Chain(
                        name = split[1],
                        data = split[2],
                        color = split[3].toInt(),
                        group = split[4],
                        cyclical = split[5] == "1"
                    )
                )
            }
        }
    }

    return Pair(pins, chains)
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
                "c|${chain.name}|${withTruncatedCoordinates(chain.data, 6)}|${chain.color}|${chain.group}|${if (chain.cyclical) 1 else 0}\n"
            )
        }
    }

    return shareCodeBuilder.toString().trim()
}