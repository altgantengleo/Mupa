package com.xeno.mupa.data.fake

import com.xeno.mupa.core.model.Track

/**
 * Helper tampilan non-destruktif untuk pengaturan "Hapus Nama Artis dari Judul" (sesuai AUDIT.md §7.9).
 * Selalu menjaga originalFileName tetap utuh; versi Rust penuh akan menangani parsing file lokal di Tahap 3.
 */
object TrackNameFormatter {
    private val extensionRegex = Regex("""\.(?:mp3|flac|m4a|wav|ogg|opus|aac|webm)$""", RegexOption.IGNORE_CASE)
    private val splitRegex = Regex("""\s+[-–—|]\s+""")
    private val bracketClutterRegex = Regex(
        """\s*[\(\[\{][^)\]}]*(?:official|audio|video|lyrics?|lirik|slowed|reverb|nightcore|remaster|hd|hq|4k|320kbps|flac)[^)\]}]*[\)\]\}]""",
        RegexOption.IGNORE_CASE,
    )

    data class DisplayParts(
        val title: String,
        val artist: String,
    )

    fun format(track: Track, stripArtistName: Boolean): DisplayParts {
        val rawFile = track.originalFileName
        if (rawFile.isNullOrBlank()) {
            return DisplayParts(
                title = track.title,
                artist = if (stripArtistName) "" else track.artistName,
            )
        }

        val withoutExt = extensionRegex.replace(rawFile, "").trim()
        val parts = splitRegex.split(withoutExt).map { it.trim() }.filter { it.isNotEmpty() }

        return if (parts.size >= 2) {
            val recoveredArtist = parts.first()
            val cleanTitle = bracketClutterRegex.replace(parts.drop(1).joinToString(" - "), "").trim()
                .ifEmpty { track.title }
            if (stripArtistName) {
                DisplayParts(title = cleanTitle, artist = "")
            } else {
                DisplayParts(title = cleanTitle, artist = recoveredArtist)
            }
        } else {
            val cleanTitle = bracketClutterRegex.replace(withoutExt, "").trim().ifEmpty { track.title }
            DisplayParts(
                title = cleanTitle,
                artist = if (stripArtistName) "" else track.artistName,
            )
        }
    }
}
