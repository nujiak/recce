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
            CoordinateSystem.UTM -> UtmUtils.transform(latLng)
            CoordinateSystem.MGRS -> MgrsUtils.transform(latLng)
            CoordinateSystem.KERTAU -> KertauUtils.transform(latLng)
            CoordinateSystem.BNG -> BngUtils.transform(latLng)
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
}
