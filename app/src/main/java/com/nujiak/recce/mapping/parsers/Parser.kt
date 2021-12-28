package com.nujiak.recce.mapping.parsers

import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.mapping.Coordinate

/**
 * Parses an input coordinate string to a [Coordinate]
 */
sealed interface Parser {
    fun parse(s: String): Coordinate?

    companion object {
        /**
         * Returns the [Parser] for the [CoordinateSystem]
         *
         * @param coordinateSystem
         * @return
         */
        fun getParser(coordinateSystem: CoordinateSystem): Parser {
            return when (coordinateSystem) {
                CoordinateSystem.MGRS -> MgrsParser
                CoordinateSystem.UTM -> UtmParser
                CoordinateSystem.KERTAU -> KertauParser
                CoordinateSystem.WGS84 -> Wgs84Parser
            }
        }
    }
}
