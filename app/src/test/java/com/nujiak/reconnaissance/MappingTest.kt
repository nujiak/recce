package com.nujiak.reconnaissance

import com.nujiak.recce.mapping.getKertauGrids
import com.nujiak.recce.mapping.getLatLngFromKertau
import org.junit.Assert.assertEquals
import org.junit.Test

class MappingTest {

    companion object {
        val kertauGrids = arrayOf(
            Pair(187074.0, 736637.0),
            Pair(601405.0, 530686.0),
            Pair(649446.0, 150856.0)
        )
        val wgsCoords = arrayOf(
            Pair(6.650785, 99.659438),
            Pair(4.799241, 103.405876),
            Pair(1.364208, 103.839614)
        )
    }

    @Test
    fun kertauForwardAccuracyTest() {
        for ((index, grids) in kertauGrids.withIndex()) {
            val latLngPair = getLatLngFromKertau(grids.first, grids.second)
            val trueCoords = wgsCoords[index]
            assertEquals(latLngPair.first, trueCoords.first, 0.00001)
            assertEquals(latLngPair.second, trueCoords.second, 0.00001)
        }
    }

    @Test
    fun kertauReverseAccuracyTest() {
        for ((index, coords) in wgsCoords.withIndex()) {
            val grids = getKertauGrids(coords.first, coords.second)
            val trueGrids = kertauGrids[index]
            grids?.let {
                assertEquals(it.first, trueGrids.first, 1.0)
                assertEquals(it.second, trueGrids.second, 1.0)
            }
        }
    }
}