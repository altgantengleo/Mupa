package com.xeno.mupa.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import com.xeno.mupa.R
import com.xeno.mupa.core.domain.ArtistRepository
import com.xeno.mupa.core.domain.MusicSource
import com.xeno.mupa.core.domain.PreferencesRepository
import com.xeno.mupa.data.local.db.LocalFolderDao
import com.xeno.mupa.data.local.db.LocalTrackDao
import com.xeno.mupa.data.local.db.LyricsCacheDao
import com.xeno.mupa.data.local.db.MupaDatabase
import com.xeno.mupa.data.local.db.UserPlaylistDao
import com.xeno.mupa.data.lyrics.LrcLibLyricsProvider
import com.xeno.mupa.data.preferences.DataStorePreferencesRepository
import com.xeno.mupa.data.streaming.StreamingArtistRepository
import com.xeno.mupa.data.streaming.StreamingMusicSource
import com.xeno.mupa.engine.local.ScopedStorageAudioScanner
import com.xeno.mupa.playback.MupaAudioCache
import com.xeno.mupa.playback.PlaybackServiceConfig
import com.xeno.mupa.playback.QueuePersistenceStore
import com.xeno.mupa.playback.eq.FiveBandEqualizerProcessor
import com.xeno.mupa.streaming.aggregator.MultiProviderCatalogAggregator
import com.xeno.mupa.streaming.aggregator.ProviderRegistry
import com.xeno.mupa.streaming.aggregator.providers.AudiusCatalogProvider
import com.xeno.mupa.streaming.aggregator.providers.DeezerCatalogProvider
import com.xeno.mupa.streaming.aggregator.providers.ItunesCatalogProvider
import com.xeno.mupa.streaming.api.StreamingLyricsProvider
import com.xeno.mupa.streaming.api.StreamingRadioProvider
import com.xeno.mupa.streaming.innertube.InnerTubeProvider
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.mupaPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mupa_preferences",
)

@Module
@InstallIn(SingletonComponent::class)
object AppInfrastructureModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.mupaPreferencesDataStore

    @Provides
    @Singleton
    fun provideMupaDatabase(
        @ApplicationContext context: Context,
    ): MupaDatabase = Room.databaseBuilder(
        context,
        MupaDatabase::class.java,
        "mupa_local.db",
    ).fallbackToDestructiveMigration(dropAllTables = true).build()

    @Provides
    fun provideLocalTrackDao(db: MupaDatabase): LocalTrackDao = db.localTrackDao()

    @Provides
    fun provideLocalFolderDao(db: MupaDatabase): LocalFolderDao = db.localFolderDao()

    @Provides
    fun provideUserPlaylistDao(db: MupaDatabase): UserPlaylistDao = db.userPlaylistDao()

    @Provides
    fun provideLyricsCacheDao(db: MupaDatabase): LyricsCacheDao = db.lyricsCacheDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 12
        }
        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .build()
    }

    @Provides
    @Singleton
    fun provideInnerTubeProvider(client: OkHttpClient): InnerTubeProvider =
        InnerTubeProvider(client)

    @Provides
    @Singleton
    fun provideDeezerCatalogProvider(client: OkHttpClient): DeezerCatalogProvider =
        DeezerCatalogProvider(client)

    @Provides
    @Singleton
    fun provideItunesCatalogProvider(client: OkHttpClient): ItunesCatalogProvider =
        ItunesCatalogProvider(client)

    @Provides
    @Singleton
    fun provideAudiusCatalogProvider(client: OkHttpClient): AudiusCatalogProvider =
        AudiusCatalogProvider(client)

    @Provides
    @Singleton
    fun provideMultiProviderCatalogAggregator(
        deezer: DeezerCatalogProvider,
        itunes: ItunesCatalogProvider,
        innerTube: InnerTubeProvider,
        audius: AudiusCatalogProvider,
    ): MultiProviderCatalogAggregator = MultiProviderCatalogAggregator(
        providers = listOf(deezer, itunes, innerTube, audius),
    )

    @Provides
    @Singleton
    fun provideStreamingRadioProvider(innerTubeProvider: InnerTubeProvider): StreamingRadioProvider =
        innerTubeProvider

    @Provides
    @Singleton
    fun provideProviderRegistry(
        innerTubeProvider: InnerTubeProvider,
    ): ProviderRegistry = ProviderRegistry(
        providers = listOf(innerTubeProvider),
    )

    @Provides
    @Singleton
    fun providePlaybackServiceConfig(): PlaybackServiceConfig = PlaybackServiceConfig(
        notificationSmallIconRes = R.drawable.ic_notification,
        audioCacheMaxBytes = PlaybackServiceConfig.DEFAULT_AUDIO_CACHE_BYTES,
    )

    @Provides
    @Singleton
    fun provideAudioCache(
        @ApplicationContext context: Context,
        config: PlaybackServiceConfig,
    ): MupaAudioCache = MupaAudioCache(context, config.audioCacheMaxBytes)

    @Provides
    @Singleton
    fun provideScopedStorageAudioScanner(
        @ApplicationContext context: Context,
    ): ScopedStorageAudioScanner = ScopedStorageAudioScanner(context)

    @Provides
    @Singleton
    fun provideQueuePersistenceStore(
        @ApplicationContext context: Context,
    ): QueuePersistenceStore = QueuePersistenceStore(File(context.filesDir, "mupa_queue.json"))

    @Provides
    @Singleton
    fun provideFiveBandEqualizerProcessor(): FiveBandEqualizerProcessor =
        FiveBandEqualizerProcessor()

    @Provides
    @Singleton
    fun provideLrcLibLyricsProvider(client: OkHttpClient): LrcLibLyricsProvider =
        LrcLibLyricsProvider(client)

    @Provides
    @Singleton
    fun provideLyricsProvider(lrcLib: LrcLibLyricsProvider): StreamingLyricsProvider = lrcLib

    @Provides
    @Singleton
    @OptIn(UnstableApi::class)
    fun provideCustomAudioProcessors(
        equalizerProcessor: FiveBandEqualizerProcessor,
    ): List<@JvmSuppressWildcards AudioProcessor> = listOf(equalizerProcessor)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {
    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(
        impl: DataStorePreferencesRepository,
    ): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindArtistRepository(
        impl: StreamingArtistRepository,
    ): ArtistRepository

    @Binds
    @Singleton
    abstract fun bindMusicSource(
        impl: StreamingMusicSource,
    ): MusicSource
}
