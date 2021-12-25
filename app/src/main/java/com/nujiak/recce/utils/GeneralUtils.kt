package com.nujiak.recce.utils

import android.content.res.Resources
import android.graphics.Color
import android.util.TypedValue
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.pow
import kotlin.math.round

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
 * @param dp length in dp
 * @return length in screen pixel
 */
fun Resources.dpToPx(dp: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, this.displayMetrics)
}

/**
 * Converts a length in sp to screen pixels
 *
 * @param sp length in sp
 * @return length in screen pixel
 */
fun Resources.spToPx(sp: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, this.displayMetrics)
}
