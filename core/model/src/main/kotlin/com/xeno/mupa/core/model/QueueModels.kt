package com.xeno.mupa.core.model

/**
 * Sumber antrean putar Mupa (Fase 2T — Antrean Cerdas).
 * Membedakan asal antrean agar perilaku lanjutan (radio/continuation), shuffle, dan label UI akurat.
 */
sealed interface QueueSource {
    val displayLabel: String
    val isInfinite: Boolean

    data class Radio(
        val seedTrackId: String,
        val seedTitle: String,
        val seedArtist: String,
    ) : QueueSource {
        override val displayLabel: String = "Radio dari '$seedTitle'"
        override val isInfinite: Boolean = true
    }

    data class Artist(
        val artistId: String,
        val artistName: String,
    ) : QueueSource {
        override val displayLabel: String = "Radio $artistName"
        override val isInfinite: Boolean = true
    }

    data class PlaylistOrAlbum(
        val collectionId: String,
        val title: String,
        val isAlbum: Boolean = false,
    ) : QueueSource {
        override val displayLabel: String = title
        override val isInfinite: Boolean = false
    }

    data class Local(
        val label: String = "Pustaka Lokal",
    ) : QueueSource {
        override val displayLabel: String = label
        override val isInfinite: Boolean = false
    }

    data class MixMupa(
        val label: String = "Mix Mupa",
    ) : QueueSource {
        override val displayLabel: String = label
        override val isInfinite: Boolean = true
    }
}

/**
 * Filter versi & penyusun antrean radio cerdas (Fase 2T — A):
 * - Normalisasi judul (huruf kecil, buang tanda baca & sufiks kurung/strip).
 * - Menolak versi lain (live, acoustic, cover, remix, lyric(s), instrumental, karaoke, slowed,
 *   sped up, reverb, 8d, nightcore, version/ver., official video/audio/mv, full album, mix, nonstop, 1 hour/jam)
 *   KECUALI lagu yang secara eksplisit diketuk pengguna.
 * - Durasi 60 dtk – 8 mnt (bila durasi diketahui > 0).
 * - Setiap (judul ternormalisasi + artis) hanya muncul sekali dalam antrean.
 * - Maksimal 2 lagu berurutan dari artis yang sama (lunak: geser, bukan dibuang).
 * - Hindari mengulang 20 lagu terakhir bila kandidat lain mencukupi.
 */
object SmartQueueFilter {
    const val MIN_DURATION_MS: Long = 60_000L
    const val MAX_DURATION_MS: Long = 480_000L
    const val MAX_CONSECUTIVE_SAME_ARTIST: Int = 2
    const val RECENT_HISTORY_LIMIT: Int = 20

    private val bracketSuffixRegex = Regex("""[\(\[\{][^)\]}]*[\)\]\}]""")
    private val dashSuffixRegex = Regex("""\s+[-–—|]\s+.*$""")
    private val nonAlphaNumRegex = Regex("""[^\p{L}\p{N}]+""")

    private val alternateVersionRegex = Regex(
        """(?i)(?:\blive\b|\bacoustic\b|\bcover\b|\bremix\b|\blyrics?\b|\blirik\b|\binstrumental\b|\bkaraoke\b|\bslowed\b|sped\s*up|\breverb\b|\b8d\b|\bnightcore\b|\bver(?:sion)?\.?\b|official\s+(?:video|audio|mv|lyric)|full\s+album|\bmix\b|\bmegamix\b|non\s*-?\s*stop|\b\d+\s*(?:hour|hours|hr|jam)\b)""",
    )

    fun normalizeTitle(rawTitle: String): String {
        var out = bracketSuffixRegex.replace(rawTitle, " ")
        out = dashSuffixRegex.replace(out, " ")
        out = nonAlphaNumRegex.replace(out.lowercase(), " ").trim()
        return out.ifEmpty {
            nonAlphaNumRegex.replace(rawTitle.lowercase(), " ").trim()
        }
    }

    fun normalizeArtist(rawArtist: String): String =
        nonAlphaNumRegex.replace(rawArtist.lowercase(), " ").trim()

    fun normalizedTrackKey(track: Track): String =
        "${normalizeTitle(track.title)}|${normalizeArtist(track.artistName)}"

    fun isAlternateVersionTitle(title: String): Boolean =
        alternateVersionRegex.containsMatchIn(title)

    fun isValidDuration(durationMs: Long): Boolean =
        durationMs <= 0L || durationMs in MIN_DURATION_MS..MAX_DURATION_MS

    fun filterAndOrderRadioCandidates(
        seed: Track?,
        candidates: List<Track>,
        existingQueue: List<Track> = emptyList(),
        recentTrackIds: Set<String> = emptySet(),
    ): List<Track> {
        val seenIds = HashSet<String>()
        val seenKeys = HashSet<String>()

        seed?.let {
            seenIds.add(it.id)
            seenKeys.add(normalizedTrackKey(it))
        }
        existingQueue.forEach {
            seenIds.add(it.id)
            seenKeys.add(normalizedTrackKey(it))
        }

        val freshCandidates = mutableListOf<Track>()
        val recentCandidates = mutableListOf<Track>()

        for (candidate in candidates) {
            if (candidate.title.isBlank()) continue
            if (!isValidDuration(candidate.durationMs)) continue
            if (isAlternateVersionTitle(candidate.title)) continue

            val key = normalizedTrackKey(candidate)
            if (!seenIds.add(candidate.id) || !seenKeys.add(key)) continue

            if (candidate.id in recentTrackIds) {
                recentCandidates.add(candidate)
            } else {
                freshCandidates.add(candidate)
            }
        }

        // Hindari mengulang 20 lagu terakhir bila kandidat segar sudah cukup (>= 3)
        val combined = if (freshCandidates.size >= 3) {
            freshCandidates
        } else {
            freshCandidates + recentCandidates
        }

        val trailingArtists = existingQueue.takeLast(MAX_CONSECUTIVE_SAME_ARTIST)
            .map { normalizeArtist(it.artistName) }
        return softenArtistStreaks(combined, trailingArtists, MAX_CONSECUTIVE_SAME_ARTIST)
    }

    /**
     * Menggeser (tanpa membuang) item agar tidak ada lebih dari [maxConsecutive] lagu berurutan
     * dari artis yang sama apabila masih ada kandidat artis lain.
     */
    fun softenArtistStreaks(
        tracks: List<Track>,
        initialTrailingArtists: List<String> = emptyList(),
        maxConsecutive: Int = MAX_CONSECUTIVE_SAME_ARTIST,
    ): List<Track> {
        if (tracks.size <= 1) return tracks
        val remaining = ArrayDeque(tracks)
        val out = ArrayList<Track>(tracks.size)
        val recentArtists = ArrayDeque<String>()
        initialTrailingArtists.takeLast(maxConsecutive).forEach { recentArtists.addLast(it) }

        while (remaining.isNotEmpty()) {
            val blockedArtist = if (
                recentArtists.size >= maxConsecutive &&
                recentArtists.all { it == recentArtists.first() && it.isNotEmpty() }
            ) {
                recentArtists.first()
            } else {
                null
            }

            val pickIndex = if (blockedArtist == null) {
                0
            } else {
                remaining.indexOfFirst { normalizeArtist(it.artistName) != blockedArtist }
                    .takeIf { it >= 0 } ?: 0
            }

            val picked = if (pickIndex == 0) {
                remaining.removeFirst()
            } else {
                val item = remaining.elementAt(pickIndex)
                remaining.removeAt(pickIndex)
                item
            }
            out.add(picked)
            recentArtists.addLast(normalizeArtist(picked.artistName))
            while (recentArtists.size > maxConsecutive) {
                recentArtists.removeFirst()
            }
        }
        return out
    }
}
