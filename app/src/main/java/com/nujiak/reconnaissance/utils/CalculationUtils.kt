package com.nujiak.reconnaissance.utils

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.tan

fun radToDeg(rad: Double): Double {
    return rad * 180 / PI
}

fun radToDeg(rad: Float): Float {
    return rad * 180 / PI.toFloat()
}

fun radToNatoMils(rad: Double): Double {
    return rad * 3200 / PI
}

fun radToNatoMils(rad: Float): Float {
    return rad * 3200 / PI.toFloat()
}

fun degToRad(deg: Double): Double {
    return deg / 180 * PI
}

fun degToRad(deg: Float): Float {
    return deg / 180 * PI.toFloat()
}

fun degToNatoMils(deg: Double): Double {
    return deg / 180 * 3200
}

fun degToNatoMils(deg: Float): Float {
    return deg / 180 * 3200
}


fun wrapLngDeg(longitude: Double): Double {
    return (longitude + 180) % 360 - 180
}

fun sec(x: Double): Double {
    return 1 / cos(x)
}

fun sec(x: Float): Float {
    return 1 / cos(x)
}

/**
 * Returns cotangent of an angle in rad using
 * cot(x) = tan(pi/2 - x)
 */
fun cot(x: Double): Double {
    return tan(PI / 2 - x)
}