package com.nujiak.recce.utils

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable

//fun animateColor(view: View, color: Int, duration: Long, callback: (Int) -> Unit) {
//    try {
//        val from = (view.background as ColorDrawable).color
//        val colorAnim = ValueAnimator.ofObject(ArgbEvaluator(), from, color).apply {
//            setDuration(duration)
//            addUpdateListener { animator ->
//                callback(animator.animatedValue as Int)
//            }
//        }
//        colorAnim.start()
//    } catch (e: java.lang.Exception) {
//        e.message?.let {
//            Log.e("animateColor", it)
//        }
//        callback(color)
//    }
//}

fun animateColor(colorDrawable: Drawable?, color: Int, duration: Long, callback: (Int) -> Unit) {
    try {
        val from = (colorDrawable as ColorDrawable).color
        animateColor(from, color, duration, callback)
    } catch (e: java.lang.Exception) {
        callback(color)
    }
}

fun animateColor(
    colorStateList: ColorStateList?,
    color: Int,
    duration: Long,
    callback: (Int) -> Unit
) {
    try {
        val from = colorStateList!!.defaultColor
        animateColor(from, color, duration, callback)
    } catch (e: java.lang.Exception) {
        callback(color)
    }
}

fun animateColor(fromColor: Int, toColor: Int, duration: Long, callback: (Int) -> Unit) {
    val colorAnim = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
        setDuration(duration)
        addUpdateListener { animator ->
            callback(animator.animatedValue as Int)
        }
    }
    colorAnim.start()
}