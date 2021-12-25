package com.nujiak.recce.mapping

import com.nujiak.recce.utils.wrapLngDeg
import kotlin.math.floor

/*
 Constant chars for north and south bands
 */
const val NORTH_BAND = 'N'
const val SOUTH_BAND = 'S'

enum class UtmBand(val letter: Char) {
    NORTH(NORTH_BAND),
    SOUTH(SOUTH_BAND);

    override fun toString(): String {
        return this.letter.toString()
    }
}

/**
 * Array of strings of UTM zone and band configurations. This only accounts for
 * the 2 major bands N and S.
 */
val ZONE_BANDS = List(120) { index ->
    if (index % 2 == 0) {
        "%.0f".format(floor(index / 2.0) + 1) + NORTH_BAND
    } else {
        "%.0f".format(floor(index / 2.0) + 1) + SOUTH_BAND
    }
}

fun getUtmEpsgCode(zone: Int, band: UtmBand): Short {
    if (zone < 1 || zone > 60) {
        throw IllegalArgumentException("UTM zone must be in the range [1, 60]: $zone")
    }

    return when (band) {
        UtmBand.NORTH -> (zone + 32600).toShort()
        UtmBand.SOUTH -> (zone + 32700).toShort()
    }
}

/**
 * Helper function that returns only the zone of a given WGS 84 longitude.
 *
 * @param longitude Longitude in degrees
 * @return UTM zone as an integer
 */
private fun getUtmZone(longitude: Double): Int {
    val lng = wrapLngDeg(longitude)
    return floor((lng + 180) / 6).toInt() + 1
}

/**
 * Returns the UTM zone and band given the latitude and longitude of the point.
 *
 * @param latitude WGS 84 latitude of the point in degrees
 * @param longitude WGS 84 longitude of the point in degrees
 *
 * @return Pair containing the zone and band of the point as the first and second object
 *         respectively.
 */
fun getUtmZoneAndBand(latitude: Double, longitude: Double): Pair<Int, UtmBand> {
    val band = if (latitude >= 0) UtmBand.NORTH else UtmBand.SOUTH
    val zone = getUtmZone(longitude)

    return Pair(zone, band)
}
