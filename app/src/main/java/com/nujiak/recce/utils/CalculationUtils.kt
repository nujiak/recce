package com.nujiak.recce.utils

import kotlin.math.PI

fun radToDeg(rad: Float): Float {
    return rad * 180 / PI.toFloat()
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

fun wrapLngDeg(longitude: Double): Double {
    return (longitude + 180) % 360 - 180
}
