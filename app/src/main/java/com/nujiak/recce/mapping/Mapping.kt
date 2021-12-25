package com.nujiak.recce.mapping

import com.google.android.gms.maps.model.LatLng
import com.nujiak.recce.enums.CoordinateSystem
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateReferenceSystem
import org.locationtech.proj4j.CoordinateTransform
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate

private const val EPSG_CODE_WGS_84: Short = 4326

private const val KERTAU_1948_NAME = "Kertau 1948"
private const val KERTAU_1948_PROJ_STRING = "+proj=omerc +lat_0=4 +lonc=102.25 +alpha=323.0257905 +k=0.99984 +x_0=804670.24 +y_0=0 +no_uoff +gamma=323.1301023611111 +a=6377295.664 +b=6356094.667915204 +units=m +no_defs +towgs84=-11,851,5"

private val crsFactory = CRSFactory()
private val ctFactory = CoordinateTransformFactory()

private val wgs84Crs: CoordinateReferenceSystem by lazy {
    crsFactory.createFromName("EPSG:$EPSG_CODE_WGS_84")
}
private val kertau1948Crs: CoordinateReferenceSystem by lazy {
    crsFactory.createFromParameters(KERTAU_1948_NAME, KERTAU_1948_PROJ_STRING)
}

private val kertau1948ToWgs84Transform: CoordinateTransform by lazy {
    ctFactory.createTransform(kertau1948Crs, wgs84Crs)
}

private val wgs84ToKertau1948Transform: CoordinateTransform by lazy {
    ctFactory.createTransform(wgs84Crs, kertau1948Crs)
}

private val utmToWgs84Transforms = HashMap<Short, CoordinateTransform>()
private val wgs84ToUtmTransforms = HashMap<Short, CoordinateTransform>()

operator fun LatLng.component1() = this.latitude
operator fun LatLng.component2() = this.longitude

/**
 * Contains functions to convert between difference coordinate reference systems using the EPSG code
 * as reference
 */
class Mapping {

    companion object {

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
         * Transforms a [Coordinate] to another in the [coordinateSystem]
         *
         * @param coordinateSystem
         * @param coordinate
         * @return
         */
        fun transformTo(coordinateSystem: CoordinateSystem, coordinate: Coordinate): Coordinate? {
            return transformTo(coordinateSystem, coordinate.latLng)
        }

        /**
         * Converts a [LatLng] to a [Coordinate] representing the same location
         *
         * @param latLng
         * @return
         */
        fun parseLatLng(latLng: LatLng): Coordinate {
            return Coordinate.of(latLng)
        }

        private fun toKertau1948(latLng: LatLng): Coordinate? {

            with(latLng) {
                if (latitude < 1.12 || latitude > 6.72 || longitude < 99.59 || longitude > 104.6) {
                    return null
                }
            }

            val sourceCoord = latLng.toProjCoordinate()
            val resultCoord = ProjCoordinate()

            wgs84ToKertau1948Transform.transform(sourceCoord, resultCoord)

            return Coordinate.of(latLng, resultCoord.x, resultCoord.y)
        }

        /**
         * Parses Kertau 1948 easting and northing into a [Coordinate]
         *
         * @param x easting
         * @param y northing
         * @return
         */
        fun parseKertau1948(x: Double, y: Double): Coordinate {
            val sourceCoord = ProjCoordinate(x, y)
            val resultCoord = ProjCoordinate()

            kertau1948ToWgs84Transform.transform(sourceCoord, resultCoord)

            val latLng = LatLng(resultCoord.y, resultCoord.x)

            return Coordinate.of(latLng, x, y)
        }

        private fun toUtm(latLng: LatLng): Coordinate? {
            return toUtm(latLng.latitude, latLng.longitude)
        }

        private fun toUtm(latitude: Double, longitude: Double): Coordinate? {
            if (latitude < -80 || latitude > 84) {
                return null
            }
            val sourceCoord = ProjCoordinate(longitude, latitude)
            val resultCoord = ProjCoordinate()

            val (zone, band) = getUtmZoneAndBand(latitude, longitude)

            val epsgCode = getUtmEpsgCode(zone, band)

            getWgs84ToUtmTransform(epsgCode).transform(sourceCoord, resultCoord)

            return Coordinate.of(LatLng(latitude, longitude), zone, band, resultCoord.x, resultCoord.y)
        }

        /**
         * Parses UTM data into a [Coordinate]
         *
         * @param zone
         * @param band
         * @param x easting
         * @param y northing
         * @return
         */
        fun parseUtm(zone: Int, band: Char, x: Double, y: Double): Coordinate? {
            val utmBand = when (band) {
                'N' -> UtmBand.NORTH
                'S' -> UtmBand.SOUTH
                else -> throw IllegalArgumentException("UTM band must be 'N' or 'S': $band")
            }
            return parseUtm(zone, utmBand, x, y)
        }

        /**
         * Parses UTM data into a [Coordinate]
         *
         * @param zone
         * @param band
         * @param x easting
         * @param y northing
         * @return
         */
        fun parseUtm(zone: Int, band: UtmBand, x: Double, y: Double): Coordinate? {
            val sourceCoord = ProjCoordinate(x, y)
            val resultCoord = ProjCoordinate()

            val epsgCode = getUtmEpsgCode(zone, band)

            getUtmToWgs84Transform(epsgCode).transform(sourceCoord, resultCoord)

            val latLng = LatLng(resultCoord.y, resultCoord.x)

            if (latLng.latitude < -80 || latLng.latitude > 84) {
                return null
            }

            return Coordinate.of(latLng, zone, band, x, y)
        }

        private fun getUtmToWgs84Transform(epsgCode: Short): CoordinateTransform {
            if (epsgCode !in 32601..32660 && epsgCode !in 32701..32760) {
                throw IllegalArgumentException("EPSG code is out of range for UTM: $epsgCode")
            }
            if (!utmToWgs84Transforms.containsKey(epsgCode)) {
                val utmCrs = crsFactory.createFromName("EPSG:$epsgCode")
                val transform = ctFactory.createTransform(utmCrs, wgs84Crs)
                utmToWgs84Transforms[epsgCode] = transform
            }

            return utmToWgs84Transforms[epsgCode]!!
        }

        private fun getWgs84ToUtmTransform(epsgCode: Short): CoordinateTransform {
            if (epsgCode !in 32601..32660 && epsgCode !in 32701..32760) {
                throw IllegalArgumentException("EPSG code is out of range for UTM: $epsgCode")
            }

            if (!wgs84ToUtmTransforms.containsKey(epsgCode)) {
                val utmCrs = crsFactory.createFromName("EPSG:$epsgCode")
                val transform = ctFactory.createTransform(wgs84Crs, utmCrs)
                wgs84ToUtmTransforms[epsgCode] = transform
            }

            return wgs84ToUtmTransforms[epsgCode]!!
        }

        private fun toMgrs(latLng: LatLng): Coordinate? {
            return toMgrs(latLng.latitude, latLng.longitude)
        }

        private fun toMgrs(latitude: Double, longitude: Double): Coordinate? {
            return transformFromWgs84(latitude, longitude)
        }

        /**
         * Parses MGRS data in a [Coordinate]
         *
         * @param zone
         * @param band
         * @param eastingLetter
         * @param northingLetter
         * @param x
         * @param y
         * @return
         */
        fun parseMgrs(
            zone: Int,
            band: Char,
            eastingLetter: Char,
            northingLetter: Char,
            x: Double,
            y: Double
        ): Coordinate? {
            return parse(zone, band, eastingLetter, northingLetter, x, y)
        }

        /**
         * Parses an MGRS string into a [Coordinate]
         *
         * @param mgrsString
         * @return
         */
        fun parseMgrs(mgrsString: String): Coordinate? {
            return parse(mgrsString)
        }

        private fun LatLng.toProjCoordinate(): ProjCoordinate {
            return ProjCoordinate(this.longitude, this.latitude)
        }
    }
}
