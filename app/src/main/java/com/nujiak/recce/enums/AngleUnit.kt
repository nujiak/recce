package com.nujiak.recce.enums

import java.lang.IllegalArgumentException

/**
 * Angle unit used by the application
 *
 * @property index index of the angle in any collection
 */
enum class AngleUnit(val index: Int) {
    /**
     * Degrees measured from 0 to 360
     */
    DEGREE(0),

    /**
     * NATO Mils measured from 0 to 6400
     *
     */
    NATO_MIL(1);

    companion object {
        private val map = values().associateBy(AngleUnit::index)

        /**
         * Returns the [AngleUnit] at the given index
         *
         * @param index
         * @return
         */
        fun atIndex(index: Int): AngleUnit {
            return map[index] ?: throw IllegalArgumentException("Invalid AngleUnit index: $index")
        }
    }
}
