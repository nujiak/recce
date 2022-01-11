package com.nujiak.recce.utils

import android.graphics.Color
import com.nujiak.recce.R

/**
 * Array of color strings in order of Hue starting from 0 deg
 */
val COLORS = arrayOf(
    "Red",
    "Orange",
    "Green",
    "Blue",
    "Purple"
)

val PIN_CARD_BACKGROUNDS = arrayOf(
    R.color.tagRed,
    R.color.tagOrange,
    R.color.tagGreen,
    R.color.tagAzure,
    R.color.tagViolet
)

val PIN_VECTOR_DRAWABLE = arrayOf(
    R.drawable.ic_map_pin_red,
    R.drawable.ic_map_pin_orange,
    R.drawable.ic_map_pin_green,
    R.drawable.ic_map_pin_azure,
    R.drawable.ic_map_pin_violet
)

fun withAlpha(color: Int, alpha: Int): Int {
    val r = Color.red(color)
    val g = Color.green(color)
    val b = Color.blue(color)

    return Color.argb(alpha, r, g, b)
}
