package com.nujiak.reconnaissance.mapping.utm

import com.nujiak.reconnaissance.cot
import com.nujiak.reconnaissance.degToRad
import com.nujiak.reconnaissance.mapping.WgsParams
import com.nujiak.reconnaissance.radToDeg
import com.nujiak.reconnaissance.wrapLngDeg
import kotlin.math.*

/**
 * Encapsulates the data of a point in UTM
 *
 * @property x Easting of the point
 * @property y Northing of the point
 * @property zone UTM zone of the point
 * @property band UTM latitude band of the point
 */
data class UtmData(val x: Double, val y: Double, val zone: Int, val band: String)

/* WGS Constants */

private val A_AXIS = WgsParams.A_AXIS
private val B_AXIS = WgsParams.B_AXIS
private val F_INVERSE = WgsParams.F_INVERSE
private val E = WgsParams.E
private val E_SQR = WgsParams.E_SQR
private val R4 = WgsParams.R4
private val A2 = WgsParams.A2
private val A4 = WgsParams.A4
private val A6 = WgsParams.A6
private val A8 = WgsParams.A8
private val A10 = WgsParams.A10
private val A12 = WgsParams.A12
private val B2 = WgsParams.B2
private val B4 = WgsParams.B4
private val B6 = WgsParams.B6
private val B8 = WgsParams.B8
private val B10 = WgsParams.B10
private val B12 = WgsParams.B12

/**
 * The resolution of the iterative calculation in chiToPhiIntermediate(). A larger integer
 * results in increased accuracy but also increased calculation times.
 */
private const val CHI_TO_PHI_RESOLUTION: Int = 4

/**
 * Array of strings of UTM latitude bands, starting from the south and heading northwards.
 */
private val LAT_BANDS = arrayOf(
    "C", "D", "E", "F", "G",
    "H", "J", "K", "L", "M",
    "N", "P", "Q", "R", "S",
    "T", "U", "V", "W", "X"
)

/**
 * Array of strings of UTM zone and band configurations. This only accounts for
 * the 2 major bands N and S.
 */
val ZONE_BANDS = List(120) { index ->
    if (index % 2 == 0) {
        "%.0fN".format(floor(index / 2.0) + 1)
    } else {
        "%.0fS".format(floor(index / 2.0) + 1)
    }
}

/**
 * Helper function that returns only the zone of a given WGS 84 longitude.
 */
private fun getUnadjustedUtmZone(longitude: Double): Int {
    val lng = wrapLngDeg(longitude)
    return floor((lng + 180) / 6).toInt() + 1
}

/**
 * Helper function that returns the naive latitude bands given a WGS 84 latitude which
 * does not account for exceptions. This function should NOT be used anywhere
 * except in getUtmZoneAndBand() as a helper function.
 *
 * @param latitude Latitude of the point in degrees
 * @return latBand Naive latitude band of the point
 */
private fun getUnadjustedUtmLatBand(latitude: Double): String {
    if (latitude < -80 || latitude > 84) {
        throw IllegalArgumentException("latitude ($latitude) must be -84.0 - 80")
    }
    val remappedLat = latitude + 80
    val index = when {
        remappedLat - 80 > 72 -> 19
        else -> floor(remappedLat / 8).toInt()
    }
    return LAT_BANDS[index]
}

/**
 * Returns the UTM zone and band given the latitude and longitude of the point. This function
 * accounts for the exceptions (such as the non-existence of 32X, 34X, etc). This function uses
 * getUnadjustedUtmLatBand() and getUnadjustedUtmZone() to obtain a naive latitude band and zone,
 * then subsequently accounts for the exceptions.
 *
 * This function also acts as a helper to getUtmData(), which should be used over this function
 * as the latter conducts sanity checks for whether the given point lies in
 * the area of usage of UTM.
 *
 * @param latitude WGS 84 latitude of the point in degrees
 * @param longitude WGS 84 longitude of the point in degrees
 *
 * @return Pair containing the zone and band of the point as the first and second object
 *         respectively.
 */
private fun getUtmZoneAndBand(latitude: Double, longitude: Double): Pair<Int, String> {
    val band =
        getUnadjustedUtmLatBand(latitude)
    val zone =
        getUnadjustedUtmZone(longitude)

    return when {
        band == "X" && zone == 32 -> Pair(if (longitude < 9) 31 else 33, band)
        band == "X" && zone == 34 -> Pair(if (longitude < 21) 33 else 35, band)
        band == "X" && zone == 36 -> Pair(if (longitude < 24) 35 else 37, band)
        band == "V" && zone == 31 && longitude >= 3 -> Pair(32, band)
        else -> Pair(zone, band)
    }
}

/**
 * Converts geodetic latitude Phi to geocentric latitude Psi. This is the inverse
 * function of psiToPhi() below
 *
 * @param phi Geodetic latitude
 * @return psi Geocentric latitude
 */
private fun phiToPsi(phi: Double): Double {
    return when {
        (phi > PI / 4) -> PI / 2 - atan(cot(phi) / (1 - E_SQR))
        (-PI / 4 <= phi && phi <= PI / 4) -> atan((1 - E_SQR) * tan(phi))
        else -> -PI / 2 - atan(cot(phi) / (1 - E_SQR))
    }
}

/**
 * Converts geocentric latitude Psi to geodetic latitude Phi. This is the inverse
 * function of phiToPsi() above
 *
 * @param psi Geocentric latitude
 * @return phi Geodetic latitude
 */
private fun psiToPhi(psi: Double): Double {
    return when {
        (psi > PI / 4) -> PI / 2 - atan((1 - E_SQR) * cot(psi))
        (-PI / 4 <= psi && psi <= PI / 4) -> atan(tan(psi) / (1 - E_SQR))
        else -> -PI / 2 - atan((1 - E_SQR) * cot(psi))
    }
}

/**
 * Computes cosine and sine of conformal latitude Chi from geodetic latitude Phi. This is the
 * inverse function of chiToPhi() below.
 *
 * @param phi Geodetic latitude
 * @return chi: Pair<Double, Double> containing the cosine and sine of conformal latitude Chi as
 *              the first and second object respectively.
 */
private fun phiToChi(phi: Double): Pair<Double, Double> {
    val p = exp(
        E * atanh(
            E * sin(phi)
        )
    )

    val cosChi = 2 * cos(phi) / ((1 + sin(phi)) / p + (1 - sin(phi)) * p)
    val sinChi = (((1 + sin(phi)) / p) - (1 - sin(phi)) * p) /
            ((1 + sin(phi) / p) + (1 - sin(phi) * p))

    return Pair(cosChi, sinChi)
}

/**
 * Computes geodetic latitude Phi from cosine and sine of conformal latitude Chi. This is the
 * inverse function of phiToChi() above.
 *
 * @param chi: Pair<Double, Double>. containing the cosine and sine of conformal latitude Chi as
 *             the first and second object respectively.
 * @return phi: Geodetic latitude
 */
private fun chiToPhi(chi: Pair<Double, Double>): Double {
    val (cosChi, sinChi) = chi
    val (sinPhi, p) = chiToPhiIntermediate(sinChi)

    val cosPhi = (((1 + sinPhi) / p + (1 - sinPhi) * p) / 2) * cosChi

    return atan2(sinPhi, cosPhi)
}

/**
 * Helper function to perform the iterative calculation
 * of intermediate variables S and P for chiToPhi()
 *
 * @return s sin(phi)
 * @return p intermediate variable for calculation
 */
private fun chiToPhiIntermediate(sinChi: Double): Pair<Double, Double> {
    if (CHI_TO_PHI_RESOLUTION < 1) throw IllegalArgumentException(" Resolution must be an integer 1 or larger")

    var s = sinChi
    var p = exp(E * atanh(E * s))

    if (CHI_TO_PHI_RESOLUTION == 1) return Pair(s, p)
    for (n in 1..CHI_TO_PHI_RESOLUTION) {
        s = ((1 + sinChi) * p.pow(2) - (1 - sinChi)) /
                ((1 + sinChi) * p.pow(2) + (1 - sinChi))
        p = exp(E * atanh(E * s))
    }
    return Pair(s, p)
}

/**
 * Calculates Easting x and Northing y from longitude Lambda and latitude Phi. This is
 * the inverse function of getLambdaPhi() below.
 *
 * @param lambda Longitude of point
 * @param phi Geodetic latitude of point
 * @param lambdaCM Longitude of Central Meridian of the UTM Zone that contains the point
 * @param scaleCM The scale or magnification at the Central Meridian
 * @param xCM False Easting at the Central Meridian
 * @param yEQ False Northing at the equator
 *
 * @return Pair<Double, Double> containing the Easting x and Northing y as the first and second
 *         objects respectively.
 */
private fun getXY(
    lambda: Double,
    phi: Double,
    lambdaCM: Double = 0.0,
    scaleCM: Double = 1.0,
    xCM: Double = 0.0,
    yEQ: Double = 0.0
): Pair<Double, Double> {

    val lambdaLocal = lambda - lambdaCM

    val (cosChi, sinChi) = phiToChi(phi)
    val u = atanh(cosChi * sin(lambdaLocal))
    val v = atan2(sinChi, (cosChi * cos(lambdaLocal)))

    val cos2v = cos(2 * v)
    val sin2v = sin(2 * v)
    val cos4v = 2 * cos2v.pow(2) - 1
    val sin4v = 2 * cos2v * sin2v
    val cos6v = cos4v * cos2v - sin4v * sin2v
    val sin6v = cos4v * sin2v + cos2v * sin4v
    val cos8v = 2 * cos4v.pow(2) - 1
    val sin8v = 2 * cos4v * sin4v
    val cos10v = cos8v * cos2v - sin8v * sin2v
    val sin10v = cos8v * sin2v + cos2v * sin8v
    val cos12v = 2 * cos6v.pow(2) - 1
    val sin12v = 2 * cos6v * sin6v

    val cosh2u = cosh(2 * u)
    val sinh2u = sinh(2 * u)
    val cosh4u = 2 * cosh2u.pow(2) - 1
    val sinh4u = 2 * cosh2u * sinh2u
    val cosh6u = cosh2u * cosh4u + sinh2u * sinh4u
    val sinh6u = cosh4u * sinh2u + cosh2u * sinh4u
    val cosh8u = 2 * cosh4u.pow(2) - 1
    val sinh8u = 2 * cosh4u * sinh4u
    val cosh10u = cosh2u * cosh8u + sinh2u * sinh8u
    val sinh10u = cosh8u * sinh2u + cosh2u * sinh8u
    val cosh12u = 2 * cosh6u.pow(2) - 1
    val sinh12u = 2 * cosh6u * sinh6u

    val x = ((A12 * sinh12u * cos12v) +
            (A10 * sinh10u * cos10v) +
            (A8 * sinh8u * cos8v) +
            (A6 * sinh6u * cos6v) +
            (A4 * sinh4u * cos4v) +
            (A2 * sinh2u * cos2v) + u) *
            R4 * scaleCM + xCM

    val y = ((A12 * cosh12u * sin12v) +
            (A10 * cosh10u * sin10v) +
            (A8 * cosh8u * sin8v) +
            (A6 * cosh6u * sin6v) +
            (A4 * cosh4u * sin4v) +
            (A2 * cosh2u * sin2v) + v) *
            R4 * scaleCM + yEQ

    return Pair(x, y)
}

/**
 * Calculates  longitude Lambda and latitude Phi from Easting x and Northing y. This is
 * the inverse function of getXY() above.
 *
 * @param x Easting of point
 * @param y Northing of point
 * @param lambdaCM Longitude of Central Meridian of the UTM Zone that contains the point
 * @param scaleCM The scale or magnification at the Central Meridian
 * @param xCM False Easting at the Central Meridian
 * @param yEQ False Northing at the equator
 *
 * @return Pair containing the Easting x and Northing y as the first
 *         and second objects respectively.
 */
private fun getLambdaPhi(
    x: Double, y: Double,
    lambdaCM: Double = 0.0,
    scaleCM: Double = 1.0,
    xCM: Double = 0.0,
    yEQ: Double = 0.0
): Pair<Double, Double> {

    val xLocal = (x - xCM) / scaleCM
    val yLocal = (y - yEQ) / scaleCM

    val u = B12 * sinh(12 * xLocal / R4) * cos(12 * yLocal / R4) +
            B10 * sinh(10 * xLocal / R4) * cos(10 * yLocal / R4) +
            B8 * sinh(8 * xLocal / R4) * cos(8 * yLocal / R4) +
            B6 * sinh(6 * xLocal / R4) * cos(6 * yLocal / R4) +
            B4 * sinh(4 * xLocal / R4) * cos(4 * yLocal / R4) +
            B2 * sinh(2 * xLocal / R4) * cos(2 * yLocal / R4) +
            xLocal / R4
    val v = B12 * cosh(12 * xLocal / R4) * sin(12 * yLocal / R4) +
            B10 * cosh(10 * xLocal / R4) * sin(10 * yLocal / R4) +
            B8 * cosh(8 * xLocal / R4) * sin(8 * yLocal / R4) +
            B6 * cosh(6 * xLocal / R4) * sin(6 * yLocal / R4) +
            B4 * cosh(4 * xLocal / R4) * sin(4 * yLocal / R4) +
            B2 * cosh(2 * xLocal / R4) * sin(2 * yLocal / R4) +
            yLocal / R4

    val lambda = atan2(sinh(u), cos(v))

    val cosChi = if (abs(lambda) < 0.01 ||
        abs(lambda + PI) < 0.01 ||
        abs(lambda - PI) < 0.01 ||
        abs(lambda + 2 * PI) < 0.01 ||
        abs(lambda - 2 * PI) < 0.01
    ) {
        sqrt(sinh(u).pow(2) + cos(v).pow(2)) / cosh(u)
    } else {
        sinh(u) / (cosh(u) * sin(lambda))
    }

    val sinChi = sin(v) / cosh(u)

    val phi =
        chiToPhi(Pair(cosChi, sinChi))

    return Pair(lambda + lambdaCM, phi)
}

/**
 * Returns a UtmData corresponding to a latitude and longitude (both in degrees).
 * This function fully abstracts the calculation of UTM zone, band, easting and northing using
 * the above functions.
 *
 * @param latDeg WGS 84 latitude of the point in degrees
 * @param lngDeg WGS 84 longitude of the point in degrees
 *
 * @return UtmData containing the zone, band, easting and northing of the point if the point lies
 *         within the area of usage of UTM (-80 <= latDeg <= 84)
 * @return null if the point lies outside the area of usage of UTM
 */
fun getUtmData(latDeg: Double, lngDeg: Double): UtmData? {
    if (latDeg > 84 || latDeg < -80) {
        return null
    }
    val lngCM =
        degToRad((lngDeg + 180) - (lngDeg + 180) % 6 + 3 - 180)
    val latRad = degToRad(latDeg)
    val lngRad = degToRad(wrapLngDeg(lngDeg))

    val (easting, northing) = when {
        latRad < 0 -> getXY(
            lngRad,
            latRad,
            lngCM,
            0.9996,
            500000.0,
            10000000.0
        )
        else -> getXY(
            lngRad,
            latRad,
            lngCM,
            0.9996,
            500000.0,
            0.0
        )
    }

    val (zone, band) = getUtmZoneAndBand(latDeg, wrapLngDeg(lngDeg))
    return UtmData(easting, northing, zone, band)
}

/**
 * Convenience function that returns a one-liner with all the UTM data of the point at
 * latitude and longitude. This function uses getUtmData() above to obtain the UTM data for
 * formatting into a string.
 *
 * Example of output: "48N 371542 150688"
 *
 * @param latDeg WGS 84 latitude of the point in degrees
 * @param lngDeg WGS 84 longitude of the point in degrees
 *
 * @return String if the point at latitude and longitude lies within the area of usage of UTM
 * @return null if the point lies outside the area of usage of UTM
 */
fun getUtmString(latDeg: Double, lngDeg: Double): String? {
    val utmData = getUtmData(latDeg, lngDeg)

    return if (utmData != null) {
        "${utmData.zone}${utmData.band} %.0f %.0f".format(floor(utmData.x), floor(utmData.y))
    } else {
        null
    }
}

/**
 * Returns the latitude and longitude of a point given the UtmData of the point. This is
 * effectively the inverse function of getUtmData() above.
 *
 * @return Pair containing the WGS 84 latitude and longitude of the point as the first and second
 *         objects respectively.
 */
fun getLatLngFromUtm(utmData: UtmData): Pair<Double, Double> {
    val (x, y, zone, band) = utmData
    val lambdaCM = degToRad(-183.0 + zone * 6)
    val isNorth = LAT_BANDS.indexOf(band) >= 10

    val (lng, lat) = when (isNorth) {
        true -> getLambdaPhi(x, y, lambdaCM, 0.9996, 500000.0, 0.0)
        false -> getLambdaPhi(x, y, lambdaCM, 0.9996, 500000.0, 10000000.0)
    }

    return Pair(radToDeg(lat), radToDeg(lng))
}

fun main() {
    val utmData = UtmData(377465.0, 155412.0, 48, "N")
    val (lat, lng) = getLatLngFromUtm(utmData)
    println("lat: $lat, lng: $lng")
    println(getUtmData(lat, lng))
}