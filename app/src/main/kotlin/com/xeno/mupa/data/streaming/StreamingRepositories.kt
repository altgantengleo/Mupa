package com.xeno.mupa.data.streaming

import android.content.Context
import com.xeno.mupa.core.domain.ArtistRepository
import com.xeno.mupa.core.domain.MusicSource
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.ShelfComposer
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.data.fake.FakeArtistRepository
import com.xeno.mupa.data.local.LocalLibraryRepository
import com.xeno.mupa.engine.local.LocalTrackItem
import com.xeno.mupa.engine.local.MixMupaLocalRecommender
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
 * Repository artis yang menggabungkan artis dari pustaka lokal TER-IMPORT (Room/SAF, tanpa auto-scan liar)
 * dengan artis YouTube Music (berfoto HD), serta menyimpan cache persisten ID->Artis.
 */
@Singleton
class StreamingArtistRepository @Inject constructor(
    @ApplicationContext appContext: Context,
    private val innerTubeProvider: InnerTubeProvider,
    private val localLibraryRepository: LocalLibraryRepository,
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
        val localItems = localLibraryRepository.getAllLocalTracks().map { track ->
            LocalTrackItem(track = track, contentUri = track.id, importedAtMs = track.dateAddedEpochMs)
        }
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
        val localItems = localLibraryRepository.getAllLocalTracks().map { track ->
            LocalTrackItem(track = track, contentUri = track.id, importedAtMs = track.dateAddedEpochMs)
        }
        val localArtists = MixMupaLocalRecommender.aggregateLocalArtists(localItems).filter { it.id in ids }
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
}

/**
 * Pemisahan ketat Mode Streaming (Awan) vs Mode Lokal (Ponsel) — perbaikan B3 & B12:
 * - Mode Lokal membaca HANYA dari LocalLibraryRepository (Room/SAF hasil import manual).
 * - Mode Streaming membaca HANYA dari InnerTubeProvider + koleksi streaming (tanpa auto-scan MediaStore & tanpa dummy palsu).
 * - Seluruh rak Beranda melewati ShelfComposer agar tiap lagu hanya muncul 1x per layar (maks 10/shelf).
 */
@Singleton
class StreamingMusicSource @Inject constructor(
    private val innerTubeProvider: InnerTubeProvider,
    private val artistRepository: StreamingArtistRepository,
    private val localLibraryRepository: LocalLibraryRepository,
) : MusicSource {

    override val sourceId: String = "mupa_strict_source"

    private val cachedStreamingCollection = ConcurrentHashMap<String, Track>()

    override suspend fun getHomeRecommendations(
        seedArtistIds: Set<String>,
        source: AudioSourceType,
    ): Result<List<Playlist>> = when (source) {
        AudioSourceType.LOCAL -> buildLocalHomeShelves()
        AudioSourceType.STREAMING -> buildStreamingHomeShelves(seedArtistIds)
    }

    override suspend fun searchTracks(
        query: String,
        source: AudioSourceType,
    ): Result<List<Track>> {
        val trimmed = query.trim()
        return when (source) {
            AudioSourceType.LOCAL -> {
                Result.success(localLibraryRepository.searchLocalTracks(trimmed))
            }
            AudioSourceType.STREAMING -> {
                val effectiveQuery = if (trimmed.length < 2) "Lagu Pop Indonesia" else trimmed
                innerTubeProvider.searchTracks(effectiveQuery, limit = 20)
                    .onSuccess { list ->
                        list.forEach { cachedStreamingCollection[it.id] = it }
                    }
            }
        }
    }

    override suspend fun getLibraryTracks(source: AudioSourceType): Result<List<Track>> =
        when (source) {
            AudioSourceType.LOCAL -> Result.success(localLibraryRepository.getAllLocalTracks())
            AudioSourceType.STREAMING -> {
                val cached = cachedStreamingCollection.values.toList()
                if (cached.isNotEmpty()) {
                    Result.success(cached)
                } else {
                    innerTubeProvider.searchTracks("Lagu Pop Indonesia Terbaru", limit = 20)
                        .onSuccess { list -> list.forEach { cachedStreamingCollection[it.id] = it } }
                }
            }
        }

    private suspend fun buildLocalHomeShelves(): Result<List<Playlist>> {
        val tracks = localLibraryRepository.getAllLocalTracks()
        if (tracks.isEmpty()) {
            return Result.success(emptyList())
        }
        val items = tracks.map { t ->
            LocalTrackItem(track = t, contentUri = t.id, importedAtMs = t.dateAddedEpochMs)
        }
        val mixTracks = MixMupaLocalRecommender.buildMixMupa(items, limit = 10).map { it.track }
        val repeatTracks = MixMupaLocalRecommender.buildRepeatAgain(items, limit = 10)
        val unplayedTracks = MixMupaLocalRecommender.buildUnplayed(items, limit = 10)

        val hero = Playlist(
            id = "shelf_mix_mupa",
            title = "Mix Mupa",
            subtitle = "Racikan dari pustaka lokalmu",
            tracks = mixTracks.ifEmpty { tracks.take(10) },
            isSystemGenerated = true,
        )
        val rawShelves = listOf(
            Playlist(
                id = "shelf_for_you",
                title = "Untukmu",
                subtitle = "Dari koleksi musik di perangkatmu",
                tracks = tracks.sortedByDescending { it.isFavorite },
                isSystemGenerated = true,
            ),
            Playlist(
                id = "shelf_repeat",
                title = "Ulangi lagi",
                subtitle = "Sering kamu dengarkan",
                tracks = repeatTracks.ifEmpty { tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount } },
                isSystemGenerated = true,
            ),
            Playlist(
                id = "shelf_unplayed",
                title = "Belum diputar",
                subtitle = "Baru di-import ke pustaka lokal",
                tracks = unplayedTracks.ifEmpty { tracks.filter { it.playCount == 0 } },
                isSystemGenerated = true,
            ),
        )
        // Dedup lintas shelf (hero Mix Mupa terpisah sebagai ringkasan, sedangkan 3 shelf tidak pernah duplikat satu sama lain)
        val dedupedShelves = ShelfComposer.compose(
            shelves = rawShelves,
            excludedTrackIds = emptySet(),
            maxPerShelf = ShelfComposer.DEFAULT_MAX_ITEMS_PER_SHELF,
            maxShelves = 3,
        )
        return Result.success(listOf(hero) + dedupedShelves)
    }

    private suspend fun buildStreamingHomeShelves(seedArtistIds: Set<String>): Result<List<Playlist>> =
        coroutineScope {
            val selectedArtists = artistRepository.getArtistsByIds(seedArtistIds).getOrDefault(emptyList())
            val seedNames = selectedArtists.map { it.name }.take(4).ifEmpty {
                listOf("Tulus", "Hindia", "Bernadya")
            }

            // Ambil beberapa query berbeda agar pool cukup besar untuk 3 shelf tanpa duplikasi (B3)
            val perArtist = seedNames.map { artistName ->
                async {
                    innerTubeProvider.searchTracks(artistName, limit = 8).getOrDefault(emptyList())
                }
            }
            val discoveryJob = async {
                innerTubeProvider.searchTracks("Indie Pop Santai", limit = 12).getOrDefault(emptyList())
            }
            val trendingJob = async {
                innerTubeProvider.searchTracks("Top Hits Indonesia", limit = 12).getOrDefault(emptyList())
            }

            val seedPool = perArtist.awaitAll().flatten().distinctBy { it.id }
            val discoveryPool = discoveryJob.await().distinctBy { it.id }
            val trendingPool = trendingJob.await().distinctBy { it.id }
            val combined = (seedPool + discoveryPool + trendingPool).distinctBy { it.id }

            if (combined.isEmpty()) {
                return@coroutineScope Result.failure(
                    IllegalStateException("Tidak dapat memuat katalog streaming. Periksa koneksi internetmu."),
                )
            }
            combined.forEach { cachedStreamingCollection[it.id] = it }

            val heroTracks = combined.take(10)
            val hero = Playlist(
                id = "shelf_mix_mupa",
                title = "Mix Mupa",
                subtitle = if (selectedArtists.isNotEmpty()) {
                    "Racikan dari ${selectedArtists.take(2).joinToString(" & ") { it.name }}"
                } else {
                    "Racikan harian untukmu"
                },
                tracks = heroTracks,
                isSystemGenerated = true,
            )

            val rawShelves = listOf(
                Playlist(
                    id = "shelf_for_you",
                    title = "Untukmu",
                    subtitle = if (selectedArtists.isNotEmpty()) {
                        "Berdasarkan ${selectedArtists.take(3).joinToString(", ") { it.name }}"
                    } else {
                        "Pilihan artis populer"
                    },
                    tracks = seedPool,
                    isSystemGenerated = true,
                ),
                Playlist(
                    id = "shelf_repeat",
                    title = "Ulangi lagi",
                    subtitle = "Pilihan hangat yang nyaman diputar ulang",
                    tracks = trendingPool,
                    isSystemGenerated = true,
                ),
                Playlist(
                    id = "shelf_unplayed",
                    title = "Belum diputar",
                    subtitle = "Temuan baru untuk kamu jelajahi",
                    tracks = discoveryPool,
                    isSystemGenerated = true,
                ),
            )

            // ShelfComposer memastikan tidak ada satu pun track muncul dua kali antar shelf (maks 10/shelf)
            val dedupedShelves = ShelfComposer.compose(
                shelves = rawShelves,
                excludedTrackIds = emptySet(),
                maxPerShelf = ShelfComposer.DEFAULT_MAX_ITEMS_PER_SHELF,
                maxShelves = 3,
            )
            Result.success(listOf(hero) + dedupedShelves)
        }
}
