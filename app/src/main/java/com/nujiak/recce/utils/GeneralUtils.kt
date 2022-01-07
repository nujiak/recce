package com.nujiak.recce.utils

import android.content.res.Resources
import android.util.TypedValue
import java.text.NumberFormat
import java.util.Locale

private val singleDecimalNumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
}

fun Double.formatAsDistanceString(): String {
    return if (this < 1000000) {
        singleDecimalNumberFormat.format(this) + " m"
    } else {
        singleDecimalNumberFormat.format(this / 1000) + " km"
    }
}

fun Float.formatAsDistanceString(): String {
    return this.toDouble().formatAsDistanceString()
}

fun Double.formatAsAreaString(): String {
    return if (this < 1000000) {
        singleDecimalNumberFormat.format(this) + " m²"
    } else {
        singleDecimalNumberFormat.format(this / 1000000) + " km²"
    }
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
