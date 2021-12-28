package com.nujiak.recce.mapping.parsers

import com.google.android.gms.maps.model.LatLng
import com.nujiak.recce.mapping.Coordinate

/**
 * Parses WGS 84 coordinates
 */
object Wgs84Parser : Parser {
    override fun parse(s: String): Coordinate? {
        val groups = s.trim().split("[,;\\s]+".toRegex())
        if (groups.size != 2) {
            return null
        }

        val latitude = groups[0].toDoubleOrNull() ?: return null
        val longitude = groups[1].toDoubleOrNull() ?: return null

        val latLng = LatLng(latitude, longitude)

        return Coordinate.of(latLng)
    }
}
