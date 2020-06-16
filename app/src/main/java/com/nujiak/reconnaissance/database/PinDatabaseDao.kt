package com.nujiak.reconnaissance.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PinDatabaseDao {

    @Insert
    fun insert(pin: Pin): Long

    @Update
    fun update(pin: Pin)

    @Query("SELECT * FROM pins_table WHERE pinId == :pinId LIMIT 1")
    fun get(pinId: Long): Pin

    @Query("SELECT * FROM pins_table ORDER BY pinId DESC LIMIT 1")
    suspend fun getLast(): Pin

    @Query("DELETE FROM pins_table WHERE pinId == :pinId")
    fun delete(pinId: Long)

    @Query("SELECT * FROM pins_table ORDER BY pinId DESC")
    fun getAllPins(): LiveData<List<Pin>>

    @Query("DELETE FROM pins_table")
    fun deleteAll()
}