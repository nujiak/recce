package com.nujiak.recce.hilt

import android.content.Context
import com.nujiak.recce.database.ReconDatabase
import com.nujiak.recce.database.ReconDatabaseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(ApplicationComponent::class)
class DatabaseModule {
    @Provides
    fun provideReconDatabaseDao(reconDatabase: ReconDatabase): ReconDatabaseDao {
        return reconDatabase.pinDatabaseDao
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context): ReconDatabase {
        return ReconDatabase.getInstance(appContext)
    }
}