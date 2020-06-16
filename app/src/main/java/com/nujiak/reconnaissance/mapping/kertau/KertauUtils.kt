package com.nujiak.reconnaissance.mapping.kertau

import com.nujiak.reconnaissance.degToRad
import com.nujiak.reconnaissance.radToDeg
import kotlin.math.*

/**
 * Everest 1948 Constants
 */

sealed class EllipsoidParams {
    abstract val A_AXIS: Double
    abstract val B_AXIS: Double
    abstract val F_INVERSE: Double
    abstract val E: Double
    abstract val E_SQR: Double
    abstract val R4: Double
    abstract val A2: Double
    abstract val A4: Double
    abstract val A6: Double
    abstract val A8: Double
    abstract val A10: Double
    abstract val A12: Double
    abstract val B2: Double
    abstract val B4: Double
    abstract val B6: Double
    abstract val B8: Double
    abstract val B10: Double
    abstract val B12: Double
}

private object WgsParams : EllipsoidParams() {
    override val A_AXIS = 6378137.0000000000000
    override val B_AXIS = 6356752.3142451794976
    override val F_INVERSE = 298.257223563
    override val E = 0.081819190842621494335
    override val E_SQR = 0.0066943799901413169961
    override val R4 = 6367449.1458234153093
    override val A2 = 8.3773182062446983032e-04
    override val A4 = 7.608527773572489156e-07
    override val A6 = 1.19764550324249210e-09
    override val A8 = 2.4291706803973131e-12
    override val A10 = 5.711818369154105e-15
    override val A12 = 1.47999802705262e-17
    override val B2 = -8.3773216405794867707e-04
    override val B4 = -5.905870152220365181e-08
    override val B6 = -1.67348266534382493e-10
    override val B8 = -2.1647981104903862e-13
    override val B10 = -3.787930968839601e-16
    override val B12 = -7.23676928796690e-19
}

private object Everest1948Params : EllipsoidParams() {
    override val A_AXIS = 6377304.063000
    override val B_AXIS = 6356103.038993
    override val F_INVERSE = 300.80170000000000000
    override val E = 0.081472980982652689208
    override val E_SQR = 0.0066378466301996867553
    override val R4 = 6366707.963440
    override val A2 = 8.3064943111192510534E-04
    override val A4 = 7.480375027595025021E-07
    override val A6 = 1.16750772278215999E-09
    override val A8 = 2.3479972304395461E-12
    override val A10 = 5.474212231879573E-15
    override val A12 = 1.40642257446745E-17
    override val B2 = -8.3064976590443772201E-04
    override val B4 = -5.805953517555717859E-08
    override val B6 = -1.63133251663416522E-10
    override val B8 = -2.0923797199593389E-13
    override val B10 = -3.630200927775259E-16
    override val B12 = -6.87666654919219E-19

}

private object Ellipsoids {
    const val WGS84: Int = 1000
    const val EVEREST_48: Int = 1001
}


private fun getParams(ellipsoid: Int): EllipsoidParams {
    return when (ellipsoid) {
        Ellipsoids.WGS84 -> WgsParams
        Ellipsoids.EVEREST_48 -> Everest1948Params
        else -> throw IllegalArgumentException("Invalid Coordinate Reference System index: $ellipsoid")
    }
}

/**
 * Converts geographic coordinates (Phi, Lambda, h) to from geocentric coordinates (X, Y, Z)
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

private fun geocentricToGeographic(XYZ: Triple<Double, Double, Double>, ellipsoid: Int) =
    geocentricToGeographic(XYZ.first, XYZ.second, XYZ.third, ellipsoid)

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
 * Parameters for Kertau (RSO) / RSO Malaya (m) [EPSG::3168]
 */
private val LAT_C = degToRad(4.0)
private val LNG_C = degToRad(102.25)
private val AZI_C = degToRad(323.0257905)
private val GAMMA_C = degToRad(323.1301024)
private const val K_C = 0.99984
private const val FE = 804670.24
private const val FN = 0.0

// Aliases for eccentricity values (from Everest Ellipsoid)
private val E1 = Everest1948Params.E
private val E2 = Everest1948Params.E_SQR
private val E4 = E2.pow(2)
private val E6 = E2.pow(3)
private val E8 = E2.pow(4)

// Derived parameters
private val B =
    sqrt(1 + (E2 * cos(LAT_C).pow(4) / (1 - E2)))
private val A =
    Everest1948Params.A_AXIS * B * K_C * sqrt(1 - E2) /
            (1 - E2 * sin(LAT_C).pow(2))
private val t_O = tan(PI / 4 - LAT_C / 2) /
        ((1 - E1 * sin(LAT_C)) /
                (1 + E1 * sin(LAT_C))).pow(E1 / 2)
private val D = B * sqrt(1 - E2) /
        (cos(LAT_C) * sqrt(1 - E2 * sin(LAT_C).pow(2)))
private val F = if (D > 1) (D + sqrt(D.pow(2) - 1) * sign(LAT_C)) else D
private val H = F * t_O.pow(B)
private val G = (F - 1 / F) / 2
private val GAMMA_O = asin(sin(AZI_C) / D)
private val LAMBDA_O = LNG_C - asin(G * tan(GAMMA_O)) / B

private fun getEN(phi: Double, lambda: Double): Pair<Double, Double> {
    val t = tan(PI / 4 - phi / 2) / ((1 - E1 * sin(phi)) / (1 + E1 * sin(phi))).pow(E1 / 2)
    val Q = H / t.pow(B)
    val S = (Q - 1 / Q) / 2
    val T = (Q + 1 / Q) / 2
    val V = sin(B * (lambda - LAMBDA_O))
    val U = (-V * cos(GAMMA_O) + S * sin(GAMMA_O)) / T
    val v = A * ln((1 - U) / (1 + U)) / (2 * B)
    val u = A * atan2((S * cos(GAMMA_O)) + V * sin(GAMMA_O), cos(B * (lambda - LAMBDA_O))) / B

    val easting = v * cos(GAMMA_C) + u * sin(GAMMA_C) + FE
    val northing = u * cos(GAMMA_C) - v * sin(GAMMA_C) + FN

    return Pair(easting, northing)
}

private fun getPhiLambda(E: Double, N: Double): Pair<Double, Double> {

    val v = (E - FE) * cos(GAMMA_C) - (N - FN) * sin(GAMMA_C)
    val u = (N - FN) * cos(GAMMA_C) + (E - FE) * sin(GAMMA_C)

    val Q = kotlin.math.E.pow(-B * v / A)
    val S = (Q - 1 / Q) / 2
    val T = (Q + 1 / Q) / 2
    val V = sin(B * u / A)
    val U = (V * cos(GAMMA_O) + S * sin(GAMMA_O)) / T
    val t = (H / sqrt((1 + U) / (1 - U))).pow(1 / B)
    val chi = PI / 2 - 2 * atan(t)

    val phi = chi + sin(2 * chi) * (E2 / 2 + 5 * E4 / 24 + E6 / 12 + 13 * E8 / 360) +
            sin(4 * chi) * (7 * E4 / 48 + 29 * E6 / 240 + 811 * E8 / 11520) +
            sin(6 * chi) * (7 * E6 / 120 + 81 * E8 / 1120) +
            sin(8 * chi) * (4279 * E8 / 161280)
    val lambda = LAMBDA_O - atan2((S * cos(GAMMA_O) - V * sin(GAMMA_O)), cos(B * u / A)) / B
    return Pair(phi, lambda)
}

fun getKertauGrids(latDeg: Double, lngDeg: Double): Pair<Double, Double>? {
    if (latDeg < 1.21 || latDeg > 6.72 || lngDeg < 99.59 || lngDeg > 104.6) {
        return null
    }

    val phi = degToRad(latDeg)
    val lambda = degToRad(lngDeg)

    // Convert to WGS 84 geocentric coordinates
    val (X, Y, Z) = geographicToGeocentric(phi, lambda, 0.0, Ellipsoids.WGS84)

    // Transform to Everest 1948 geocentric coordinates
    val translatedGeocentric =
        geocentricTranslate(X, Y, Z, Triple(11.0, -851.0, -5.0))

    // Convert to Everest 1948 geographic coordinates
    val translatedGeographic = geocentricToGeographic(translatedGeocentric, Ellipsoids.EVEREST_48)

    // Return Easting and Northing using X and Y of translated geographic coordinates
    return getEN(translatedGeographic.first, translatedGeographic.second)
}

fun getLatLngFromKertau(easting: Double, northing: Double): Pair<Double, Double>? {

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

fun getKertauGridsString(latDeg: Double, lngDeg: Double): String? {
    val kertauPair = getKertauGrids(latDeg, lngDeg)
    return if (kertauPair != null) {
        val (easting, northing) = kertauPair
        "%.0f %.0f".format(floor(easting), floor(northing))
    } else {
        null
    }
}

fun main() {
    if (false) {
        val lat = degToRad(1.3998391682)
        val lng = degToRad(103.7386691917)

        val (X, Y, Z) = geographicToGeocentric(lat, lng, ellipsoid = Ellipsoids.WGS84)
        val (newX, newY, newZ) = geocentricTranslate(X, Y, Z, Triple(11.0, -851.0, -5.0))
        val (newLat, newLng, _) = geocentricToGeographic(
            newX,
            newY,
            newZ,
            ellipsoid = Ellipsoids.EVEREST_48
        )
        val (easting, northing) = getEN(newLat, newLng)
        println("E: $easting\nN: $northing")

    }

    if (false) {
        val X = 3771793.968
        val Y = 140253.342
        val Z = 5124304.349
        val (lat, lng, h) = geocentricToGeographic(X, Y, Z, Ellipsoids.WGS84)
        println("lat: $lat, lng: $lng, h: $h")
        val (newX, newY, newZ) = geographicToGeocentric(lat, lng, h, Ellipsoids.WGS84)
        println("newX: $newX, newY: $newY, newZ: $newZ")
        val (newLat, newLng, newH) = geocentricToGeographic(newX, newY, newZ, Ellipsoids.WGS84)
        println("newLat: $newLat, newLng: $newLng, newH: $newH")
    }

}