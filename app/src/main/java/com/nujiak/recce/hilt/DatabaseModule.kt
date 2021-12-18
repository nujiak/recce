package com.nujiak.recce.hilt

import android.content.Context
import com.nujiak.recce.database.RecceDatabase
import com.nujiak.recce.database.RecceDatabaseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {
    @Provides
    fun provideRecceDatabaseDao(recceDatabase: RecceDatabase): RecceDatabaseDao {
        return recceDatabase.pinDatabaseDao
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context): RecceDatabase {
        return RecceDatabase.getInstance(appContext)
    }
}
