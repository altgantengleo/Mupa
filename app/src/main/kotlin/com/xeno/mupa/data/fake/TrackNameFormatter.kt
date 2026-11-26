package com.xeno.mupa.data.fake

import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.TrackDisplayRules

/**
 * Wrapper kompatibilitas menuju TrackDisplayRules (core:model) agar aturan
 * "Hapus Nama Artis dari Judul" non-destruktif & baris kedua tidak pernah kosong
 * konsisten di seluruh layar.
 */
object TrackNameFormatter {
    data class DisplayParts(
        val title: String,
        val artist: String,
        val durationText: String? = null,
    )

    fun format(track: Track, stripArtistName: Boolean): DisplayParts {
        val parts = TrackDisplayRules.formatTrack(track, stripArtistName)
        return DisplayParts(
            title = parts.title,
            artist = parts.subtitle,
            durationText = parts.durationText,
        )
    }
}
