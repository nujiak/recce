package com.nujiak.reconnaissance.database

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.android.parcel.Parcelize

sealed class ReconData {
    abstract val name: String
    abstract val color: Int
    abstract val group: String
}

/**
 * Represents a point on the map defined by its latitude and longitude
 */
@Parcelize
@Entity(tableName = "pins_table")
data class Pin(

    override val name: String,

    val latitude: Double,

    val longitude: Double,

    override val color: Int = 0,

    @PrimaryKey(autoGenerate = true)
    val pinId: Long = 0L,

    @ColumnInfo(defaultValue = "")
    override val group: String = ""

) : Parcelable, ReconData()

@Parcelize
@Entity(tableName = "chains_table")
data class Chain(

    override val name: String,

    val data: String,

    override val color: Int = 0,

    @PrimaryKey(autoGenerate = true)
    val chainId: Long = 0L,

    @ColumnInfo(defaultValue = "")
    override val group: String = "",

    @ColumnInfo(defaultValue = "0")
    val cyclical: Boolean = false

) : Parcelable, ReconData()