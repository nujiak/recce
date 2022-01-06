package com.nujiak.recce.mapping.parsers

import com.nujiak.recce.mapping.Coordinate
import com.nujiak.recce.mapping.QthUtils

/**
 * Parses Maidenhead coding
 */
object QthParser : Parser {

    private val regex = Regex("^[A-Ra-r]{2}([0-9]{2}([A-Xa-x]{2}([0-9]{2})?)?)?$")

    override fun parse(s: String): Coordinate? {
        val str = s.trim()
        if (!regex.containsMatchIn(str)) {
            return null
        }

        val field = Pair(str[0], str[1])
        val square = if (str.length >= 4) Pair(str[2], str[3]) else Pair('0', '0')
        val subsquare = if (str.length >= 6) Pair(str[4], str[5]) else Pair('a', 'a')
        val extendedSquare = if (str.length >= 8) Pair(str[6], str[7]) else Pair('0', '0')

        return QthUtils.parse(field, square, subsquare, extendedSquare)
    }
}