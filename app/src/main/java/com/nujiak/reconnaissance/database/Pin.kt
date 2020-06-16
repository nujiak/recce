package com.nujiak.reconnaissance.database

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.android.parcel.Parcelize

/**
 * Represents a point on the map defined by its latitude and longitude
 */
@Parcelize
@Entity(tableName = "pins_table")
data class Pin(

    val name: String,

    val latitude: Double,

    val longitude: Double,

    val color: Int = 0,

    // var group: String?,

    @PrimaryKey(autoGenerate = true)
    val pinId: Long = 0L

) : Parcelable