package com.xeno.mupa.data.streaming

import com.xeno.mupa.core.domain.ArtistRepository
import com.xeno.mupa.core.domain.MusicSource
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.data.fake.FakeArtistRepository
import com.xeno.mupa.data.fake.FakeMusicSource
import com.xeno.mupa.streaming.innertube.InnerTubeProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi ArtistRepository berbasis penyedia streaming (InnerTube)
 * dengan fallback offline ke katalog kurasi agar Onboarding selalu berjalan lancar.
 */
@Singleton
class StreamingArtistRepository @Inject constructor(
    private val innerTubeProvider: InnerTubeProvider,
    private val fallbackCurated: FakeArtistRepository,
) : ArtistRepository {

    override suspend fun getSuggestedArtists(query: String): Result<List<Artist>> {
        val trimmed = query.trim()
        val curated = fallbackCurated.getSuggestedArtists(trimmed).getOrDefault(emptyList())
        if (trimmed.length < 2) {
            return Result.success(curated)
        }
        val online = innerTubeProvider.searchArtists(trimmed, limit = 12).getOrDefault(emptyList())
        val merged = (online + curated).distinctBy { it.name.lowercase() }
        return Result.success(merged)
    }

    override suspend fun getArtistsByIds(ids: Set<String>): Result<List<Artist>> =
        fallbackCurated.getArtistsByIds(ids)
}

/**
 * Implementasi MusicSource berbasis penyedia streaming (InnerTube) dengan fallback lokal/kurasi
 * bila perangkat offline atau penyedia sedang tidak terjangkau.
 */
@Singleton
class StreamingMusicSource @Inject constructor(
    private val innerTubeProvider: InnerTubeProvider,
    private val fallbackCatalog: FakeMusicSource,
) : MusicSource {

    override val sourceId: String = "streaming_unified"

    override suspend fun getHomeRecommendations(seedArtistIds: Set<String>): Result<List<Playlist>> =
        fallbackCatalog.getHomeRecommendations(seedArtistIds)

    override suspend fun searchTracks(query: String): Result<List<Track>> {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            return fallbackCatalog.searchTracks(trimmed)
        }
        val online = innerTubeProvider.searchTracks(trimmed, limit = 20).getOrDefault(emptyList())
        val localFallback = fallbackCatalog.searchTracks(trimmed).getOrDefault(emptyList())
        return Result.success((online + localFallback).distinctBy { it.id })
    }

    override suspend fun getLibraryTracks(): Result<List<Track>> =
        fallbackCatalog.getLibraryTracks()
}
