package com.nujiak.reconnaissance.database

import android.os.Parcelable
import androidx.room.ColumnInfo
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

    @PrimaryKey(autoGenerate = true)
    val pinId: Long = 0L,

    @ColumnInfo(defaultValue = "")
    var group: String = ""

) : Parcelable