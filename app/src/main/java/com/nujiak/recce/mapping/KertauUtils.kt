package com.nujiak.recce.mapping

import com.nujiak.recce.utils.degToRad
import com.nujiak.recce.utils.radToDeg
import kotlin.math.*

/*
 Parameters for Kertau (RSO) / RSO Malaya (m) [EPSG::3168]
 */
private val LAT_C = degToRad(4.0)
private val LNG_C = degToRad(102.25)
private val AZI_C = degToRad(323.0257905)
private val GAMMA_C = degToRad(323.1301024)
private const val K_C = 0.99984
private const val FE = 804670.24
private const val FN = 0.0

/*
 Aliases for eccentricity values (from Everest Ellipsoid)
 */
private val E1 = Everest1948Params.E
private val E2 = Everest1948Params.E_SQR
private val E4 = E2.pow(2)
private val E6 = E2.pow(3)
private val E8 = E2.pow(4)

/*
 Derived parameters
 */
private val B =
    sqrt(1 + (E2 * cos(LAT_C).pow(4) / (1 - E2)))
private val A =
    Everest1948Params.A_AXIS * B * K_C * sqrt(1 - E2) /
            (1 - E2 * sin(LAT_C).pow(2))
private val t_O = tan(PI / 4 - LAT_C / 2) /
        ((1 - E1 * sin(LAT_C)) /
                (1 + E1 * sin(
                    LAT_C
                ))).pow(E1 / 2)
private val D = B * sqrt(1 - E2) /
        (cos(LAT_C) * sqrt(
            1 - E2 * sin(
                LAT_C
            ).pow(2)
        ))
private val F = if (D > 1) (D + sqrt(
    D.pow(2) - 1
) * sign(LAT_C)) else D
private val H = F * t_O.pow(
    B
)
private val G = (F - 1 / F) / 2
private val GAMMA_O = asin(sin(AZI_C) / D)
private val LAMBDA_O = LNG_C - asin(
    G * tan(GAMMA_O)
) / B


/**
 * Converts geographic coordinates (Phi, Lambda, h) to geocentric coordinates (X, Y, Z)
 *
 * @param phi Geographic latitude of the point
 * @param lambda Geographic longitude of the point
 * @param h Height of the point
 * @param ellipsoid ID of the ellipsoid to use as given in the EllipsoidUtils.Ellipsoids object
 *
 * @return Triple containing the geocentric coordinates X, Y, Z of the point as the first, second,
 *         and third object respectively.
 */
private fun geographicToGeocentric(
    phi: Double,
    lambda: Double,
    h: Double = 0.0,
    ellipsoid: Int
): Triple<Double, Double, Double> {

    val ep = getParams(ellipsoid)

    val v = ep.A_AXIS / sqrt(1 - ep.E_SQR * sin(phi).pow(2))

    val X = (v + h) * cos(phi) * cos(lambda)
    val Y = (v + h) * cos(phi) * sin(lambda)
    val Z = ((1 - ep.E_SQR) * v + h) * sin(phi)
    return Triple(X, Y, Z)
}

/**
 * Converts from geocentric coordinates (X, Y, Z) to geographic coordinates (Phi, Lambda, h)
 *
 * @param X Geocentric coordinate X of the point
 * @param Y Geocentric coordinate Y of the point
 * @param Z Geocentric coordinate Z of the point
 * @param ellipsoid ID of the ellipsoid to use as given in the EllipsoidUtils.Ellipsoids object
 *
 * @return Triple containing the geographic latitude, longitude and height of the point as
 * the first, second, and third object respectively.
 */
private fun geocentricToGeographic(
    X: Double,
    Y: Double,
    Z: Double,
    ellipsoid: Int
): Triple<Double, Double, Double> {

    val ep = getParams(ellipsoid)

    val epsilon = ep.E_SQR / (1 - ep.E_SQR)
    val p = sqrt(X.pow(2) + Y.pow(2))
    val q = atan2((Z * ep.A_AXIS), (p * ep.B_AXIS))

    val phi = atan2(
        (Z + epsilon * ep.B_AXIS * sin(q).pow(3)),
        (p - ep.E_SQR * ep.A_AXIS * cos(q).pow(3))
    )
    val lambda = atan2(Y, X)
    val v = ep.A_AXIS / sqrt(1 - ep.E_SQR * sin(phi).pow(2))
    val h = (p / cos(phi)) - v

    return Triple(phi, lambda, h)
}

/**
 * Overloaded function to convert a Triple containing geocentric coordinates
 * to geographic coordinates.
 *
 * @param XYZ Triple containing the geocentric coordinates X, Y, and Z as the first, second, and
 *            third object respectively.
 *
 * @return Triple containing the geographic latitude, longitude and height of the point as
 *         the first, second, and third object respectively.
 */
private fun geocentricToGeographic(XYZ: Triple<Double, Double, Double>, ellipsoid: Int) =
    geocentricToGeographic(
        XYZ.first,
        XYZ.second,
        XYZ.third,
        ellipsoid
    )

/**
 * Translates geocentric coordinates of a point given a translation vector.
 *
 * @param X Geocentric coordinate X of the point
 * @param Y Geocentric coordinate Y of the point
 * @param Z Geocentric coordinate Z of the point
 * @param translation Triple containing the translations in X, Y, and Z as the first, second, and
 *                    third object respectively.
 *
 * @return Triple containing the translated geocentric coordinates X, Y, and Z as the first, second,
 *         and third object respectively.
 */
private fun geocentricTranslate(
    X: Double,
    Y: Double,
    Z: Double,
    translation: Triple<Double, Double, Double>
): Triple<Double, Double, Double> {
    val (dX, dY, dZ) = translation
    return Triple(X + dX, Y + dY, Z + dZ)
}

/**
 * Returns the Easting and Northing of a point in the Kertau (RSO) / RSO Malaya system given the
 * latitude Phi and longitude Lambda. This is the inverse function of getPhiLambda() below.
 *
 * This function is obtained from:
 * EPSG Guidance Note 7 Part 2, 3.2.4.1 Hotine Oblique Mercator of which Variant A is used.
 *
 * @param phi Latitude of the point
 * @param lambda Longitude of the point
 *
 * @return Pair containing the easting and northing of the point as the first and second object
 *         respectively.
 */
private fun getEN(phi: Double, lambda: Double): Pair<Double, Double> {
    val t = tan(PI / 4 - phi / 2) / ((1 - E1 * sin(phi)) / (1 + E1 * sin(phi))).pow(
        E1 / 2
    )
    val Q = H / t.pow(B)
    val S = (Q - 1 / Q) / 2
    val T = (Q + 1 / Q) / 2
    val V = sin(B * (lambda - LAMBDA_O))
    val U = (-V * cos(GAMMA_O) + S * sin(
        GAMMA_O
    )) / T
    val v = A * ln((1 - U) / (1 + U)) / (2 * B)
    val u = A * atan2(
        (S * cos(
            GAMMA_O
        )) + V * sin(GAMMA_O), cos(
            B * (lambda - LAMBDA_O)
        )
    ) / B

    val easting = v * cos(GAMMA_C) + u * sin(
        GAMMA_C
    ) + FE
    val northing = u * cos(GAMMA_C) - v * sin(
        GAMMA_C
    ) + FN

    return Pair(easting, northing)
}

/**
 * Returns the WGS 84 latitude and longitude of a point given the Kertau (RSO) / RSO Malaya grids.
 * This is the inverse function of getEN() above.
 *
 * This function is obtained from:
 * EPSG Guidance Note 7 Part 2, 3.2.4.1 Hotine Oblique Mercator of which Variant A is used.
 *
 * @param E Easting of the point
 * @param N Northing of the point
 *
 * @return Pair containing the WGS 84 latitude and longitude of the point as the first and second
 *         object respectively.
 */
private fun getPhiLambda(E: Double, N: Double): Pair<Double, Double> {

    val v = (E - FE) * cos(GAMMA_C) - (N - FN) * sin(
        GAMMA_C
    )
    val u = (N - FN) * cos(GAMMA_C) + (E - FE) * sin(
        GAMMA_C
    )

    val Q = kotlin.math.E.pow(-B * v / A)
    val S = (Q - 1 / Q) / 2
    val T = (Q + 1 / Q) / 2
    val V = sin(B * u / A)
    val U = (V * cos(GAMMA_O) + S * sin(
        GAMMA_O
    )) / T
    val t = (H / sqrt((1 + U) / (1 - U))).pow(1 / B)
    val chi = PI / 2 - 2 * atan(t)

    val phi = chi + sin(2 * chi) * (E2 / 2 + 5 * E4 / 24 + E6 / 12 + 13 * E8 / 360) +
            sin(4 * chi) * (7 * E4 / 48 + 29 * E6 / 240 + 811 * E8 / 11520) +
            sin(6 * chi) * (7 * E6 / 120 + 81 * E8 / 1120) +
            sin(8 * chi) * (4279 * E8 / 161280)
    val lambda = LAMBDA_O - atan2(
        (S * cos(
            GAMMA_O
        ) - V * sin(GAMMA_O)), cos(
            B * u / A
        )
    ) / B
    return Pair(phi, lambda)
}

/**
 * Convenience functions that abstracts away all the steps of converting WGS 84 latitude and
 * longitude of a point into Kertau (RSO) / RSO Malaya grids. This is the inverse function of
 * getLatLngFromKertau() below
 *
 * @param latDeg WGS 84 latitude of the point in degrees
 * @param lngDeg WGS 84 longitude of the point in degrees
 *
 * @return Pair containing the easting and northing of the point as the first and second object
 *         respectively.
 */
fun getKertauGrids(latDeg: Double, lngDeg: Double): Pair<Double, Double>? {
    if (latDeg < 1.21 || latDeg > 6.72 || lngDeg < 99.59 || lngDeg > 104.6) {
        return null
    }

    val phi = degToRad(latDeg)
    val lambda = degToRad(lngDeg)

    // Convert to WGS 84 geocentric coordinates
    val (X, Y, Z) = geographicToGeocentric(
        phi,
        lambda,
        0.0,
        Ellipsoids.WGS84
    )

    // Transform to Everest 1948 geocentric coordinates
    val translatedGeocentric =
        geocentricTranslate(
            X,
            Y,
            Z,
            Triple(11.0, -851.0, -5.0)
        )

    // Convert to Everest 1948 geographic coordinates
    val translatedGeographic =
        geocentricToGeographic(
            translatedGeocentric,
            Ellipsoids.EVEREST_48
        )

    // Return Easting and Northing using X and Y of translated geographic coordinates
    return getEN(
        translatedGeographic.first,
        translatedGeographic.second
    )
}

/**
 * Convenience functions that abstracts away all the steps of converting Kertau (RSO) / RSO Malaya
 * grids of a point into WGS 84 latitude and longitude. This is the inverse function of
 * getKertauGrids() above
 *
 * @param easting Easting of the point in degrees
 * @param northing Northing of the point in degrees
 *
 * @return Pair containing the WGS 84 longitude and latitude of the point as the first and second
 *         object respectively.
 */
fun getLatLngFromKertau(easting: Double, northing: Double): Pair<Double, Double> {

    // Get phi and lambda from Easting and Northing
    val (phi, lambda) = getPhiLambda(easting, northing)

    // Convert to Everest 1948 geocentric coordinates
    val (X, Y, Z) = geographicToGeocentric(phi, lambda, 0.0, Ellipsoids.EVEREST_48)

    // Translate to WGS 84 geocentric coordinates
    val transformedGeocentric =
        geocentricTranslate(X, Y, Z, Triple(-11.0, 851.0, 5.0))

    // Convert to WGS 84 geographic coordinates
    val transformedGeographic =
        geocentricToGeographic(transformedGeocentric, Ellipsoids.WGS84)

    // Return first 2 values of coordinates as Latitude and Longitude (3rd value being Altitude)
    return Pair(radToDeg(transformedGeographic.first), radToDeg(transformedGeographic.second))
}

/**
 * Convenience function that returns a one-liner with the Kertau (RSO) / RSO Malaya grids
 * of a point concatenated in a space-separated string. Grids are rounded down to integers.
 * This function uses getKertauGrids() above to obtain the grids for formatting into a string.
 *
 * Example of output: "650083 150728"
 *
 * @param latDeg WGS 84 latitude of the point in degrees
 * @param lngDeg WGS 84 longitude of the point in degrees
 *
 * @return String if the point at latitude and longitude lies within the area of usage
 * @return null if the point lies outside the area of usage
 */
fun getKertauGridsString(latDeg: Double, lngDeg: Double): String? {
    val kertauPair =
        getKertauGrids(latDeg, lngDeg)
    return if (kertauPair != null) {
        val (easting, northing) = kertauPair
        "%.0f %.0f".format(floor(easting), floor(northing))
    } else {
        null
    }
}