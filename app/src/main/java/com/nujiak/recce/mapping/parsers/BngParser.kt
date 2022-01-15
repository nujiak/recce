package com.nujiak.recce.mapping.parsers

import com.nujiak.recce.mapping.BngUtils
import com.nujiak.recce.mapping.Coordinate
import com.nujiak.recce.mapping.filterWhitespaces
import com.nujiak.recce.mapping.intsFromGridString

/**
 * Parses BNG grids
 */
object BngParser : Parser() {

    private val utmRegex = Regex("(^[JHONST])([A-HJ-Z])(\\d{1,12})$")

    override fun parse(s: String): Coordinate? {
        val match = utmRegex.find(s.filterWhitespaces().uppercase()) ?: return null
        val matchGroups = match.groupValues

        val majorLetter = matchGroups[1][0]
        val minorLetter = matchGroups[2][0]
        val (easting, northing) = intsFromGridString(matchGroups[3]) ?: return null

        return BngUtils.parse(majorLetter, minorLetter, easting, northing)
    }
}
