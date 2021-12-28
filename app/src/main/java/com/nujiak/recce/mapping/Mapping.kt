package com.nujiak.recce.mapping

import com.google.android.gms.maps.model.LatLng
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.mapping.parsers.Parser

/**
 * Contains functions to convert between difference coordinate reference systems using the EPSG code
 * as reference
 */
object Mapping {
    /**
     * Transforms a WGS 84 coordinate to a coordinate system in [CoordinateSystem]
     *
     * @param coordinateSystem
     * @param latLng
     * @return
     */
    fun transformTo(coordinateSystem: CoordinateSystem, latLng: LatLng): Coordinate? {
        return when (coordinateSystem) {
            CoordinateSystem.WGS84 -> Coordinate.of(latLng)
            CoordinateSystem.UTM -> toUtm(latLng)
            CoordinateSystem.MGRS -> toMgrs(latLng)
            CoordinateSystem.KERTAU -> toKertau1948(latLng)
        }
    }

    /**
     * Parses an input coordinate string to a [Coordinate]
     *
     * @param s input coordinate
     * @param coordinateSystem
     * @return [Coordinate] if the string is valid, null if not
     */
    fun parse(s: String, coordinateSystem: CoordinateSystem): Coordinate? {
        val parser = Parser.getParser(coordinateSystem)
        return parser.parse(s)
    }

    private fun toKertau1948(latLng: LatLng): Coordinate? {
        return KertauUtils.toKertau1948(latLng)
    }

    private fun toUtm(latLng: LatLng): Coordinate? {
        return this.toUtm(latLng.latitude, latLng.longitude)
    }

    private fun toUtm(latitude: Double, longitude: Double): Coordinate? {
        return UtmUtils.transform(latitude, longitude)
    }

    private fun toMgrs(latLng: LatLng): Coordinate? {
        return this.toMgrs(latLng.latitude, latLng.longitude)
    }

    private fun toMgrs(latitude: Double, longitude: Double): Coordinate? {
        return MgrsUtils.transformFromWgs84(latitude, longitude)
    }
}
