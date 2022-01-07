package com.nujiak.recce.enums

import androidx.appcompat.app.AppCompatDelegate
import java.lang.IllegalArgumentException

/**
 * Themes used by the application
 *
 * @property index
 */
enum class ThemePreference(val index: Int, val mode: Int) {
    /**
     * Automatic day/night theme
     */
    AUTO(0, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),

    /**
     * Light theme
     */
    LIGHT(1, AppCompatDelegate.MODE_NIGHT_NO),

    /**
     * Dark theme
     */
    DARK(2, AppCompatDelegate.MODE_NIGHT_YES);

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
