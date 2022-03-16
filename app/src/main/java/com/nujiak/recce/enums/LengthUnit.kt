package com.nujiak.recce.enums

import androidx.annotation.StringRes
import com.nujiak.recce.R

/**
 * Length units used throughout the application
 *
 * @property unitName name of the unit or system
 * @property smallUnit symbol for the smaller unit
 * @property bigUnit symbol for the larger unit
 * @property metrePerSmallUnit multiplier such that
 *                             lengthInMetres / [metrePerSmallUnit] = lengthInSmallUnit
 * @property metrePerBigUnit multiplier such that
 *                           lengthInMetres / [metrePerBigUnit] = lengthInBigUnit
 * @property smallUnitThreshold value such that any length exceeding this amount in the small unit
 *                              will be displayed in the big unit
 * @property smallAreaUnit symbol for the area unit
 * @property bigAreaUnit symbol for the larger area unit
 * @property metreSquarePerSmallAreaUnit multiplier such that
 *                                       areaInMetres / [metreSquarePerSmallAreaUnit] = areaInSmallAreaUnit
 * @property metreSquarePerBigAreaUnit multiplier such that
 *                                     areaInMetres / [metreSquarePerBigAreaUnit] = areaInBigAreaUnit
 * @property smallAreaUnitThreshold value such that any area exceeding this amount in the small area
 *                                  unit will be displayed in the big unit
 */
enum class LengthUnit(
    @StringRes val unitName: Int,
    @StringRes val smallUnit: Int,
    @StringRes val bigUnit: Int,
    val metrePerSmallUnit: Double,
    val metrePerBigUnit: Double,
    val smallUnitThreshold: Int,
    val smallUnitPrecision: Int,
    val bigUnitPrecision: Int,
    @StringRes val smallAreaUnit: Int,
    @StringRes val bigAreaUnit: Int,
    val metreSquarePerSmallAreaUnit: Double,
    val metreSquarePerBigAreaUnit: Double,
    val smallAreaUnitThreshold: Int,
) {
    /**
     * Metric units: metres (m) and kilometres (km)
     */
    METRIC(
        R.string.metric,
        R.string.metre_unit,
        R.string.kilometre_unit,
        1.0,
        1000.0,
        1_000_000,
        1,
        1,
        R.string.metre_square_unit,
        R.string.kilometre_square_unit,
        1.0,
        1000000.0,
        1000000,
    ),

    /**
     * Imperial units: feet (ft) and miles (mi)
     */
    IMPERIAL(
        R.string.imperial,
        R.string.feet_unit,
        R.string.mile_unit,
        1 / 0.3048,
        1609.344,
        264,
        1,
        2,
        R.string.acre_unit,
        R.string.acre_unit,
        4046.8564224,
        4046.8564224,
        0,
    ),

    /**
     * Nautical miles (NM)
     */
    NAUTICAL_MILES(
        R.string.nautical_miles,
        R.string.nautical_mile_unit,
        R.string.nautical_mile_unit,
        0.0,
        1852.0,
        0,
        0,
        3,
        R.string.metre_square_unit,
        R.string.kilometre_square_unit,
        1.0,
        1000000.0,
        1000000,
    ),

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
