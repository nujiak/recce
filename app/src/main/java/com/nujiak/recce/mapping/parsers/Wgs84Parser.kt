package com.nujiak.recce.mapping.parsers

import com.google.android.gms.maps.model.LatLng
import com.nujiak.recce.mapping.Coordinate

/**
 * Parses WGS 84 coordinates
 */
object Wgs84Parser : Parser {
    private val regex1 = Regex("^\\s*([0123456789.]+)([NSns])\\s*([0123456789.]+)([EWew])\\s*$")
    private val regex2 = Regex("^\\s*([-+0123456789.]+)\\s+([-+0123456789.]+)\\s*$")

    private fun checkRegex1(s: String): LatLng? {
        val match = regex1.find(s.uppercase()) ?: return null
        val matchGroupValues = match.groupValues

        var lat = matchGroupValues[1].toDoubleOrNull() ?: return null
        lat = when (matchGroupValues[2].first()) {
            'N' -> lat
            'S' -> - lat
            else -> return null
        }

        var long = matchGroupValues[3].toDoubleOrNull() ?: return null
        long = when (matchGroupValues[4].first()) {
            'E' -> long
            'W' -> - long
            else -> return null
        }

        return LatLng(lat, long)
    }

    private fun checkRegex2(s: String): LatLng? {
        val match = regex2.find(s) ?: return null
        val matchGroupValues = match.groupValues

        val lat = matchGroupValues[1].toDoubleOrNull() ?: return null
        val long = matchGroupValues[2].toDoubleOrNull() ?: return null

        return LatLng(lat, long)
    }

    override fun parse(s: String): Coordinate? {
        val latLng = checkRegex1(s) ?: checkRegex2(s) ?: return null

        return Coordinate.of(latLng)
    }
}
