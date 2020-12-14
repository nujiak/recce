package com.nujiak.recce.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Pin::class, Chain::class], version = 5, exportSchema = true)
abstract class RecceDatabase : RoomDatabase() {

    abstract val pinDatabaseDao: RecceDatabaseDao

    companion object {
        /**
         * INSTANCE will keep a reference to any database returned via getInstance.
         *
         * This will help us avoid repeatedly initializing the database, which is expensive.
         *
         *  The value of a volatile variable will never be cached, and all writes and
         *  reads will be done to and from the main memory. It means that changes made by one
         *  thread to shared data are visible to other threads.
         */
        @Volatile
        private var INSTANCE: RecceDatabase? = null

        fun getInstance(context: Context): RecceDatabase {

            synchronized(this) {

                // Copy the current value of INSTANCE to a local variable to make use of smart cast.
                var instance = INSTANCE

                // If instance is `null` make a new database instance.
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        RecceDatabase::class.java,
                        "recce_database"
                    ).build()
                    // Assign INSTANCE to the newly created database.
                    INSTANCE = instance
                }

                // Return instance; smart cast to be non-null.
                return instance
            }
        }
    }

}