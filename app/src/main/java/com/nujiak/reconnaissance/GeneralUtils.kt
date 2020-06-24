package com.nujiak.reconnaissance

import android.content.res.Resources
import com.nujiak.reconnaissance.mapping.getKertauGridsString
import com.nujiak.reconnaissance.mapping.getMgrsData
import com.nujiak.reconnaissance.mapping.getUtmData
import com.nujiak.reconnaissance.mapping.toSingleLine
import com.nujiak.reconnaissance.modalsheets.SettingsSheet

fun getGridString(latDeg: Double, lngDeg: Double, coordSysId: Int, resources: Resources): String {
    return when (coordSysId) {
        SettingsSheet.COORD_SYS_ID_UTM -> {
            getUtmData(
                latDeg,
                lngDeg
            )?.toSingleLine(5)
        }
        SettingsSheet.COORD_SYS_ID_MGRS -> {
            getMgrsData(latDeg, lngDeg)?.toSingleLine(includeWhitespace = true)
        }
        SettingsSheet.COORD_SYS_ID_KERTAU -> {
            getKertauGridsString(
                latDeg,
                lngDeg
            )
        }
        else -> throw IllegalArgumentException("Invalid coordinate system index: $coordSysId")
    } ?: resources.getString(R.string.not_available)
}