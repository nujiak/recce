package com.nujiak.recce.utils

import com.nujiak.recce.mapping.Mapping
import android.content.res.Resources
import android.graphics.Color
import com.google.android.gms.maps.model.LatLng
import com.nujiak.recce.ANGLE_UNIT_ID_DEG
import com.nujiak.recce.ANGLE_UNIT_ID_NATO_MILS
import com.nujiak.recce.COORD_SYS_ID_KERTAU
import com.nujiak.recce.COORD_SYS_ID_LATLNG
import com.nujiak.recce.COORD_SYS_ID_MGRS
import com.nujiak.recce.COORD_SYS_ID_UTM
import com.nujiak.recce.R
import java.text.NumberFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

fun getGridString(latDeg: Double, lngDeg: Double, coordSysId: Int, resources: Resources): String {
    val latLng = LatLng(latDeg, lngDeg)
    return when (coordSysId) {
        COORD_SYS_ID_UTM -> {
            Mapping.toUtm(latLng).toString()
        }
        COORD_SYS_ID_MGRS -> {
            Mapping.toMgrs(latLng)?.toString()
        }
        COORD_SYS_ID_KERTAU -> {
            Mapping.toKertau1948(latLng).toString()
        }
        COORD_SYS_ID_LATLNG -> {
            return Mapping.parseLatLng(latLng).toString()
        }
        else -> throw IllegalArgumentException("Invalid coordinate system index: $coordSysId")
    } ?: resources.getString(R.string.not_available)
}

fun getAngleString(angleRad: Float, angleUnitId: Int, withSign: Boolean = true): String {
    return when (angleUnitId) {
        ANGLE_UNIT_ID_DEG -> {
            "%.1f°".format(radToDeg(angleRad))
        }
        ANGLE_UNIT_ID_NATO_MILS -> {
            if (withSign) {
                "${if (angleRad > 0) '+' else '-'}${
                    radToNatoMils(abs(angleRad)).roundToInt().toString()
                        .padStart(4, '0')
                } mils"
            } else {
                "${radToNatoMils(abs(angleRad)).roundToInt().toString().padStart(4, '0')} mils"
            }
        }
        else -> throw IllegalArgumentException("Invalid angle unit index: $angleUnitId")
    }
}

fun getAngleString(angleRad: Double, angleUnitId: Int, withSign: Boolean = true) =
    getAngleString(angleRad.toFloat(), angleUnitId, withSign)

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