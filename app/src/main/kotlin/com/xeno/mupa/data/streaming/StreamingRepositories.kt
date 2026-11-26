package com.xeno.mupa.data.streaming

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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menggabungkan artis dari library lokal (Scoped Storage / Rust engine), katalog kurasi,
 * dan pencarian streaming InnerTube agar Onboarding & Beranda memakai sumber lokal + online.
 */
@Singleton
class StreamingArtistRepository @Inject constructor(
    private val innerTubeProvider: InnerTubeProvider,
    private val localScanner: ScopedStorageAudioScanner,
    private val fallbackCurated: FakeArtistRepository,
) : ArtistRepository {

    override suspend fun getSuggestedArtists(query: String): Result<List<Artist>> {
        val trimmed = query.trim()
        val localItems = scanLocalItemsSafe()
        val localArtists = MixMupaLocalRecommender.aggregateLocalArtists(localItems)
            .filter { trimmed.isEmpty() || it.name.contains(trimmed, ignoreCase = true) }
        val curated = fallbackCurated.getSuggestedArtists(trimmed).getOrDefault(emptyList())

        if (trimmed.length < 2) {
            return Result.success((localArtists + curated).distinctBy { it.name.lowercase() })
        }
        val online = innerTubeProvider.searchArtists(trimmed, limit = 12).getOrDefault(emptyList())
        return Result.success((localArtists + online + curated).distinctBy { it.name.lowercase() })
    }

    override suspend fun getArtistsByIds(ids: Set<String>): Result<List<Artist>> {
        val localArtists = MixMupaLocalRecommender.aggregateLocalArtists(scanLocalItemsSafe())
            .filter { it.id in ids }
        val curated = fallbackCurated.getArtistsByIds(ids).getOrDefault(emptyList())
        return Result.success((localArtists + curated).distinctBy { it.id })
    }

    private suspend fun scanLocalItemsSafe(): List<LocalTrackItem> =
        runCatching {
            localScanner.scanMediaStoreAudio().map { entry ->
                LocalTrackItem(
                    track = Track(
                        id = entry.id,
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
 * Menggabungkan pustaka lokal (Scoped Storage + Mix Mupa recommender) dan streaming InnerTube
 * di balik satu interface MusicSource.
 */
@Singleton
class StreamingMusicSource @Inject constructor(
    private val innerTubeProvider: InnerTubeProvider,
    private val localScanner: ScopedStorageAudioScanner,
    private val fallbackCatalog: FakeMusicSource,
) : MusicSource {

    override val sourceId: String = "mupa_unified_source"

    override suspend fun getHomeRecommendations(seedArtistIds: Set<String>): Result<List<Playlist>> {
        val localItems = scanLocalItemsSafe()
        val baseShelves = fallbackCatalog.getHomeRecommendations(seedArtistIds).getOrDefault(emptyList())
        if (localItems.isEmpty()) {
            return Result.success(baseShelves)
        }

        val mixTracks = MixMupaLocalRecommender.buildMixMupa(localItems, limit = 12).map { it.track }
        val repeatTracks = MixMupaLocalRecommender.buildRepeatAgain(localItems, limit = 12)
        val unplayedTracks = MixMupaLocalRecommender.buildUnplayed(localItems, limit = 12)

        val merged = baseShelves.map { shelf ->
            when (shelf.id) {
                "shelf_mix_mupa" -> shelf.copy(tracks = (mixTracks + shelf.tracks).distinctBy { it.id }.take(12))
                "shelf_repeat" -> shelf.copy(tracks = (repeatTracks + shelf.tracks).distinctBy { it.id }.take(12))
                "shelf_unplayed" -> shelf.copy(tracks = (unplayedTracks + shelf.tracks).distinctBy { it.id }.take(12))
                else -> shelf
            }
        }
        return Result.success(merged)
    }

    override suspend fun searchTracks(query: String): Result<List<Track>> {
        val trimmed = query.trim()
        val localTracks = scanLocalItemsSafe().map { it.track }.filter { track ->
            trimmed.isEmpty() ||
                track.title.contains(trimmed, ignoreCase = true) ||
                track.artistName.contains(trimmed, ignoreCase = true)
        }
        val curated = fallbackCatalog.searchTracks(trimmed).getOrDefault(emptyList())
        if (trimmed.length < 2) {
            return Result.success((localTracks + curated).distinctBy { it.id })
        }
        val online = innerTubeProvider.searchTracks(trimmed, limit = 20).getOrDefault(emptyList())
        return Result.success((localTracks + online + curated).distinctBy { it.id })
    }

    override suspend fun getLibraryTracks(): Result<List<Track>> {
        val localTracks = scanLocalItemsSafe().map { it.track }
        val curated = fallbackCatalog.getLibraryTracks().getOrDefault(emptyList())
        return Result.success((localTracks + curated).distinctBy { it.id })
    }

    private suspend fun scanLocalItemsSafe(): List<LocalTrackItem> =
        runCatching {
            localScanner.scanMediaStoreAudio().map { entry ->
                LocalTrackItem(
                    track = Track(
                        id = entry.id,
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
