package com.nujiak.recce.mapping

import com.google.android.gms.maps.model.LatLng
import kotlin.math.pow

open class Coordinate private constructor(
    val latLng: LatLng,
    val x: Double,
    val y: Double,
    val z: Double = Double.NaN
) {
    override fun toString(): String {
        return if (this.z.isNaN()) {
            "%.0f %.0f".format(this.x, this.y)
        } else {
            "%.0f %.0f %.0f".format(this.x.truncate(), this.y.truncate(), this.z.truncate())
        }
    }

    companion object {

        /**
         * Truncates a decimal to a specified [precision]
         *
         * @param precision number of decimal places to truncate to
         * @return
         */
        private fun Double.truncate(precision: Int = 0): Double {
            return if (precision == 0) {
                kotlin.math.floor(this)
            } else {
                val shift = 10f.pow(precision)
                kotlin.math.floor(this * shift) / shift
            }
        }

        /**
         * Formats a decimal as a string with specified [magnitude] and [precision]
         *
         * @param magnitude number of digits before decimal point
         * @param precision number of digits after decimal point
         * @return
         */
        private fun Double.format(magnitude: Int, precision: Int): String {
            return if (precision == 0) {
                "%.${precision}f".format(this.truncate()).padStart(magnitude, '0')
            } else {
                "%.${precision}f".format(this.truncate(precision)).padStart(magnitude + precision + 1, '0')
            }
        }

        /**
         * Returns a [LatLngCoordinate] to represent the given WGS84 coordinate
         *
         * @param latLng corresponding [LatLng] of the location
         * @return [LatLngCoordinate] representing the location
         */
        fun of(latLng: LatLng): Coordinate {
            return LatLngCoordinate(latLng)
        }

        /**
         * Returns a Coordinate to represent the given location in an unspecified GCS
         *
         * @param latLng corresponding [LatLng] of the location
         * @param x x ordinate of the point
         * @param y y ordinate of the point
         * @param z z ordinate of the point
         * @return [Coordinate] representing the location
         */
        fun of(latLng: LatLng, x: Double, y: Double, z: Double = Double.NaN): Coordinate {
            return Coordinate(latLng, x, y, z)
        }

        /**
         * Returns a [UtmCoordinate] to represent the given location in UTM
         *
         * @param latLng corresponding [LatLng] of the location
         * @param zone UTM zone
         * @param band UTM letter band
         * @param x easting
         * @param y northing
         * @return
         */
        fun of(latLng: LatLng, zone: Int, band: UtmUtils.UtmBand, x: Double, y: Double): Coordinate {
            return UtmCoordinate(latLng, zone, band, x, y)
        }

        /**
         * Returns a [MgrsCoordinate] to represent the given location in MGRS
         *
         * @param latLng corresponding [LatLng] of the location
         * @param zone MGRS / UTM zone
         * @param band MGRS / UTM letter band
         * @param eastingLetter MGRS easting letter
         * @param northingLetter MGRS northing letter
         * @param x easting
         * @param y northing
         * @return
         */
        fun of(
            latLng: LatLng,
            zone: Int,
            band: Char,
            eastingLetter: Char,
            northingLetter: Char,
            x: Double,
            y: Double,
        ): Coordinate {
            return MgrsCoordinate(latLng, zone, band, eastingLetter, northingLetter, x, y)
        }
    }

    /**
     * Represents a location in latitude and longitude
     *
     * @constructor
     * Creates a [LatLngCoordinate] for the given point
     *
     * @param latitude latitude of the location
     * @param longitude longitude of the location
     */
    private class LatLngCoordinate(latitude: Double, longitude: Double) :
        Coordinate(LatLng(latitude, longitude), longitude, latitude) {

        constructor(latLng: LatLng) : this(latLng.latitude, latLng.longitude)

        override fun toString(): String {
            return "${this.y.format(0, 6)} ${this.x.format(0, 6)}"
        }
    }

    /**
     * Represents a location in Universal Traverse Mercator (UTM)
     *
     * @property zone UTM zone
     * @property band UTM letter band
     * @constructor
     * creates a
     *
     * @param latLng corresponding [LatLng] of the location
     * @param x easting
     * @param y northing
     */
    private class UtmCoordinate(
        latLng: LatLng,
        val zone: Int,
        val band: UtmUtils.UtmBand,
        x: Double,
        y: Double,
    ) : Coordinate(latLng, x, y) {
        override fun toString(): String {
            return "$zone${band.letter} ${this.x.format(6, 0)} ${this.y.format(6, 0)}"
        }
    }

    /**
     * Represents a location in Military Grid Reference System (MGRS)
     *
     * @property zone MGRS / UTM zone
     * @property band MGRS / UTM letter band
     * @property eastingLetter MGRS easting letter
     * @property northingLetter MGRS northing letter
     * @constructor
     * Creates a [MgrsCoordinate] for the given point
     *
     * @param latLng corresponding [LatLng] of the location
     * @param x easting
     * @param y northing
     */
    private class MgrsCoordinate(
        latLng: LatLng,
        private val zone: Int,
        private val band: Char,
        private val eastingLetter: Char,
        private val northingLetter: Char,
        x: Double,
        y: Double,
    ) : Coordinate(latLng, x, y) {
        override fun toString(): String {
            return "$zone$band$eastingLetter$northingLetter ${this.x.format(5, 0)} ${this.y.format(5, 0)}"
        }
    }
}
