package com.nujiak.recce.mapping.parsers

import androidx.core.text.isDigitsOnly
import com.google.android.gms.maps.model.LatLng
import com.nujiak.recce.mapping.Coordinate
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateReferenceSystem
import org.locationtech.proj4j.CoordinateTransform
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import kotlin.math.pow

/**
 * Parses Kertau 1948 grids
 */
object KertauParser : Parser {
    private const val EPSG_CODE_WGS_84: Short = 4326

    private const val KERTAU_1948_NAME = "Kertau 1948"
    private const val KERTAU_1948_PROJ_STRING = "+proj=omerc +lat_0=4 +lonc=102.25 +alpha=323.0257905 +k=0.99984 +x_0=804670.24 +y_0=0 +no_uoff +gamma=323.1301023611111 +a=6377295.664 +b=6356094.667915204 +units=m +no_defs +towgs84=-11,851,5"

    private val crsFactory = CRSFactory()
    private val ctFactory = CoordinateTransformFactory()

    private val wgs84Crs: CoordinateReferenceSystem by lazy {
        crsFactory.createFromName("EPSG:$EPSG_CODE_WGS_84")
    }
    private val kertau1948Crs: CoordinateReferenceSystem by lazy {
        crsFactory.createFromParameters(KERTAU_1948_NAME, KERTAU_1948_PROJ_STRING)
    }

    private val kertau1948ToWgs84Transform: CoordinateTransform by lazy {
        ctFactory.createTransform(kertau1948Crs, wgs84Crs)
    }

    override fun parse(s: String): Coordinate? {
        val groups = s.trim().split("[,;\\s]+".toRegex())
        if (groups.size != 2) {
            return null
        }

        val x = shiftToMagnitude(groups[0]) ?: return null
        val y = shiftToMagnitude(groups[1]) ?: return null

        val sourceCoord = ProjCoordinate(x, y)
        val resultCoord = ProjCoordinate()

        kertau1948ToWgs84Transform.transform(sourceCoord, resultCoord)

        val latLng = LatLng(resultCoord.y, resultCoord.x)

        return Coordinate.of(latLng, x, y)
    }

    private fun shiftToMagnitude(s: String, magnitude: Int = 6): Double? {
        if (!s.isDigitsOnly()) {
            return null
        }
        return s.toIntOrNull()?.times(10.0.pow(magnitude - s.length))
    }
}
