package com.nujiak.recce.mapping.parsers

import com.nujiak.recce.mapping.Coordinate
import com.nujiak.recce.mapping.MgrsUtils
import com.nujiak.recce.mapping.filterWhitespaces
import com.nujiak.recce.mapping.intsFromGridString

/**
 * Parses MGRS grids
 */
object MgrsParser : Parser() {

    override fun parse(s: String): Coordinate? {
        val matchResult =
            mgrsRegex.find(s.filterWhitespaces().uppercase())

        matchResult ?: return null

        val matchGroupValues = matchResult.groupValues

        val gridString = matchGroupValues[3]
        val (easting, northing) = intsFromGridString(gridString) ?: return null
        val (band, eastingLtr, northingLtr) = matchGroupValues[2].toList()

        return MgrsUtils.parse(matchGroupValues[1].toInt(), band, eastingLtr, northingLtr, easting, northing)
    }

    /**
     * Regex matching the MGRS format with all whitespaces trimmed
     *
     * Example of match: "48NUG123456123456"
     */
    private val mgrsRegex = Regex("(^\\d{1,2})(\\w{3})(\\d{1,12})$")
}
