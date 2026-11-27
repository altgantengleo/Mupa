package com.xeno.mupa.core.model

/**
 * Aturan tampilan data Mupa Calm (perbaikan B3, B4, B5):
 * - Durasi <= 0 disembunyikan (null), tidak pernah menampilkan "0:00"; mendukung >= 1 jam.
 * - "Hapus Nama Artis dari Judul" membersihkan prefiks "Artis - " dan clutter secara non-destruktif
 *   dari judul, sementara baris kedua (subtitle) tidak pernah kosong: artis -> album -> label sumber.
 */
object TrackDisplayRules {

    private val extensionRegex = Regex("""\.(?:mp3|flac|m4a|wav|ogg|opus|aac|webm)$""", RegexOption.IGNORE_CASE)
    private val separatorRegex = Regex("""\s+[-–—|]\s+""")
    private val bracketClutterRegex = Regex(
        """\s*[\(\[\{][^)\]}]*(?:official|audio|video|lyrics?|lirik|slowed|reverb|nightcore|remaster|hd|hq|4k|320kbps|flac|mv|visualizer|explicit)[^)\]}]*[\)\]\}]""",
        RegexOption.IGNORE_CASE,
    )
    private val trailingClutterRegex = Regex(
        """\s*[-–—|]\s*(?:official\s*(?:video|audio|mv|lyric\s*video)|lyrics?|slowed(?:\s*\+?\s*reverb)?|reverb|nightcore|remastered|hd|hq)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val unknownArtistTokens = setOf(
        "",
        "unknown",
        "<unknown>",
        "unknown artist",
        "artis tidak diketahui",
        "various",
    )

    data class TrackDisplayParts(
        val title: String,
        val subtitle: String,
        val durationText: String?,
        val sourceLabel: String,
    )

    /** Mengembalikan null bila durasi tidak diketahui (<= 0ms). Mendukung >= 1 jam. */
    fun formatDuration(durationMs: Long): String? {
        if (durationMs <= 0L) return null
        val totalSeconds = durationMs / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }

    /** Membersihkan judul untuk pencarian lirik (hapus Official Video, feat., dll.). */
    fun cleanTitleForLyrics(rawTitle: String): String {
        var cleaned = bracketClutterRegex.replace(rawTitle, "")
        cleaned = trailingClutterRegex.replace(cleaned, "")
        cleaned = Regex("""\s*[\(\[](?:feat\.?|ft\.?|featuring)\s+[^)\]]*[\)\]]""", RegexOption.IGNORE_CASE)
            .replace(cleaned, "")
        cleaned = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+.+$""", RegexOption.IGNORE_CASE)
            .replace(cleaned, "")
        return cleaned.replace(Regex("""\s+"""), " ").trim().ifEmpty { rawTitle.trim() }
    }

    fun formatTrack(track: Track, stripArtistName: Boolean): TrackDisplayParts {
        val sourceLabel = if (track.sourceType == AudioSourceType.LOCAL) "Lokal" else "Streaming"
        val rawFile = track.originalFileName?.takeIf { it.isNotBlank() }

        var displayTitle = track.title.trim()
        var resolvedArtist = track.artistName.trim()

        if (rawFile != null) {
            val withoutExt = extensionRegex.replace(rawFile, "").trim()
            val parts = separatorRegex.split(withoutExt).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val fileArtist = parts.first()
                val fileTitle = cleanClutter(parts.drop(1).joinToString(" - "))
                if (resolvedArtist.lowercase() in unknownArtistTokens) {
                    resolvedArtist = fileArtist
                }
                if (stripArtistName && fileTitle.isNotEmpty()) {
                    displayTitle = fileTitle
                } else if (!stripArtistName && displayTitle.isBlank()) {
                    displayTitle = cleanClutter(withoutExt)
                }
            } else if (displayTitle.isBlank()) {
                displayTitle = cleanClutter(withoutExt)
            }
        }

        if (stripArtistName && displayTitle.contains(" - ")) {
            val split = separatorRegex.split(displayTitle).map { it.trim() }.filter { it.isNotEmpty() }
            if (split.size >= 2) {
                val prefixMatchesArtist = resolvedArtist.isNotBlank() &&
                    split.first().equals(resolvedArtist, ignoreCase = true)
                if (prefixMatchesArtist || resolvedArtist.lowercase() in unknownArtistTokens) {
                    if (resolvedArtist.lowercase() in unknownArtistTokens) {
                        resolvedArtist = split.first()
                    }
                    displayTitle = cleanClutter(split.drop(1).joinToString(" - "))
                }
            }
        }

        if (stripArtistName) {
            displayTitle = cleanClutter(displayTitle)
        }
        if (displayTitle.isBlank()) {
            displayTitle = track.title.ifBlank { "Tanpa Judul" }
        }

        // Aturan baris kedua: artis -> album -> label sumber (TIDAK PERNAH KOSONG)
        val validArtist = resolvedArtist.takeIf { it.lowercase() !in unknownArtistTokens }
        val validAlbum = track.albumTitle?.trim()?.takeIf { it.isNotBlank() && it.lowercase() !in unknownArtistTokens }
        val subtitle = validArtist ?: validAlbum ?: sourceLabel

        return TrackDisplayParts(
            title = displayTitle,
            subtitle = subtitle,
            durationText = formatDuration(track.durationMs),
            sourceLabel = sourceLabel,
        )
    }

    private fun cleanClutter(input: String): String {
        var out = bracketClutterRegex.replace(input, "")
        out = trailingClutterRegex.replace(out, "")
        return out.replace(Regex("""\s+"""), " ").trim()
    }
}

/**
 * Filter kualitas rekomendasi Beranda (perbaikan N4):
 * - Memprioritaskan lagu tunggal berkualitas dan menolak kompilasi/upload sampah
 *   ("top hits", "playlist", "full album", "mix", "nonstop", "1 hour/jam", dll.).
 * - Menolak durasi > 8 menit atau < 60 detik bila durasi diketahui (> 0 ms).
 * - Deduplikasi berdasarkan ID maupun kunci (judul dinormalisasi + artis).
 */
object RecommendationQualityFilter {
    const val MIN_DURATION_MS: Long = 60_000L
    const val MAX_DURATION_MS: Long = 480_000L

    private val junkTitleRegex = Regex(
        """(?i)(?:top\s*hits|playlist|full\s*album|\bmix\b|megamix|non\s*-?\s*stop|\b\d+\s*(?:hour|hours|hr|jam)\b|compilation|kompilasi|kumpulan\s+lagu|terbaik\s+sepanjang\s+masa|lagu\s+hits\s+\d{4}|tik\s*tok|dj\s+remix|jedag\s+jedug|sped\s+up|slowed\s*\+?\s*reverb|mashup\s+nonstop)""",
    )
    private val junkArtistRegex = Regex(
        """(?i)(?:top\s*hits|spotify|playlist|compilation|kompilasi|piktor\s*tv|music\s*tv|non\s*-?\s*stop)""",
    )
    private val nonAlphaNumRegex = Regex("""[^\p{L}\p{N}]+""")

    fun isHighQualitySong(track: Track): Boolean {
        val title = track.title.trim()
        if (title.isEmpty()) return false
        if (junkTitleRegex.containsMatchIn(title)) return false
        val artist = track.artistName.trim()
        if (artist.isNotEmpty() && junkArtistRegex.containsMatchIn(artist)) return false
        if (track.durationMs > 0L && (track.durationMs < MIN_DURATION_MS || track.durationMs > MAX_DURATION_MS)) {
            return false
        }
        return true
    }

    fun normalizedDedupKey(track: Track): String {
        val cleanedTitle = TrackDisplayRules.cleanTitleForLyrics(track.title)
            .lowercase()
            .let { nonAlphaNumRegex.replace(it, " ").trim() }
        val cleanedArtist = track.artistName
            .lowercase()
            .let { nonAlphaNumRegex.replace(it, " ").trim() }
        return "$cleanedTitle|$cleanedArtist"
    }

    fun filterTracks(tracks: List<Track>): List<Track> {
        val seenIds = HashSet<String>(tracks.size)
        val seenKeys = HashSet<String>(tracks.size)
        val out = ArrayList<Track>(tracks.size)
        for (track in tracks) {
            if (!isHighQualitySong(track)) continue
            val key = normalizedDedupKey(track)
            if (seenIds.add(track.id) && seenKeys.add(key)) {
                out.add(track)
            }
        }
        return out
    }
}

/**
 * Menyusun rak Beranda agar setiap lagu hanya muncul sekali per layar (distinctBy id & judul dinormalisasi)
 * dan setiap shelf dibatasi maksimal 10 item (perbaikan B3 & N4).
 */
object ShelfComposer {
    const val DEFAULT_MAX_ITEMS_PER_SHELF = 10

    fun compose(
        shelves: List<Playlist>,
        excludedTrackIds: Set<String> = emptySet(),
        maxPerShelf: Int = DEFAULT_MAX_ITEMS_PER_SHELF,
        maxShelves: Int = 3,
    ): List<Playlist> {
        val seenIds = excludedTrackIds.toMutableSet()
        val seenKeys = mutableSetOf<String>()
        val result = mutableListOf<Playlist>()
        for (shelf in shelves) {
            if (result.size >= maxShelves) break
            val unique = mutableListOf<Track>()
            for (track in shelf.tracks) {
                if (unique.size >= maxPerShelf) break
                val key = RecommendationQualityFilter.normalizedDedupKey(track)
                if (seenIds.add(track.id) && seenKeys.add(key)) {
                    unique.add(track)
                }
            }
            if (unique.isNotEmpty()) {
                result.add(shelf.copy(tracks = unique))
            }
        }
        return result
    }
}
