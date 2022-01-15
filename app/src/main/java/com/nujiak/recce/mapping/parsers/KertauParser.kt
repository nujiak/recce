package com.nujiak.recce.mapping.parsers

import androidx.core.text.isDigitsOnly
import com.nujiak.recce.mapping.Coordinate
import com.nujiak.recce.mapping.KertauUtils
import kotlin.math.pow

/**
 * Parses Kertau 1948 grids
 */
object KertauParser : Parser() {
    override fun parse(s: String): Coordinate? {
        val groups = s.trim().split("[,;\\s]+".toRegex())
        if (groups.size != 2) {
            return null
        }

        val easting = shiftToMagnitude(groups[0]) ?: return null
        val northing = shiftToMagnitude(groups[1]) ?: return null

        return KertauUtils.parse(easting, northing)
    }

    private fun shiftToMagnitude(s: String, magnitude: Int = 6): Double? {
        if (!s.isDigitsOnly()) {
            return null
        }
        return s.toIntOrNull()?.times(10.0.pow(magnitude - s.length))
    }
}
