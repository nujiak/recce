package com.nujiak.reconnaissance.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ReconDatabaseDao {

    /*
     Pins
     */

    @Insert
    fun insert(pin: Pin): Long

    @Update
    fun update(pin: Pin)

    @Query("SELECT * FROM pins_table WHERE pinId == :pinId LIMIT 1")
    fun getPin(pinId: Long): Pin

    @Query("SELECT * FROM pins_table ORDER BY pinId DESC LIMIT 1")
    suspend fun getLastPin(): Pin

    @Query("DELETE FROM pins_table WHERE pinId == :pinId")
    fun deletePin(pinId: Long)

    @Query("SELECT * FROM pins_table ORDER BY pinId DESC")
    fun getAllPins(): LiveData<List<Pin>>

    /*
     Chains
     */

    @Insert
    fun insert(chain: Chain): Long

    @Update
    fun update(chain: Chain)

    @Query("SELECT * FROM chains_table WHERE chainId == :chainId LIMIT 1")
    fun getChain(chainId: Long): Chain

    @Query("DELETE FROM chains_table WHERE chainId == :chainId")
    fun deleteChain(chainId: Long)

    @Query("SELECT * FROM chains_table ORDER BY chainId DESC")
    fun getAllChains(): LiveData<List<Chain>>

}