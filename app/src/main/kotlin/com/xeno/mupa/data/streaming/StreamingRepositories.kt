package com.xeno.mupa.data.streaming

import android.content.Context
import com.xeno.mupa.core.domain.ArtistRepository
import com.xeno.mupa.core.domain.MusicSource
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.data.fake.FakeArtistRepository
import com.xeno.mupa.data.fake.FakeMusicSource
import com.xeno.mupa.engine.local.LocalTrackItem
import com.xeno.mupa.engine.local.MixMupaLocalRecommender
import com.xeno.mupa.engine.local.ScopedStorageAudioScanner
import com.xeno.mupa.streaming.innertube.InnerTubeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class CachedArtistRecord(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val genre: String? = null,
)

/**
 * Menggabungkan artis dari YouTube Music (foto HD asli), library lokal (Scoped Storage),
 * dan katalog cadangan bila offline.
 * Menyimpan cache ID->Artis secara persisten agar artis online (UC...) pilihan onboarding
 * tetap dikenali Beranda & Mix Mupa setelah restart aplikasi.
 */
@Singleton
class StreamingArtistRepository @Inject constructor(
    @ApplicationContext appContext: Context,
    private val innerTubeProvider: InnerTubeProvider,
    private val localScanner: ScopedStorageAudioScanner,
    private val fallbackCurated: FakeArtistRepository,
) : ArtistRepository {

    private val cacheFile = File(appContext.filesDir, "mupa_known_artists.json")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val knownArtistsById = ConcurrentHashMap<String, Artist>()

    @Volatile
    private var cachedTrendingArtists: List<Artist> = emptyList()

    init {
        loadDiskArtistCache()
    }

    override suspend fun getSuggestedArtists(query: String): Result<List<Artist>> {
        val trimmed = query.trim()
        val localItems = scanLocalItemsSafe()
        val localArtists = MixMupaLocalRecommender.aggregateLocalArtists(localItems)
            .filter { trimmed.isEmpty() || it.name.contains(trimmed, ignoreCase = true) }

        val onlineArtists = if (trimmed.length < 2) {
            cachedTrendingArtists.ifEmpty {
                innerTubeProvider.getTrendingArtists(limit = 24)
                    .getOrDefault(emptyList())
                    .also { fetched ->
                        if (fetched.isNotEmpty()) cachedTrendingArtists = fetched
                    }
            }
        } else {
            innerTubeProvider.searchArtists(trimmed, limit = 18).getOrDefault(emptyList())
        }

        val merged = buildList {
            addAll(onlineArtists)
            addAll(localArtists)
            if (onlineArtists.isEmpty()) {
                addAll(fallbackCurated.getSuggestedArtists(trimmed).getOrDefault(emptyList()))
            }
        }.distinctBy { it.name.lowercase() }

        if (merged.isNotEmpty()) {
            merged.forEach { knownArtistsById[it.id] = it }
            saveDiskArtistCache()
        }
        return Result.success(merged)
    }

    override suspend fun getArtistsByIds(ids: Set<String>): Result<List<Artist>> {
        if (ids.isEmpty()) return Result.success(emptyList())
        val cached = ids.mapNotNull { knownArtistsById[it] }
        val localArtists = MixMupaLocalRecommender.aggregateLocalArtists(scanLocalItemsSafe())
            .filter { it.id in ids }
        val curated = fallbackCurated.getArtistsByIds(ids).getOrDefault(emptyList())
        return Result.success((cached + localArtists + curated).distinctBy { it.id })
    }

    private fun loadDiskArtistCache() {
        runCatching {
            if (!cacheFile.exists()) return
            val records = json.decodeFromString<List<CachedArtistRecord>>(cacheFile.readText())
            records.forEach { rec ->
                knownArtistsById[rec.id] = Artist(
                    id = rec.id,
                    name = rec.name,
                    artworkUrl = rec.artworkUrl,
                    genre = rec.genre,
                    sourceType = AudioSourceType.STREAMING,
                )
            }
        }
    }

    private fun saveDiskArtistCache() {
        runCatching {
            val records = knownArtistsById.values.take(120).map { artist ->
                CachedArtistRecord(
                    id = artist.id,
                    name = artist.name,
                    artworkUrl = artist.artworkUrl,
                    genre = artist.genre,
                )
            }
            cacheFile.writeText(json.encodeToString(records))
        }
    }

    private suspend fun scanLocalItemsSafe(): List<LocalTrackItem> =
        runCatching {
            localScanner.scanMediaStoreAudio().map { entry ->
                LocalTrackItem(
                    track = Track(
                        id = entry.contentUri.toString(),
                        title = entry.title,
                        artistName = entry.artist,
                        albumTitle = entry.album,
                        durationMs = entry.durationMs,
                        sourceType = AudioSourceType.LOCAL,
                        originalFileName = entry.originalFileName,
                    ),
                    contentUri = entry.contentUri.toString(),
                    importedAtMs = entry.dateAddedEpochMs,
                )
            }
        }.getOrDefault(emptyList())
}

/**
 * Menggabungkan pustaka lokal (Scoped Storage + Mix Mupa recommender) dan streaming InnerTube nyata
 * berdasarkan artis pilihan onboarding.
 */
@Singleton
class StreamingMusicSource @Inject constructor(
    private val innerTubeProvider: InnerTubeProvider,
    private val artistRepository: StreamingArtistRepository,
    private val localScanner: ScopedStorageAudioScanner,
    private val fallbackCatalog: FakeMusicSource,
) : MusicSource {

    override val sourceId: String = "mupa_unified_source"

    override suspend fun getHomeRecommendations(seedArtistIds: Set<String>): Result<List<Playlist>> =
        coroutineScope {
            val localItems = scanLocalItemsSafe()
            val selectedArtists = artistRepository.getArtistsByIds(seedArtistIds).getOrDefault(emptyList())

            val seedNames = selectedArtists.map { it.name }.take(4).ifEmpty {
                listOf("Tulus", "Hindia", "Bernadya")
            }
            val onlineTracksByArtist = seedNames.map { artistName ->
                async {
                    innerTubeProvider.searchTracks(artistName, limit = 6).getOrDefault(emptyList())
                }
            }.awaitAll().flatten().distinctBy { it.id }

            if (onlineTracksByArtist.isEmpty() && localItems.isEmpty()) {
                return@coroutineScope fallbackCatalog.getHomeRecommendations(seedArtistIds)
            }

            val mixLocal = MixMupaLocalRecommender.buildMixMupa(localItems, limit = 12).map { it.track }
            val repeatLocal = MixMupaLocalRecommender.buildRepeatAgain(localItems, limit = 12)
            val unplayedLocal = MixMupaLocalRecommender.buildUnplayed(localItems, limit = 12)

            val forYouTracks = onlineTracksByArtist.take(12)
            val mixTracks = (mixLocal + onlineTracksByArtist.shuffled().take(12))
                .distinctBy { it.id }
                .take(12)
            val repeatTracks = (repeatLocal + onlineTracksByArtist.drop(4).take(10))
                .distinctBy { it.id }
                .take(12)
            val unplayedTracks = (unplayedLocal + onlineTracksByArtist.reversed().take(10))
                .distinctBy { it.id }
                .take(12)

            val playlists = buildList {
                if (forYouTracks.isNotEmpty()) {
                    add(
                        Playlist(
                            id = "shelf_for_you",
                            title = "Untukmu",
                            subtitle = if (selectedArtists.isNotEmpty()) {
                                "Berdasarkan ${selectedArtists.take(3).joinToString(", ") { it.name }}"
                            } else {
                                "Pilihan streaming untukmu"
                            },
                            tracks = forYouTracks,
                            isSystemGenerated = true,
                        ),
                    )
                }
                if (mixTracks.isNotEmpty()) {
                    add(
                        Playlist(
                            id = "shelf_mix_mupa",
                            title = "Mix Mupa",
                            subtitle = "Racikan dari selera artis & pustaka lokalmu",
                            tracks = mixTracks,
                            isSystemGenerated = true,
                        ),
                    )
                }
                if (repeatTracks.isNotEmpty()) {
                    add(
                        Playlist(
                            id = "shelf_repeat",
                            title = "Ulangi Lagi",
                            subtitle = "Lagu pilihan yang asyik diputar ulang",
                            tracks = repeatTracks,
                            isSystemGenerated = true,
                        ),
                    )
                }
                if (unplayedTracks.isNotEmpty()) {
                    add(
                        Playlist(
                            id = "shelf_unplayed",
                            title = "Belum Diputar",
                            subtitle = "Temuan baru untuk kamu jelajahi",
                            tracks = unplayedTracks,
                            isSystemGenerated = true,
                        ),
                    )
                }
            }
            Result.success(playlists)
        }

    override suspend fun searchTracks(query: String): Result<List<Track>> {
        val trimmed = query.trim()
        val localTracks = scanLocalItemsSafe().map { it.track }.filter { track ->
            trimmed.isEmpty() ||
                track.title.contains(trimmed, ignoreCase = true) ||
                track.artistName.contains(trimmed, ignoreCase = true)
        }
        if (trimmed.length < 2) {
            val trending = innerTubeProvider.searchTracks("Lagu Pop Indonesia Terbaru", limit = 12)
                .getOrDefault(emptyList())
            val fallback = if (trending.isEmpty() && localTracks.isEmpty()) {
                fallbackCatalog.searchTracks("").getOrDefault(emptyList())
            } else {
                emptyList()
            }
            return Result.success((localTracks + trending + fallback).distinctBy { it.id })
        }
        val online = innerTubeProvider.searchTracks(trimmed, limit = 20).getOrDefault(emptyList())
        return Result.success((localTracks + online).distinctBy { it.id })
    }

    override suspend fun getLibraryTracks(): Result<List<Track>> {
        val localTracks = scanLocalItemsSafe().map { it.track }
        if (localTracks.isNotEmpty()) {
            return Result.success(localTracks)
        }
        return fallbackCatalog.getLibraryTracks()
    }

    private suspend fun scanLocalItemsSafe(): List<LocalTrackItem> =
        runCatching {
            localScanner.scanMediaStoreAudio().map { entry ->
                LocalTrackItem(
                    track = Track(
                        id = entry.contentUri.toString(),
                        title = entry.title,
                        artistName = entry.artist,
                        albumTitle = entry.album,
                        durationMs = entry.durationMs,
                        sourceType = AudioSourceType.LOCAL,
                        originalFileName = entry.originalFileName,
                    ),
                    contentUri = entry.contentUri.toString(),
                    importedAtMs = entry.dateAddedEpochMs,
                )
            }
        }.getOrDefault(emptyList())
}
