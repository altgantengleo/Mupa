package com.xeno.mupa.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.xeno.mupa.core.domain.PreferencesRepository
import com.xeno.mupa.data.preferences.DataStorePreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.mupaPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mupa_preferences",
)

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.mupaPreferencesDataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {
    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(
        impl: DataStorePreferencesRepository,
    ): PreferencesRepository
}
