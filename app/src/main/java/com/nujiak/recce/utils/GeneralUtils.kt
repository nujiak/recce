package com.nujiak.recce.utils

import android.content.res.Resources
import android.util.TypedValue

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
