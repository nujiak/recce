package com.nujiak.recce.enums

import com.google.android.gms.maps.GoogleMap

/**
 * SharedPreferences keys used by the application
 *
 * @property key name of the key used to store SharedPreferences values
 */
enum class SharedPrefsKey(val key: String) {
    /**
     * Coordinate system used by the application. See [CoordinateSystem]
     */
    COORDINATE_SYSTEM("coordinate_system"),

    /**
     * Angle unit used by the application. See [AngleUnit]
     */
    ANGLE_UNIT("angle_unit"),

    /**
     * Theme used by the application. See [ThemePreference]
     */
    THEME_PREF("theme_pref"),

    /**
     * Map type used in the maps. Values stored with this key include [GoogleMap.MAP_TYPE_NORMAL],
     * [GoogleMap.MAP_TYPE_SATELLITE], [GoogleMap.MAP_TYPE_HYBRID]
     *
     */
    MAP_TYPE("map_type"),

    /**
     * Whether the chains guide has been shown to the user before
     */
    CHAINS_GUIDE_SHOWN("chains_guide_shown"),

    /**
     * Whether the saved items should be in sorted in ascending order, following the
     * [SORT_BY] order.
     */
    SORT_ASCENDING("sort_ascending"),

    /**
     * Order to sort the saved items by. See [SORT_BY]
     */
    SORT_BY("sort_by"),

    /**
     * Whether the Onboarding screen has been showing to the user before
     */
    ONBOARDING_COMPLETED("onboarding_complete"),
}