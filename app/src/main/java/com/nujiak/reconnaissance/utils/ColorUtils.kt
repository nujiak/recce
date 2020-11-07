package com.nujiak.reconnaissance.utils

import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.nujiak.reconnaissance.R

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

val PIN_COLOR_HUES = arrayOf(
    BitmapDescriptorFactory.HUE_RED,
    BitmapDescriptorFactory.HUE_ORANGE,
    BitmapDescriptorFactory.HUE_GREEN,
    BitmapDescriptorFactory.HUE_AZURE,
    BitmapDescriptorFactory.HUE_VIOLET
)

val PIN_CARD_BACKGROUNDS = arrayOf(
    R.color.tagRed,
    R.color.tagOrange,
    R.color.tagGreen,
    R.color.tagAzure,
    R.color.tagViolet
)
val PIN_CARD_DARK_BACKGROUNDS = arrayOf(
    R.color.tagRedDark,
    R.color.tagOrangeDark,
    R.color.tagGreenDark,
    R.color.tagAzureDark,
    R.color.tagVioletDark
)

val PIN_VECTOR_DRAWABLE = arrayOf(
    R.drawable.ic_map_pin_red,
    R.drawable.ic_map_pin_orange,
    R.drawable.ic_map_pin_green,
    R.drawable.ic_map_pin_azure,
    R.drawable.ic_map_pin_violet
)