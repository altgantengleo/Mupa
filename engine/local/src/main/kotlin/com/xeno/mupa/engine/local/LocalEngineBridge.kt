package com.xeno.mupa.engine.local

import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Track
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

data class LocalTrackItem(
    val track: Track,
    val contentUri: String,
    val importedAtMs: Long,
    val lastPlayedAtMs: Long = 0L,
    val embeddedLyrics: String? = null,
)

data class LocalRecommendationItem(
    val track: Track,
    val reason: String,
    val score: Double,
)

/**
 * Kontrak bridge ke engine lokal Rust (UniFFI libmupa_local.so).
 * Menyediakan deteksi runtime library native + implementasi JVM sinkron agar
 * unit test JVM tetap memverifikasi formula Mix Mupa dan Hapus Nama Artis secara deterministik.
 */
object NativeRustLibraryLoader {
    @Volatile
    private var loaded: Boolean? = null

    fun isNativeLibraryAvailable(): Boolean {
        loaded?.let { return it }
        val ok = runCatching {
            System.loadLibrary("mupa_local")
            true
        }.getOrDefault(false)
        loaded = ok
        return ok
    }
}

/**
 * Cermin algoritma Mix Mupa / Ulangi Lagi / Belum Diputar (identik dengan engine/local-rust/mupa-local/src/recommender.rs)
 * untuk menjamin paritas uji JVM & fallback saat library native belum dimuat.
 */
object MixMupaLocalRecommender {
    private const val DAY_MS = 86_400_000.0
    private const val HALF_LIFE_DAYS = 21.0
    private val EXPLORE_SLOTS = setOf(2, 6, 11, 17)

    fun buildMixMupa(
        items: List<LocalTrackItem>,
        currentTrackId: String? = null,
        seed: Int = 0,
        nowMs: Long = System.currentTimeMillis(),
        limit: Int = 12,
    ): List<LocalRecommendationItem> {
        if (items.isEmpty() || limit <= 0) return emptyList()

        val artistAff = mutableMapOf<String, Double>()
        var maxPlay = 1
        for (item in items) {
            maxPlay = maxOf(maxPlay, item.track.playCount)
            val key = item.track.artistName.lowercase()
            artistAff[key] = (artistAff[key] ?: 0.0) + item.track.playCount + if (item.track.isFavorite) 2.0 else 0.0
        }
        val maxAff = artistAff.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val seedTag = "${nowMs / 86_400_000L}|$seed"

        val scored = items.map { item ->
            val t = item.track
            val aKey = t.artistName.lowercase()
            var total = 0.0
            var bestLabel = "Pilihan Mupa"
            var bestWeight = 0.07

            fun offer(value: Double, label: String, weight: Double = 1.0) {
                val w = value * weight
                if (w > bestWeight) {
                    bestWeight = w
                    bestLabel = label
                }
            }

            if (t.isFavorite) {
                total += 0.30
                offer(0.30, "Favorit")
            }

            val af = (artistAff[aKey] ?: 0.0) / maxAff
            total += 0.30 * af
            if (af > 0.5) offer(0.30 * af, "Artis favoritmu")

            total += 0.12 * (ln(1.0 + t.playCount.coerceAtLeast(0)) / ln(1.0 + maxPlay))

            if (item.lastPlayedAtMs > 0L) {
                val days = ((nowMs - item.lastPlayedAtMs).coerceAtLeast(0L)) / DAY_MS
                val decay = 0.5.pow(days / HALF_LIFE_DAYS)
                total += 0.16 * decay
                when {
                    days < 1.0 -> total -= 0.60
                    days < 3.0 -> total -= 0.30
                    days < 7.0 -> total -= 0.10
                    t.playCount >= 2 && days >= 14.0 -> {
                        val redisc = 0.28 + min(0.20, (days - 14.0) / 100.0)
                        total += redisc
                        offer(redisc, "Lama tak diputar")
                    }
                }
            }

            if (t.playCount == 0) {
                val importDays = ((nowMs - item.importedAtMs).coerceAtLeast(0L)) / DAY_MS
                val newBonus = 0.12 + if (importDays < 30.0) 0.10 else 0.0
                total += newBonus
                offer(newBonus, "Baru buatmu")
            }

            if (currentTrackId == t.id) total -= 1.0
            total += 0.06 * fnv1aSeeded("${t.id}$seedTag")

            LocalRecommendationItem(track = t, reason = bestLabel, score = total)
        }.sortedByDescending { it.score }

        val explorePool = scored
            .filter { it.track.playCount <= 1 && it.track.id != currentTrackId }
            .sortedByDescending { fnv1aSeeded("e${it.track.id}$seedTag") }
            .toMutableList()

        val maxOut = minOf(limit, 30, scored.size)
        val result = ArrayList<LocalRecommendationItem>(maxOut)
        val used = HashSet<String>()
        val artistCounts = HashMap<String, Int>()

        for (slot in 0 until maxOut) {
            val cap = if (slot < 12) 2 else 3
            val prevArtist = result.lastOrNull()?.track?.artistName?.lowercase()

            if (slot in EXPLORE_SLOTS) {
                val idx = explorePool.indexOfFirst { cand ->
                    cand.track.id !in used && (artistCounts[cand.track.artistName.lowercase()] ?: 0) < cap
                }
                if (idx >= 0) {
                    val picked = explorePool.removeAt(idx)
                    val label = if (picked.reason == "Pilihan Mupa") {
                        if (picked.track.playCount == 0) "Coba ini" else "Coba lagi"
                    } else {
                        picked.reason
                    }
                    used.add(picked.track.id)
                    val key = picked.track.artistName.lowercase()
                    artistCounts[key] = (artistCounts[key] ?: 0) + 1
                    result.add(picked.copy(reason = label))
                    continue
                }
            }

            var best: LocalRecommendationItem? = null
            var bestMmr = Double.NEGATIVE_INFINITY
            var fallback: LocalRecommendationItem? = null
            var fallbackMmr = Double.NEGATIVE_INFINITY
            var inspected = 0
            for (cand in scored) {
                if (cand.track.id in used) continue
                inspected++
                val key = cand.track.artistName.lowercase()
                val count = artistCounts[key] ?: 0
                val samePrev = if (prevArtist == key) 0.20 else 0.0
                val mmr = cand.score - 0.18 * count - samePrev
                if (count < cap) {
                    if (mmr > bestMmr) {
                        bestMmr = mmr
                        best = cand
                    }
                } else if (mmr > fallbackMmr) {
                    fallbackMmr = mmr
                    fallback = cand
                }
                if (inspected >= 40) break
            }
            val chosen = best ?: fallback ?: break
            used.add(chosen.track.id)
            val key = chosen.track.artistName.lowercase()
            artistCounts[key] = (artistCounts[key] ?: 0) + 1
            result.add(chosen)
        }
        return result
    }

    fun buildRepeatAgain(
        items: List<LocalTrackItem>,
        nowMs: Long = System.currentTimeMillis(),
        limit: Int = 12,
    ): List<Track> {
        val tenDaysMs = 10L * 86_400_000L
        return items
            .filter {
                (it.track.playCount >= 2 || it.track.isFavorite) &&
                    it.lastPlayedAtMs > 0L &&
                    (nowMs - it.lastPlayedAtMs) >= tenDaysMs
            }
            .sortedByDescending { it.track.playCount }
            .take(limit.coerceAtMost(30))
            .map { it.track }
    }

    fun buildUnplayed(
        items: List<LocalTrackItem>,
        limit: Int = 12,
    ): List<Track> = items
        .filter { it.track.playCount == 0 }
        .sortedByDescending { it.importedAtMs }
        .take(limit.coerceAtMost(30))
        .map { it.track }

    fun aggregateLocalArtists(items: List<LocalTrackItem>): List<Artist> = items
        .filter { it.track.artistName.isNotBlank() }
        .groupBy { it.track.artistName.lowercase() }
        .values
        .sortedByDescending { group -> group.sumOf { it.track.playCount } * 10 + group.size }
        .map { group ->
            val name = group.first().track.artistName
            Artist(
                id = "local-art-${abs(name.lowercase().hashCode())}",
                name = name,
                genre = "Pustaka Lokal (${group.size} lagu)",
                sourceType = AudioSourceType.LOCAL,
            )
        }

    private fun fnv1aSeeded(key: String): Double {
        var h = 2_166_136_261u
        for (b in key.toByteArray(Charsets.UTF_8)) {
            h = h xor b.toUByte().toUInt()
            h *= 16_777_619u
        }
        return (h % 1000u).toDouble() / 1000.0
    }
}
