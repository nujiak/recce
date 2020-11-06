package com.nujiak.reconnaissance.hilt

import android.content.Context
import com.nujiak.reconnaissance.database.ReconDatabase
import com.nujiak.reconnaissance.database.ReconDatabaseDao
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