package com.nujiak.recce.enums

import androidx.annotation.StringRes
import com.nujiak.recce.R
import java.lang.IllegalArgumentException

enum class LengthUnit(@StringRes val unitName: Int) {

    /**
     * Metric units: metres (m) and kilometres (km)
     */
    METRIC(R.string.metric),

    /**
     * Imperial units: feet (ft) and miles (mi)
     */
    IMPERIAL(R.string.imperial),

    /**
     * Nautical miles (NM)
     */
    NAUTICAL_MILES(R.string.nautical_miles),

    ;

    companion object {
        private val map = values().withIndex().associateBy { it.index }
        val names: List<Int> = values().map(LengthUnit::unitName)
        private val indices = values().withIndex().associateBy { it.value }

        /**
         * Returns the [LengthUnit] at the given index
         *
         * @param index
         * @return
         */
        fun atIndex(index: Int): LengthUnit {
            return map[index]?.value ?: throw IllegalArgumentException("Invalid LengthUnit index: $index")
        }
    }

    /**
     * The index of the [LengthUnit]
     */
    val index: Int
        get() = indices[this]!!.index
}