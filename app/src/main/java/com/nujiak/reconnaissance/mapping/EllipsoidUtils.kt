package com.nujiak.reconnaissance.mapping

/**
 * An abstract class laying out the properties required to define an ellipsoid model
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

/**
 * An implementation of EllipsoidParams containing the parameters
 * to define the WGS 84 ellipsoid
 */
object WgsParams : EllipsoidParams() {
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

/**
 * An implementation of EllipsoidParams containing the parameters
 * to define the Everest 1948 ellipsoid
 */
object Everest1948Params : EllipsoidParams() {
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

/**
 * Object containing unique IDs to identify ellipsoids
 */
object Ellipsoids {
    const val WGS84: Int = 1000
    const val EVEREST_48: Int = 1001
}

/**
 * Helper function to get the relevant EllipsoidParams object
 *
 * @param ellipsoid ID matching one of constants in Ellipsoids
 */
fun getParams(ellipsoid: Int): EllipsoidParams {
    return when (ellipsoid) {
        Ellipsoids.WGS84 -> WgsParams
        Ellipsoids.EVEREST_48 -> Everest1948Params
        else -> throw IllegalArgumentException("Invalid Coordinate Reference System index: $ellipsoid")
    }
}