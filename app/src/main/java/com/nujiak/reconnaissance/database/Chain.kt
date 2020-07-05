package com.nujiak.reconnaissance.database

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.android.parcel.Parcelize

@Parcelize
@Entity(tableName = "chains_table")
data class Chain(

    val name: String,

    val data: String,

    val color: Int = 0,

    @PrimaryKey(autoGenerate = true)
    val chainId: Long = 0L,

    @ColumnInfo(defaultValue = "")
    val group: String = ""

) : Parcelable