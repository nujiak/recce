package com.nujiak.recce.enums

import java.lang.IllegalArgumentException

/**
 * Coordinate System used by the application
 *
 * @property index index of the coordinate system in any collection
 */
enum class CoordinateSystem(val index: Int) {
    /**
     * Universal Traverse Mercator
     */
    UTM(0),

    /**
     * Military Grid Reference System
     */
    MGRS(1),

    /**
     * Kertau 1948
     */
    KERTAU(2),

    /**
     * World Geodetic System 1984
     *
     * Used in Google Maps
     */
    WGS84(3),

    /**
     * Ordnance Survey National Grid / British National Grid (BNG)
     */
    BNG(4);

    companion object {
        private val map = values().associateBy(CoordinateSystem::index)

        /**
         * Returns the [CoordinateSystem] at the given index
         *
         * @param index
         * @return
         */
        fun atIndex(index: Int): CoordinateSystem {
            return map[index] ?: throw IllegalArgumentException("Invalid CoordinateSystem index: $index")
        }
    }
}
