package com.xeno.mupa.data.fake

import com.xeno.mupa.core.domain.ArtistRepository
import com.xeno.mupa.core.domain.MusicSource
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeArtistRepository @Inject constructor() : ArtistRepository {

    private val catalogArtists = listOf(
        Artist("art-hindia", "Hindia", genre = "Indie Pop"),
        Artist("art-nadin", "Nadin Amizah", genre = "Folk Pop"),
        Artist("art-tulus", "Tulus", genre = "Pop"),
        Artist("art-reality", "Reality Club", genre = "Indie Rock"),
        Artist("art-feast", ".Feast", genre = "Rock"),
        Artist("art-sal", "Sal Priadi", genre = "Pop Alternatif"),
        Artist("art-bernadya", "Bernadya", genre = "Pop"),
        Artist("art-pamungkas", "Pamungkas", genre = "Indie"),
        Artist("art-niki", "NIKI", genre = "R&B"),
        Artist("art-joji", "Joji", genre = "Lo-Fi R&B"),
        Artist("art-yoasobi", "YOASOBI", genre = "J-Pop"),
        Artist("art-fujii", "Fujii Kaze", genre = "J-Pop / Soul"),
        Artist("art-weeknd", "The Weeknd", genre = "Synth-Pop"),
        Artist("art-billie", "Billie Eilish", genre = "Alt-Pop"),
        Artist("art-sheila", "Sheila On 7", genre = "Pop Rock", sourceType = AudioSourceType.LOCAL),
        Artist("art-payung", "Payung Teduh", genre = "Folk Jazz", sourceType = AudioSourceType.LOCAL),
    )

    override suspend fun getSuggestedArtists(query: String): Result<List<Artist>> {
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            catalogArtists
        } else {
            catalogArtists.filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                    (it.genre?.contains(trimmed, ignoreCase = true) == true)
            }
        }
        return Result.success(filtered)
    }

    override suspend fun getArtistsByIds(ids: Set<String>): Result<List<Artist>> {
        if (ids.isEmpty()) return Result.success(emptyList())
        return Result.success(catalogArtists.filter { it.id in ids })
    }
}

@Singleton
class FakeMusicSource @Inject constructor(
    private val artistRepository: ArtistRepository,
) : MusicSource {

    override val sourceId: String = "fake_catalog"

    private val allTracks = listOf(
        Track(
            id = "trk-1",
            title = "Evaluasi",
            artistName = "Hindia",
            albumTitle = "Menari Dengan Bayangan",
            durationMs = 214_000L,
            accentColorArgb = 0xFFFF5A3C,
            sourceType = AudioSourceType.STREAMING,
            isFavorite = true,
            playCount = 19,
        ),
        Track(
            id = "trk-2",
            title = "Bertaut",
            artistName = "Nadin Amizah",
            albumTitle = "Selamat Ulang Tahun",
            durationMs = 256_000L,
            accentColorArgb = 0xFF4ECDC4,
            sourceType = AudioSourceType.LOCAL,
            originalFileName = "Nadin Amizah - Bertaut (Official Audio).flac",
            isFavorite = true,
            playCount = 15,
        ),
        Track(
            id = "trk-3",
            title = "Hati-Hati di Jalan",
            artistName = "Tulus",
            albumTitle = "Manusia",
            durationMs = 242_000L,
            accentColorArgb = 0xFFE07A5F,
            sourceType = AudioSourceType.STREAMING,
            isFavorite = true,
            playCount = 24,
        ),
        Track(
            id = "trk-4",
            title = "Anything You Want",
            artistName = "Reality Club",
            albumTitle = "Reality Club Presents...",
            durationMs = 228_000L,
            accentColorArgb = 0xFF7AA2F7,
            sourceType = AudioSourceType.LOCAL,
            originalFileName = "Reality Club - Anything You Want (HD).mp3",
            isFavorite = false,
            playCount = 9,
        ),
        Track(
            id = "trk-5",
            title = "Nina",
            artistName = ".Feast",
            albumTitle = "Membangun & Menghancurkan",
            durationMs = 278_000L,
            accentColorArgb = 0xFFFF8A4C,
            sourceType = AudioSourceType.STREAMING,
            isFavorite = true,
            playCount = 12,
        ),
        Track(
            id = "trk-6",
            title = "Gala Bunga Matahari",
            artistName = "Sal Priadi",
            albumTitle = "Markers and Such Pens Flashdisks",
            durationMs = 231_000L,
            accentColorArgb = 0xFFFFB067,
            sourceType = AudioSourceType.STREAMING,
            isFavorite = false,
            playCount = 0,
        ),
        Track(
            id = "trk-7",
            title = "Satu Bulan",
            artistName = "Bernadya",
            albumTitle = "Sialnya, Hidup Harus Tetap Berjalan",
            durationMs = 204_000L,
            accentColorArgb = 0xFFE87A90,
            sourceType = AudioSourceType.STREAMING,
            isFavorite = false,
            playCount = 0,
        ),
        Track(
            id = "trk-8",
            title = "To the Bone",
            artistName = "Pamungkas",
            albumTitle = "Flying Solo",
            durationMs = 344_000L,
            accentColorArgb = 0xFFC77DFF,
            sourceType = AudioSourceType.LOCAL,
            originalFileName = "Pamungkas - To the Bone (Lyrics).mp3",
            isFavorite = true,
            playCount = 17,
        ),
        Track(
            id = "trk-9",
            title = "Lowkey",
            artistName = "NIKI",
            albumTitle = "wanna take this downtown?",
            durationMs = 171_000L,
            accentColorArgb = 0xFF639BFF,
            sourceType = AudioSourceType.STREAMING,
            isFavorite = false,
            playCount = 6,
        ),
        Track(
            id = "trk-10",
            title = "Glimpse of Us",
            artistName = "Joji",
            albumTitle = "SMITHEREENS",
            durationMs = 233_000L,
            accentColorArgb = 0xFF56CFE1,
            sourceType = AudioSourceType.STREAMING,
            isFavorite = true,
            playCount = 11,
        ),
        Track(
            id = "trk-11",
            title = "Yoru ni Kakeru",
            artistName = "YOASOBI",
            albumTitle = "THE BOOK",
            durationMs = 261_000L,
            accentColorArgb = 0xFFFF6B6B,
            sourceType = AudioSourceType.STREAMING,
            isFavorite = false,
            playCount = 0,
        ),
        Track(
            id = "trk-12",
            title = "Shinunoga E-Wa",
            artistName = "Fujii Kaze",
            albumTitle = "HELP EVER HURT NEVER",
            durationMs = 185_000L,
            accentColorArgb = 0xFFF2C078,
            sourceType = AudioSourceType.STREAMING,
            isFavorite = true,
            playCount = 8,
        ),
        Track(
            id = "trk-13",
            title = "Dan",
            artistName = "Sheila On 7",
            albumTitle = "Sheila On 7",
            durationMs = 286_000L,
            accentColorArgb = 0xFF8AC926,
            sourceType = AudioSourceType.LOCAL,
            originalFileName = "Sheila On 7 - Dan (Remastered).flac",
            isFavorite = true,
            playCount = 28,
        ),
        Track(
            id = "trk-14",
            title = "Akad",
            artistName = "Payung Teduh",
            albumTitle = "Ruang Tunggu",
            durationMs = 259_000L,
            accentColorArgb = 0xFF4CC9F0,
            sourceType = AudioSourceType.LOCAL,
            originalFileName = "Payung Teduh - Akad.mp3",
            isFavorite = false,
            playCount = 0,
        ),
    )

    override suspend fun getHomeRecommendations(
        seedArtistIds: Set<String>,
        source: AudioSourceType,
    ): Result<List<Playlist>> {
        val selectedArtists = artistRepository.getArtistsByIds(seedArtistIds).getOrDefault(emptyList())
        val selectedNames = selectedArtists.map { it.name.lowercase() }.toSet()

        val forYouTracks = if (selectedNames.isNotEmpty()) {
            val matched = allTracks.filter { it.artistName.lowercase() in selectedNames }
            val remainder = allTracks.filterNot { it.artistName.lowercase() in selectedNames }.take(4)
            (matched + remainder).distinctBy { it.id }
        } else {
            allTracks.take(6)
        }

        val mixMupaTracks = allTracks
            .sortedByDescending { (if (it.isFavorite) 10 else 0) + it.playCount }
            .take(8)

        val repeatAgainTracks = allTracks
            .filter { it.playCount >= 8 || it.isFavorite }
            .sortedByDescending { it.playCount }

        val unplayedTracks = allTracks.filter { it.playCount == 0 }

        return Result.success(
            listOf(
                Playlist(
                    id = "shelf_for_you",
                    title = "Untukmu",
                    subtitle = if (selectedArtists.isNotEmpty()) {
                        "Berdasarkan ${selectedArtists.take(3).joinToString(", ") { it.name }}"
                    } else {
                        "Pilihan awal untukmu"
                    },
                    tracks = forYouTracks,
                    isSystemGenerated = true,
                ),
                Playlist(
                    id = "shelf_mix_mupa",
                    title = "Mix Mupa",
                    subtitle = "Racikan harian dari selera dan favoritmu",
                    tracks = mixMupaTracks,
                    isSystemGenerated = true,
                ),
                Playlist(
                    id = "shelf_repeat",
                    title = "Ulangi Lagi",
                    subtitle = "Lagu yang sering kamu putar",
                    tracks = repeatAgainTracks,
                    isSystemGenerated = true,
                ),
                Playlist(
                    id = "shelf_unplayed",
                    title = "Belum Diputar",
                    subtitle = "Baru ditambahkan dan belum sempat diputar",
                    tracks = unplayedTracks,
                    isSystemGenerated = true,
                ),
            ),
        )
    }

    override suspend fun searchTracks(query: String, source: AudioSourceType): Result<List<Track>> {
        val trimmed = query.trim()
        val scoped = allTracks.filter { it.sourceType == source }
        if (trimmed.isEmpty()) return Result.success(scoped)
        return Result.success(
            scoped.filter { track ->
                track.title.contains(trimmed, ignoreCase = true) ||
                    track.artistName.contains(trimmed, ignoreCase = true) ||
                    (track.albumTitle?.contains(trimmed, ignoreCase = true) == true)
            },
        )
    }

    override suspend fun getLibraryTracks(source: AudioSourceType): Result<List<Track>> =
        Result.success(allTracks.filter { it.sourceType == source })
}
