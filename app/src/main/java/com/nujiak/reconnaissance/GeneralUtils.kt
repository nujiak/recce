package com.nujiak.reconnaissance

import android.content.res.Resources
import android.graphics.Color
import com.nujiak.reconnaissance.mapping.getKertauGridsString
import com.nujiak.reconnaissance.mapping.getMgrsData
import com.nujiak.reconnaissance.mapping.getUtmData
import com.nujiak.reconnaissance.mapping.toSingleLine
import com.nujiak.reconnaissance.modalsheets.SettingsSheet
import java.text.NumberFormat
import java.util.*

fun getGridString(latDeg: Double, lngDeg: Double, coordSysId: Int, resources: Resources): String {
    return when (coordSysId) {
        SettingsSheet.COORD_SYS_ID_UTM -> {
            getUtmData(
                latDeg,
                lngDeg
            )?.toSingleLine(5)
        }
        SettingsSheet.COORD_SYS_ID_MGRS -> {
            getMgrsData(latDeg, lngDeg)?.toSingleLine(includeWhitespace = true)
        }
        SettingsSheet.COORD_SYS_ID_KERTAU -> {
            getKertauGridsString(
                latDeg,
                lngDeg
            )
        }
        else -> throw IllegalArgumentException("Invalid coordinate system index: $coordSysId")
    } ?: resources.getString(R.string.not_available)
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