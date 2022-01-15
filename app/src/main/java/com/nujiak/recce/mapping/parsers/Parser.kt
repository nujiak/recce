package com.nujiak.recce.mapping.parsers

import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.mapping.Coordinate
import java.text.NumberFormat

/**
 * Parses an input coordinate string to a [Coordinate]
 */
abstract class Parser {
    abstract fun parse(s: String): Coordinate?

    protected fun String.toDoubleOrNull(numberFormat: NumberFormat): Double? {
        return try {
            numberFormat.parse(this)?.toDouble()
        } catch (e: NumberFormatException) {
            null
        }
    }

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
                CoordinateSystem.BNG -> BngParser
                CoordinateSystem.QTH -> QthParser
            }
        }
    }
}
