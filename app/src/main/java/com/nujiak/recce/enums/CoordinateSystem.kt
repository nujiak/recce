package com.nujiak.recce.enums

import androidx.annotation.StringRes
import com.nujiak.recce.R
import java.lang.IllegalArgumentException

/**
 * Coordinate System used by the application
 *
 * The ordering of the enums affects the ordering of the coordinate system in the app
 *
 * @property index index of the coordinate system in any collection
 */
enum class CoordinateSystem(@StringRes val shortName: Int, @StringRes val fullName: Int) {
    /**
     * Universal Traverse Mercator
     */
    UTM(R.string.utm, R.string.utm_full),

    /**
     * Military Grid Reference System
     */
    MGRS(R.string.mgrs, R.string.mgrs_full),

    /**
     * Kertau 1948
     */
    KERTAU(R.string.kertau, R.string.kertau),

    /**
     * World Geodetic System 1984
     *
     * Used in Google Maps
     */
    WGS84(R.string.wgs_84, R.string.wgs_84),

    /**
     * Ordnance Survey National Grid / British National Grid (BNG)
     */
    BNG(R.string.bng, R.string.bng_full),

    /**
     * Maidenhead Locator System (QTH Locator)
     */
    QTH(R.string.qth, R.string.qth_full),

    ;

    companion object {
        private val map = values().withIndex().associateBy { it.index }
        private val indices = values().withIndex().associateBy { it.value }

        val shortNames: List<Int> = values().map(CoordinateSystem::shortName)
        val fullNames = values().map(CoordinateSystem::fullName)

        /**
         * Returns the [CoordinateSystem] at the given index
         *
         * @param index
         * @return
         */
        fun atIndex(index: Int): CoordinateSystem {
            return map[index]?.value ?: throw IllegalArgumentException("Invalid CoordinateSystem index: $index")
        }
    }

    /**
     * The index of the [CoordinateSystem]
     */
    val index: Int
        get() = indices[this]!!.index
}
