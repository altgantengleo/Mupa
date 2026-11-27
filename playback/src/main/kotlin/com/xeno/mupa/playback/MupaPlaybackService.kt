package com.xeno.mupa.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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
    private val mainHandler = Handler(Looper.getMainLooper())

    private val sessionCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val baseResult = super.onConnect(session, controller)
            val sessionCommands = baseResult.availableSessionCommands.buildUpon()
                .add(COMMAND_TOGGLE_FAVORITE)
                .add(COMMAND_TOGGLE_SHUFFLE)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(baseResult.availablePlayerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_FAVORITE -> {
                    mainHandler.post {
                        MupaMediaSessionBridge.dispatchToggleFavorite()
                        refreshSessionCustomLayout()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_TOGGLE_SHUFFLE -> {
                    mainHandler.post {
                        MupaMediaSessionBridge.dispatchToggleShuffle()
                        refreshSessionCustomLayout()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private fun buildSessionActivityPendingIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_OPEN_NOW_PLAYING, true)
        } ?: return null
        return PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildCustomLayoutButtons(): List<CommandButton> {
        val isFav = MupaMediaSessionBridge.isCurrentFavorite
        val isShuf = MupaMediaSessionBridge.isShuffleEnabled
        val favoriteBtn = CommandButton.Builder()
            .setDisplayName(if (isFav) "Hapus Favorit" else "Favorit")
            .setIconResId(if (isFav) serviceConfig.favoriteOnIconRes else serviceConfig.favoriteOffIconRes)
            .setSessionCommand(COMMAND_TOGGLE_FAVORITE)
            .setEnabled(true)
            .build()
        val shuffleBtn = CommandButton.Builder()
            .setDisplayName(if (isShuf) "Matikan Acak" else "Acak")
            .setIconResId(if (isShuf) serviceConfig.shuffleOnIconRes else serviceConfig.shuffleOffIconRes)
            .setSessionCommand(COMMAND_TOGGLE_SHUFFLE)
            .setEnabled(true)
            .build()
        return listOf(favoriteBtn, shuffleBtn)
    }

    private fun refreshSessionCustomLayout() {
        val session = mediaLibrarySession ?: return
        val buttons = buildCustomLayoutButtons()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            session.setCustomLayout(buttons)
        } else {
            mainHandler.post { mediaLibrarySession?.setCustomLayout(buttons) }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .build().apply {
                setSmallIcon(serviceConfig.notificationSmallIconRes)
            }
        setMediaNotificationProvider(notificationProvider)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 45_000, 650, 1_400)
            .setTargetBufferBytes(10 * 1024 * 1024)
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
        val sessionBuilder = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            sessionCallback,
        ).setCustomLayout(buildCustomLayoutButtons())
        buildSessionActivityPendingIntent()?.let { pendingIntent ->
            sessionBuilder.setSessionActivity(pendingIntent)
        }
        mediaLibrarySession = sessionBuilder.build()
        MupaMediaSessionBridge.attachSessionRefresher { refreshSessionCustomLayout() }
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
                refreshSessionCustomLayout()
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
        MupaMediaSessionBridge.attachSessionRefresher(null)
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
        const val EXTRA_OPEN_NOW_PLAYING = "com.xeno.mupa.extra.OPEN_NOW_PLAYING"
        const val ACTION_TOGGLE_FAVORITE = "com.xeno.mupa.action.TOGGLE_FAVORITE"
        const val ACTION_TOGGLE_SHUFFLE = "com.xeno.mupa.action.TOGGLE_SHUFFLE"
        private const val SESSION_ACTIVITY_REQUEST_CODE = 1001

        private val COMMAND_TOGGLE_FAVORITE = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
        private val COMMAND_TOGGLE_SHUFFLE = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)

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
