package com.xeno.mupa.playback

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.xeno.mupa.streaming.aggregator.ProviderRegistry
import com.xeno.mupa.streaming.api.StreamLookupRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.IOException
import javax.inject.Inject

/**
 * Layanan pemutaran latar belakang Mupa berbasis Media3 MediaLibraryService + ExoPlayer.
 * - Menggunakan ic_notification dari PlaybackServiceConfig untuk notifikasi pemutar.
 * - Mendukung pemutaran streaming via ProviderRegistry (resolusi lazy + cache 500 MB)
 *   dan pemutaran file lokal Android via content:// URI (Scoped Storage).
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MupaPlaybackService : MediaLibraryService() {

    @Inject
    lateinit var providerRegistry: ProviderRegistry

    @Inject
    lateinit var audioCache: MupaAudioCache

    @Inject
    lateinit var serviceConfig: PlaybackServiceConfig

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var customAudioProcessors: List<@JvmSuppressWildcards AudioProcessor>

    private var player: ExoPlayer? = null
    private var crossfadeEngine: MupaCrossfadeEngine? = null
    private var mediaLibrarySession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .build().apply {
                setSmallIcon(serviceConfig.notificationSmallIconRes)
            }
        setMediaNotificationProvider(notificationProvider)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
            .setTargetBufferBytes(6 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val upstreamFactory = OkHttpDataSource.Factory(okHttpClient)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(audioCache.simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val defaultFactory = DefaultDataSource.Factory(this, cacheFactory)
        val resolvingFactory = ResolvingDataSource.Factory(defaultFactory) { dataSpec ->
            resolveDataSpec(dataSpec)
        }

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(customAudioProcessors.toTypedArray())
                .build()
        }.setEnableDecoderFallback(true)

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player = exoPlayer
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            object : MediaLibrarySession.Callback {},
        ).build()
        crossfadeEngine = MupaCrossfadeEngine(
            context = this,
            initialActivePlayer = exoPlayer,
            dataSourceFactory = resolvingFactory,
            audioProcessorsFactory = {
                customAudioProcessors.mapNotNull { processor ->
                    when (processor) {
                        is com.xeno.mupa.playback.eq.FiveBandEqualizerProcessor -> processor.createSyncedClone()
                        else -> null
                    }
                }
            },
            onReleaseProcessors = { clones ->
                val eqMasters = customAudioProcessors.filterIsInstance<com.xeno.mupa.playback.eq.FiveBandEqualizerProcessor>()
                clones.filterIsInstance<com.xeno.mupa.playback.eq.FiveBandEqualizerProcessor>().forEach { clone ->
                    eqMasters.forEach { master -> master.removeSyncedClone(clone) }
                }
            },
            onActivePlayerSwapped = { newActive ->
                player = newActive
                mediaLibrarySession?.player = newActive
            },
        ).also { it.attach() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaLibrarySession?.player
        if (currentPlayer == null ||
            !currentPlayer.playWhenReady ||
            currentPlayer.mediaItemCount == 0 ||
            currentPlayer.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        crossfadeEngine?.release()
        crossfadeEngine = null
        player = null
        super.onDestroy()
    }

    private fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != STREAM_SCHEME) {
            return dataSpec
        }

        val trackId = uri.host.orEmpty().ifBlank { uri.lastPathSegment.orEmpty() }
        val title = uri.getQueryParameter("title").orEmpty()
        val artist = uri.getQueryParameter("artist").orEmpty()
        if (trackId.isBlank()) {
            throw IOException("TrackId streaming kosong")
        }

        val outcome = runBlocking(Dispatchers.IO) {
            providerRegistry.resolve(
                StreamLookupRequest(
                    trackId = trackId,
                    title = title,
                    artistName = artist,
                ),
            )
        }.getOrElse { err ->
            throw IOException("Gagal menyelesaikan stream untuk $trackId: ${err.message}", err)
        }

        return dataSpec.buildUpon()
            .setUri(Uri.parse(outcome.stream.url))
            .setKey("mupa_stream_$trackId")
            .setHttpRequestHeaders(outcome.stream.headers)
            .build()
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "mupa_playback_channel"
        const val STREAM_SCHEME = "mupa-stream"

        fun buildStreamingUri(trackId: String, title: String, artist: String): Uri =
            Uri.Builder()
                .scheme(STREAM_SCHEME)
                .authority(trackId)
                .appendQueryParameter("title", title)
                .appendQueryParameter("artist", artist)
                .build()

        fun buildMediaItem(
            trackId: String,
            title: String,
            artist: String,
            albumTitle: String? = null,
            artworkUrl: String? = null,
            localContentUri: String? = null,
        ): MediaItem {
            val targetUri = when {
                !localContentUri.isNullOrBlank() -> Uri.parse(localContentUri)
                trackId.startsWith("content://") || trackId.startsWith("file://") -> Uri.parse(trackId)
                else -> buildStreamingUri(trackId, title, artist)
            }
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .apply {
                    if (!albumTitle.isNullOrBlank()) setAlbumTitle(albumTitle)
                    if (!artworkUrl.isNullOrBlank()) setArtworkUri(Uri.parse(artworkUrl))
                }
                .build()
            return MediaItem.Builder()
                .setMediaId(trackId)
                .setUri(targetUri)
                .setMediaMetadata(metadata)
                .build()
        }
    }
}
