package com.nujiak.recce.mapping

import androidx.core.text.isDigitsOnly
import com.google.android.gms.maps.model.LatLng
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.utils.wrapLngDeg
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateReferenceSystem
import org.locationtech.proj4j.CoordinateTransform
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import kotlin.math.floor
import kotlin.math.pow

/**
 * Extension function to create a string with all whitespaces removed
 */
fun String.filterWhitespaces() = this.replace("\\s".toRegex(), "")

/**
 * Splits a string of numerical digits into 2 decimals.
 *
 * This adjusts the decimal point of each number to have 5 digits before the decimal point.
 *
 * E.g "1054" -> Pair(10000.0, 54000.0)
 *     "4003204992" -> Pair(40032.0, 4992.0)
 *     "200302503396" -> Pair(20030.2, 503396)
 *     "2039948510028493" -> Pair(20399.485, 10028.493)
 *
 * @param gridStr MGRS grid string containing even number of numerical digits
 * @return Pair containing the first (easting) and second (northing) number sliced
 *
 * @throws IllegalArgumentException if the string has an odd number length or contains non-digits.
 */
fun intsFromGridString(gridStr: String, magnitude: Int = 5): Pair<Double, Double>? {
    if (gridStr.length % 2 == 1 || !gridStr.isDigitsOnly()) {
        return null
    }

    val initialLength = gridStr.length
    if (initialLength % 2 != 0) {
        throw IllegalArgumentException("Length of gridStr ($gridStr) must be even.")
    }
    if (gridStr.matches("\\D".toRegex())) {
        throw IllegalArgumentException("gridStr should contain only numerical digits.")
    }
    val easting = gridStr.slice(0 until initialLength / 2)
    val northing = gridStr.slice(initialLength / 2 until initialLength)

    val precision = initialLength / 2
    val eastingInt = easting.toInt() * 10.0.pow(magnitude - precision)
    val northingInt = northing.toInt() * 10.0.pow(magnitude - precision)
    return Pair(eastingInt, northingInt)
}

object MgrsUtils {

    /**
     * MGRS Easting letters according to both AA and AL lettering schemes.
     */
    private val columnLetters = arrayOf(
        arrayOf('S', 'T', 'U', 'V', 'X', 'W', 'Y', 'Z'),
        arrayOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'),
        arrayOf('J', 'K', 'L', 'M', 'N', 'P', 'Q', 'R'),
    )

    /**
     * MGRS Northing Letters according to the AA lettering scheme.
     */
    private val rowLetters = arrayOf(
        arrayOf(
            'F', 'G', 'H', 'J', 'K',
            'L', 'M', 'N', 'P', 'Q',
            'R', 'S', 'T', 'U', 'V',
            'A', 'B', 'C', 'D', 'E'
        ),
        arrayOf(
            'A', 'B', 'C', 'D', 'E',
            'F', 'G', 'H', 'J', 'K',
            'L', 'M', 'N', 'P', 'Q',
            'R', 'S', 'T', 'U', 'V'
        ),
    )

    /**
     * A list of possible yBands associated with a MGRS band.
     */
    private val Y_BANDS = mapOf(
        'C' to arrayOf(1, 0),
        'D' to arrayOf(1, 0),
        'E' to arrayOf(1),
        'F' to arrayOf(2, 1),
        'G' to arrayOf(2),
        'H' to arrayOf(3, 2),
        'J' to arrayOf(3),
        'K' to arrayOf(4, 3),
        'L' to arrayOf(4),
        'M' to arrayOf(4, 4),
        'N' to arrayOf(0),
        'P' to arrayOf(0),
        'Q' to arrayOf(0, 1),
        'R' to arrayOf(1),
        'S' to arrayOf(1, 2),
        'T' to arrayOf(2),
        'U' to arrayOf(2, 3),
        'V' to arrayOf(3),
        'W' to arrayOf(3, 4),
        'X' to arrayOf(3, 4)
    )

    /**
     * Returns MGRS band given latitude. Note that this differs from UTM bands.
     *
     * This function follows section 11.13 of <<The Universal Grids and the Transverse Mercator and Polar
     * Stereographic Map Projections>>.
     *
     * @param latitude Latitude in degrees
     * @return [Char] of the corresponding MGRS latitude band
     */
    private fun getMgrsBand(latitude: Double): Char {
        return when {
            latitude < -80 -> throw IllegalArgumentException("Latitude must be between -80 and 84: $latitude")
            latitude < -72 -> 'C'
            latitude < -64 -> 'D'
            latitude < -56 -> 'E'
            latitude < -48 -> 'F'
            latitude < -40 -> 'G'
            latitude < -32 -> 'H'
            latitude < -24 -> 'J'
            latitude < -16 -> 'K'
            latitude < -8 -> 'L'
            latitude < 0 -> 'M'
            latitude < 8 -> 'N'
            latitude < 16 -> 'P'
            latitude < 24 -> 'Q'
            latitude < 32 -> 'R'
            latitude < 40 -> 'S'
            latitude < 48 -> 'T'
            latitude < 56 -> 'U'
            latitude < 64 -> 'V'
            latitude < 72 -> 'W'
            latitude < 84 -> 'X'
            else -> throw IllegalArgumentException("Latitude must be between -80 and 84: $latitude")
        }
    }

    /**
     * Returns the MGRS column letter for a given easting
     *
     * @param x
     * @param utmZone
     * @return MGRS column letter for the given easting
     */
    private fun getMgrsColumnLetter(x: Double, utmZone: Int): Char {
        val eastingConversion = floor(x / 100000).toInt()
        return columnLetters[utmZone % 3][eastingConversion - 1]
    }

    /**
     * Returns the MGRS row letter for a given northing
     *
     * @param y
     * @param utmZone
     * @return MGRS row letter for the given northing
     */
    private fun getMgrsRowLetter(y: Double, utmZone: Int): Char {
        val northingConversion = floor((y % 2000000) / 100000).toInt()
        return rowLetters[utmZone % 2][northingConversion]
    }

    /**
     * Parses MGRS details into a [Coordinate]
     *
     * @param mgrsZone
     * @param mgrsBand
     * @param columnLetter
     * @param rowLetter
     * @param x
     * @param y
     * @return [Coordinate] representing the location in MGRS
     */
    fun parse(mgrsZone: Int, mgrsBand: Char, columnLetter: Char, rowLetter: Char, x: Double, y: Double): Coordinate? {

        val utmBand = if (mgrsBand in "NPQRSTUVWX") UtmUtils.UtmBand.NORTH else UtmUtils.UtmBand.SOUTH

        val xLetter = columnLetters[mgrsZone % 3].indexOf(columnLetter) + 1
        if (xLetter <= 0) {
            // Invalid column letter
            return null
        }

        val yLetter = rowLetters[mgrsZone % 2].indexOf(rowLetter)
        if (yLetter == -1) {
            // Invalid row letter
            return null
        }

        val utmX = 100000 * xLetter + x
        val yPrelim = 100000 * yLetter + y

        for (yBand in requireNotNull(Y_BANDS[mgrsBand])) {
            val utmCoord = UtmUtils.parse(mgrsZone, utmBand, utmX, 2000000 * yBand + yPrelim) ?: continue
            val derivedBand = getMgrsBand(utmCoord.latLng.latitude)
            if (derivedBand == mgrsBand) {
                return Coordinate.of(
                    utmCoord.latLng,
                    mgrsZone,
                    mgrsBand,
                    columnLetter,
                    rowLetter,
                    x,
                    y
                )
            }
        }
        return null
    }

    /**
     * Returns a [Coordinate] representing a WGS 84 coordinate in MGRS
     *
     * @param latitude
     * @param longitude
     * @return [Coordinate] representing the location in MGRS
     */
    fun transform(latLng: LatLng): Coordinate? {

        val latitude = latLng.latitude
        val longitude = latLng.longitude

        if (latitude < -84 || latitude > 84) {
            return null
        }

        // Convert to UTM
        val utmCoord = Mapping.transformTo(CoordinateSystem.UTM, LatLng(latitude, longitude)) ?: return null
        val (utmZone, utmBand) = UtmUtils.getUtmZoneAndBand(latitude, longitude)

        // Use UTM zone and easting/northing to get the MGRS column/row letters
        val columnLetter = getMgrsColumnLetter(utmCoord.x, utmZone)
        val rowLetter = getMgrsRowLetter(utmCoord.y, utmZone)

        // Use the WGS 84 latitude to find the MGRS band
        val band = getMgrsBand(latitude)

        // Find (x mod 10000) and (y mod 100000)
        val x = utmCoord.x % 100000
        val y = utmCoord.y % 100000

        return Coordinate.of(
            LatLng(latitude, longitude),
            utmZone,
            band,
            columnLetter,
            rowLetter,
            x,
            y
        )
    }
}

object KertauUtils {

    private const val EPSG_CODE_WGS_84: Short = 4326

    private val crsFactory = CRSFactory()
    private val ctFactory = CoordinateTransformFactory()

    private const val KERTAU_1948_NAME = "Kertau 1948"
    private const val KERTAU_1948_PROJ_STRING = "+proj=omerc +lat_0=4 +lonc=102.25 +alpha=323.0257905 +k=0.99984 +x_0=804670.24 +y_0=0 +no_uoff +gamma=323.1301023611111 +a=6377295.664 +b=6356094.667915204 +units=m +no_defs +towgs84=-11,851,5"

    private val wgs84Crs: CoordinateReferenceSystem by lazy {
        crsFactory.createFromName("EPSG:$EPSG_CODE_WGS_84")
    }

    private val kertau1948Crs: CoordinateReferenceSystem by lazy {
        crsFactory.createFromParameters(
            KERTAU_1948_NAME,
            KERTAU_1948_PROJ_STRING
        )
    }

    private val wgs84ToKertau1948Transform: CoordinateTransform by lazy {
        ctFactory.createTransform(wgs84Crs, kertau1948Crs)
    }

    private val kertau1948ToWgs84Transform: CoordinateTransform by lazy {
        ctFactory.createTransform(kertau1948Crs, wgs84Crs)
    }

    fun transform(latLng: LatLng): Coordinate? {
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

    private fun LatLng.toProjCoordinate(): ProjCoordinate {
        return ProjCoordinate(this.longitude, this.latitude)
    }

    fun parse(easting: Double, northing: Double): Coordinate? {
        val sourceCoord = ProjCoordinate(easting, northing)
        val resultCoord = ProjCoordinate()

        kertau1948ToWgs84Transform.transform(sourceCoord, resultCoord)

        val latLng = LatLng(resultCoord.y, resultCoord.x)

        return Coordinate.of(latLng, easting, northing)
    }
}

object UtmUtils {
    /**
     * Character representing the north band
     */
    const val NORTH_BAND = 'N'

    /**
     * Character representing the south band
     */
    const val SOUTH_BAND = 'S'

    enum class UtmBand(val letter: Char) {
        NORTH(NORTH_BAND),
        SOUTH(SOUTH_BAND);

        override fun toString(): String {
            return this.letter.toString()
        }
    }

    private val utmToWgs84Transforms = HashMap<Short, CoordinateTransform>()
    private val wgs84ToUtmTransforms = HashMap<Short, CoordinateTransform>()

    private val crsFactory = CRSFactory()
    private val ctFactory = CoordinateTransformFactory()

    private const val EPSG_CODE_WGS_84: Short = 4326

    private val wgs84Crs: CoordinateReferenceSystem by lazy {
        crsFactory.createFromName("EPSG:$EPSG_CODE_WGS_84")
    }

    private fun getUtmEpsgCode(zone: Int, band: UtmBand): Short {
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

    /**
     * Parses UTM data into a [Coordinate]
     *
     * @param zone
     * @param band
     * @param x easting
     * @param y northing
     * @return
     */
    fun parse(zone: Int, band: UtmBand, x: Double, y: Double): Coordinate? {
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

    fun transform(latLng: LatLng): Coordinate? {

        val latitude = latLng.latitude
        val longitude = latLng.longitude

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
}
