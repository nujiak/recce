package com.nujiak.recce.mapping.parsers

import com.nujiak.recce.mapping.Coordinate
import com.nujiak.recce.mapping.UtmUtils
import com.nujiak.recce.mapping.filterWhitespaces
import com.nujiak.recce.mapping.intsFromGridString

/**
 * Parses UTM grids
 */
object UtmParser : Parser() {

    private val utmRegex = Regex("(^\\d{1,2})([NSns])(\\d{1,14})$")

    override fun parse(s: String): Coordinate? {
        val match = utmRegex.find(s.filterWhitespaces()) ?: return null
        val matchGroups = match.groupValues

        val zone = matchGroups[1].toInt()
        val band = matchGroups[2][0].let {
            when (it.uppercaseChar()) {
                'N' -> UtmUtils.UtmBand.NORTH
                'S' -> UtmUtils.UtmBand.SOUTH
                else -> return null
            }
        }
        val (easting, northing) = intsFromGridString(matchGroups[3], 7) ?: return null

        return UtmUtils.parse(zone, band, easting, northing)
    }
}
