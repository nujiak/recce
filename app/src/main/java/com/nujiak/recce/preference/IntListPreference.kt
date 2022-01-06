package com.nujiak.recce.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference

class IntListPreference : ListPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

    override fun getPersistedString(defaultReturnValue: String?): String {

        val intDefaultReturnValue = defaultReturnValue?.toInt() ?: 0
        val intValue: Int = getPersistedInt(intDefaultReturnValue)

        return intValue.toString()
    }

    override fun persistString(value: String): Boolean {
        val intValue = value.toInt()
        return persistInt(intValue)
    }
}
