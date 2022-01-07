package com.nujiak.recce.enums

import java.lang.IllegalArgumentException

/**
 * Themes used by the application
 *
 * @property index
 */
enum class ThemePreference(val index: Int) {
    /**
     * Automatic day/night theme
     */
    AUTO(0),

    /**
     * Light theme
     */
    LIGHT(1),

    /**
     * Dark theme
     */
    DARK(2);

    companion object {
        private val map = values().associateBy(ThemePreference::index)

        /**
         * Returns the [ThemePreference] at the given index
         *
         * @param index
         * @return
         */
        fun atIndex(index: Int): ThemePreference {
            return map[index] ?: throw IllegalArgumentException("Invalid ThemePreference index: $index")
        }
    }
}
