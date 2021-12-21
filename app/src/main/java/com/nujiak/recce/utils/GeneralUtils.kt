package com.nujiak.recce.utils

import android.content.res.Resources
import android.graphics.Color
import android.util.TypedValue
import com.google.android.gms.maps.model.LatLng
import com.nujiak.recce.R
import com.nujiak.recce.enums.AngleUnit
import com.nujiak.recce.enums.CoordinateSystem
import com.nujiak.recce.mapping.Mapping
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

fun getGridString(latDeg: Double, lngDeg: Double, coordSysId: CoordinateSystem, resources: Resources): String {
    val latLng = LatLng(latDeg, lngDeg)
    return when (coordSysId) {
        CoordinateSystem.UTM -> {
            Mapping.toUtm(latLng).toString()
        }
        CoordinateSystem.MGRS -> {
            Mapping.toMgrs(latLng)?.toString()
        }
        CoordinateSystem.KERTAU -> {
            Mapping.toKertau1948(latLng).toString()
        }
        CoordinateSystem.WGS84 -> {
            return Mapping.parseLatLng(latLng).toString()
        }
    } ?: resources.getString(R.string.not_available)
}

fun getAngleString(angleRad: Float, angleUnit: AngleUnit, withSign: Boolean = true): String {
    return when (angleUnit) {
        AngleUnit.DEGREE -> {
            "%.1f°".format(radToDeg(angleRad))
        }
        AngleUnit.NATO_MIL -> {
            if (withSign) {
                "${if (angleRad > 0) '+' else '-'}${
                radToNatoMils(abs(angleRad)).roundToInt().toString()
                    .padStart(4, '0')
                } mils"
            } else {
                "${radToNatoMils(abs(angleRad)).roundToInt().toString().padStart(4, '0')} mils"
            }
        }
    }
}

private val numberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
}

fun Double.formatAsDistanceString(): String {
    return if (this < 1000000) {
        numberFormat.format(this) + " m"
    } else {
        numberFormat.format(this / 1000) + " km"
    }
}

fun Float.formatAsDistanceString(): String {
    return this.toDouble().formatAsDistanceString()
}

fun Double.formatAsAreaString(): String {
    return if (this < 1000000) {
        numberFormat.format(this) + " m²"
    } else {
        numberFormat.format(this / 1000000) + " km²"
    }
}

fun withAlpha(color: Int, alpha: Int): Int {
    val r = Color.red(color)
    val g = Color.green(color)
    val b = Color.blue(color)

    return Color.argb(alpha, r, g, b)
}

fun Double.round(decimals: Int): Double {
    val magnitude = 10.0.pow(decimals.toDouble())
    return round(this * magnitude) / magnitude
}

/**
 * Converts a length in dp to screen pixels
 *
 * @param dps length in dp
 * @return length in screen pixel
 */
fun Resources.dpToPx(dp:Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, this.displayMetrics)
}