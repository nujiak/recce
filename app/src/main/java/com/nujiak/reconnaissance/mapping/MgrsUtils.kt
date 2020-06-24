package com.nujiak.reconnaissance.mapping

import java.util.*
import kotlin.math.floor
import kotlin.math.pow

/**
 * Extension function to create a string with all whitespaces removed
 */
private fun String.filterWhitespaces() = this.replace("\\s".toRegex(), "")

/**
 * Encapsulates the data of a point in MGRS
 *
 * @property zone UTM zone of the point
 * @property band UTM latitude band of the point
 * @property eastingLetter MGRS easting letter denoting 100,000m square
 * @property northingLetter MGRS northing letter denoting 100,000m square
 * @property x Easting of the point
 * @property y Northing of the point
 */
data class MgrsData(
    val zone: Int,
    val band: Char,
    val eastingLetter: Char,
    val northingLetter: Char,
    val x: Double,
    val y: Double
)

/**
 * Extension function to print MgrsData as a single-line string
 *
 * @param precision Number of digits per axes
 * @param includeWhitespace Whether to insert a space to segment the string
 *
 * @return Single-line string of MGRS
 *
 * @throws IllegalArgumentException if precision is not larger than 0
 */
fun MgrsData.toSingleLine(precision: Int = 5, includeWhitespace: Boolean = false): String {
    if (precision <= 0) {
        throw IllegalArgumentException("Precision must be more than 0: $precision)")
    }
    val x = this.x * 10.0.pow(precision - 5)
    val y = this.y * 10.0.pow(precision - 5)
    return this.let {
        "${it.zone.toString().padStart(2, '0')}${it.band}${it.eastingLetter}${it.northingLetter}" +
                "${if (includeWhitespace) " " else ""}${x.toInt().toString().padStart(precision, '0')}" +
                "${if (includeWhitespace) " " else ""}${y.toInt().toString().padStart(precision, '0')}"
    }
}

fun MgrsData.toUtmData() = getUtmFromMgrs(this)
/**
 * Regex matching the MGRS format with all whitespaces trimmed
 *
 * Example of match: "48NUG123456123456"
 */
private val mgrsRegex = Regex("(^\\d{1,2})(\\w{3})(\\d{1,12})$")

/**
 * Takes a string containing an even number of numerical digits, splits it into 2 numbers
 * and adjusts the decimal point of each number to have 5 digits before the dp
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
 * Uses regex to parse and segment an MGRS string
 *
 * @param mgrsString MGRS String
 * @return MgrsData containing the data of the point in MGRS
 */
fun parseMgrsFrom(mgrsString: String): MgrsData? {
    val matchResult = mgrsRegex.find(mgrsString.filterWhitespaces().toUpperCase(Locale.getDefault()))
    if (matchResult != null) {
        val matchGroupValues = matchResult.groupValues

        val gridString = matchGroupValues[3]
        if (gridString.length % 2 != 0) {
            return null
        }
        val (easting, northing) = intsFromMgrsGridString(gridString)
        val (band, eastingLtr, northingLtr) = matchGroupValues[2].toList()
        return MgrsData(
            matchGroupValues[1].toInt(),
            band,
            eastingLtr,
            northingLtr,
            easting,
            northing
        )
    } else {
        return null
    }
}

/*
 Arrays of MGRS Easting Letters as defined by both lettering schemes AA and AL. The ending integer
 denotes |Z| % 3 where Z is the UTM zone.
 */
private val eastingLetters1 = arrayOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H')
private val eastingLetters2 = arrayOf('J', 'K', 'L', 'M', 'N', 'P', 'Q', 'R')
private val eastingLetters0 = arrayOf('S', 'T', 'U', 'V', 'X', 'W', 'Y', 'Z')

/*
 Arrays of MGRS Northing Letters according to lettering scheme AA. The odd/even refers to
 the parity of the UTM zone.
 */
private val aaNorthingLettersOdd = arrayOf(
    'A', 'B', 'C', 'D', 'E',
    'F', 'G', 'H', 'J', 'K',
    'L', 'M', 'N', 'P', 'Q',
    'R', 'S', 'T', 'U', 'V'
)
private val aaNorthingLettersEven = arrayOf(
    'F', 'G', 'H', 'J', 'K',
    'L', 'M', 'N', 'P', 'Q',
    'R', 'S', 'T', 'U', 'V',
    'A', 'B', 'C', 'D', 'E'
)

/**
 * Returns MGRS band given latitude. Note that this differs from UTM bands.
 *
 * This function follows section 11.13 of <<The Universal Grids and the Transverse Mercator and Polar
 * Stereographic Map Projections>>.
 *
 * @param latDeg Latitude in degrees
 * @return Char of the corresponding MGRS latitude band
 */
private fun getMgrsBand(latDeg: Double): Char {
    return when (floor(latDeg / 8).toInt()) {
        -11 -> 'C'
        -10 -> 'C'
        -9 -> 'D'
        -8 -> 'E'
        -7 -> 'F'
        -6 -> 'G'
        -5 -> 'H'
        -4 -> 'J'
        -3 -> 'K'
        -2 -> 'L'
        -1 -> 'M'
        0 -> 'N'
        1 -> 'P'
        2 -> 'Q'
        3 -> 'R'
        4 -> 'S'
        5 -> 'T'
        6 -> 'U'
        7 -> 'V'
        8 -> 'W'
        9 -> 'X'
        10 -> 'X'
        else -> throw IllegalArgumentException("lat is outside UTM bounds: $latDeg")
    }
}

/**
 * Returns a MgrsData corresponding to a point in UTM.
 *
 * @param utmData UtmData of a point
 *
 * @return MgrsData containing the data of the point if the point lies within the area of usage
 *         of UTM (-80 <= latDeg <= 84)
 * @return null if the point lies outside the area of usage of UTM
 */
fun getMgrsData(utmData: UtmData?): MgrsData? {
    utmData ?: return null

    val eastingConversion = floor(utmData.x / 100000).toInt()
    val northingConversion = floor((utmData.y % 2000000) / 100000).toInt()

    val eastingLetter = when (utmData.zone % 3) {
        1 -> eastingLetters1[eastingConversion - 1]
        2 -> eastingLetters2[eastingConversion - 1]
        else -> eastingLetters0[eastingConversion - 1]
    }

    val northingLetter = when (utmData.zone % 2) {
        1 -> aaNorthingLettersOdd[northingConversion]
        else -> aaNorthingLettersEven[northingConversion]
    }

    val lat = utmData.toLatLng().first
    val band = getMgrsBand(lat)

    val x = utmData.x % 100000
    val y = utmData.y % 100000

    return MgrsData(
        zone = utmData.zone,
        band = band,
        eastingLetter = eastingLetter,
        northingLetter = northingLetter,
        x = x,
        y = y
    )
}

/**
 * Same as [getMgrsData] but takes in WGS 84 latitude and longitude.
 *
 * @param latDeg Latitude in degrees
 * @param lngDeg Longitude in degrees
 */
fun getMgrsData(latDeg: Double, lngDeg: Double): MgrsData? {
    return getMgrsData(getUtmData(latDeg, lngDeg))
}

/**
 * Same as [getMgrsData] but takes in a pair of Doubles as WGS 84  latitude and longitude.
 *
 * @param latLng Latitude and longitude in degrees as a Pair of Doubles
 */
fun getMgrsData(latLng: Pair<Double, Double>?): MgrsData? {
    latLng ?: return null
    return getMgrsData(latLng.first, latLng.second)
}

/*
 A list of possible yBands associated with a MGRS band.
 */
private val Y_BANDS = mapOf(
    'C' to arrayOf(1, 0),
    'D' to arrayOf(1, 0),
    'E' to arrayOf(1, 1),
    'F' to arrayOf(2, 1),
    'G' to arrayOf(2, 2),
    'H' to arrayOf(3, 2),
    'J' to arrayOf(3, 3),
    'K' to arrayOf(4, 3),
    'L' to arrayOf(4, 4),
    'M' to arrayOf(4, 4),
    'N' to arrayOf(0, 0),
    'P' to arrayOf(0, 0),
    'Q' to arrayOf(0, 1),
    'R' to arrayOf(1, 1),
    'S' to arrayOf(1, 2),
    'T' to arrayOf(2, 2),
    'U' to arrayOf(2, 3),
    'V' to arrayOf(3, 3),
    'W' to arrayOf(3, 4),
    'X' to arrayOf(3, 4)
)

/**
 * Returns a UtmData corresponding to a given MgrsData
 *
 * @param mgrsData MgrsData of a point
 * @return UtmData corresponding to the point
 * @return null if [mgrsData] is null
 */
fun getUtmFromMgrs(mgrsData: MgrsData?): UtmData? {
    mgrsData ?: return null

    val isNorth = "NPQRSTUVWX".contains(mgrsData.band)

    val xLetter = when (mgrsData.zone % 3) {
        1 -> eastingLetters1.indexOf(mgrsData.eastingLetter) + 1
        2 -> eastingLetters2.indexOf(mgrsData.eastingLetter) + 1
        else -> eastingLetters0.indexOf(mgrsData.eastingLetter) + 1
    }
    if (xLetter == -1) {
        throw IllegalArgumentException("MGRS band is invalid: ${mgrsData.band}")
    }
    val yLetter = when (mgrsData.zone % 2) {
        0 -> aaNorthingLettersEven.indexOf(mgrsData.northingLetter)
        else -> aaNorthingLettersOdd.indexOf(mgrsData.northingLetter)
    }
    if (yLetter == -1) {
        throw IllegalArgumentException("MGRS zone is invalid: ${mgrsData.zone}")
    }

    val x = 100000 * xLetter + mgrsData.x
    val yPrelim = 100000 * yLetter + mgrsData.y

    for (yBand in requireNotNull(Y_BANDS[mgrsData.band])) {
        val utmData = UtmData(
            x = x,
            y = 2000000 * yBand + yPrelim,
            zone = mgrsData.zone,
            band = if (isNorth) NORTH_BAND else SOUTH_BAND
        )
        if (getMgrsBand(utmData.toLatLng().first) == mgrsData.band) {
            return utmData
        }
    }
    throw Exception("No suitable y_band was found")
}