package com.nujiak.recce.mapping

import com.google.android.gms.maps.model.LatLng
import com.nujiak.recce.enums.CoordinateSystem
import kotlin.math.floor
import kotlin.math.pow

/**
 * Extension function to create a string with all whitespaces removed
 */
private fun String.filterWhitespaces() = this.replace("\\s".toRegex(), "")

/**
 * Regex matching the MGRS format with all whitespaces trimmed
 *
 * Example of match: "48NUG123456123456"
 */
private val mgrsRegex = Regex("(^\\d{1,2})(\\w{3})(\\d{1,12})$")

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
private fun intsFromMgrsGridString(gridStr: String): Pair<Double, Double> {
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
    val eastingInt = easting.toInt() * 10.0.pow(5 - precision)
    val northingInt = northing.toInt() * 10.0.pow(5 - precision)
    return Pair(eastingInt, northingInt)
}

/**
 * Parses and segments a MGRS string
 *
 * @param mgrsString MGRS String
 * @return MgrsData containing the data of the point in MGRS
 */
fun parse(mgrsString: String): Coordinate? {
    val matchResult =
        mgrsRegex.find(mgrsString.filterWhitespaces().uppercase())

    matchResult ?: return null

    val matchGroupValues = matchResult.groupValues

    val gridString = matchGroupValues[3]
    if (gridString.length % 2 != 0) {
        return null
    }
    val (easting, northing) = intsFromMgrsGridString(gridString)
    val (band, eastingLtr, northingLtr) = matchGroupValues[2].toList()

    return parse(matchGroupValues[1].toInt(), band, eastingLtr, northingLtr, easting, northing)
}

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
fun getMgrsBand(latitude: Double): Char {
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

    val utmBand = if (mgrsBand in "NPQRSTUVWX") UtmBand.NORTH else UtmBand.SOUTH

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
        val utmCoord = Mapping.parseUtm(mgrsZone, utmBand, utmX, 2000000 * yBand + yPrelim) ?: continue
        val derivedBand = getMgrsBand(utmCoord.latLng.latitude)
        if (derivedBand == mgrsBand) {
            return Coordinate.of(utmCoord.latLng, mgrsZone, mgrsBand, columnLetter, rowLetter, x, y)
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
fun transformFromWgs84(latitude: Double, longitude: Double): Coordinate? {

    if (latitude < -84 || latitude > 84) {
        return null
    }

    // Convert to UTM
    val utmCoord = Mapping.transformTo(CoordinateSystem.UTM, LatLng(latitude, longitude)) ?: return null
    val (utmZone, utmBand) = getUtmZoneAndBand(latitude, longitude)

    // Use UTM zone and easting/northing to get the MGRS column/row letters
    val columnLetter = getMgrsColumnLetter(utmCoord.x, utmZone)
    val rowLetter = getMgrsRowLetter(utmCoord.y, utmZone)

    // Use the WGS 84 latitude to find the MGRS band
    val band = getMgrsBand(latitude)

    // Find (x mod 10000) and (y mod 100000)
    val x = utmCoord.x % 100000
    val y = utmCoord.y % 100000

    return Coordinate.of(LatLng(latitude, longitude), utmZone, band, columnLetter, rowLetter, x, y)
}
