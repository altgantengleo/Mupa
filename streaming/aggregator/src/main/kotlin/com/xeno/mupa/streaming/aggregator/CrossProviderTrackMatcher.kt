package com.xeno.mupa.streaming.aggregator

import com.xeno.mupa.core.model.SmartQueueFilter
import com.xeno.mupa.core.model.Track
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Pencocokan lintas provider (Katalog Deezer/iTunes -> Stream InnerTube):
 * - Skor berdasarkan judul ternormalisasi + artis ternormalisasi + toleransi durasi +-3 detik.
 * - Penalti berat bila target bersih tetapi kandidat adalah versi alternatif (live/slowed/karaoke/remix).
 * - Bila skor < MIN_MATCH_SCORE (0.72), JANGAN memutar lagu yang salah!
 * - Menyimpan pemetaan terverifikasi di cache thread-safe (ConcurrentHashMap).
 */
object CrossProviderTrackMatcher {
    const val MIN_MATCH_SCORE: Float = 0.72f
    const val STRICT_DURATION_TOLERANCE_MS: Long = 3_000L

    private val verifiedIdCache = ConcurrentHashMap<String, String>()

    data class MatchResult(
        val candidate: Track,
        val score: Float,
    )

    fun getCachedStreamId(catalogTrackId: String): String? = verifiedIdCache[catalogTrackId]

    fun cacheVerifiedMapping(catalogTrackId: String, streamTrackId: String) {
        if (catalogTrackId.isNotBlank() && streamTrackId.isNotBlank()) {
            verifiedIdCache[catalogTrackId] = streamTrackId
        }
    }

    fun clearCache() {
        verifiedIdCache.clear()
    }

    fun scoreCandidate(
        targetTitle: String,
        targetArtist: String,
        targetDurationMs: Long,
        candidate: Track,
    ): Float {
        val normTargetTitle = SmartQueueFilter.normalizeTitle(targetTitle)
        val normCandTitle = SmartQueueFilter.normalizeTitle(candidate.title)
        val normTargetArtist = SmartQueueFilter.normalizeArtist(targetArtist)
        val normCandArtist = SmartQueueFilter.normalizeArtist(candidate.artistName)

        if (normTargetTitle.isEmpty() || normCandTitle.isEmpty()) return 0f

        val titleScore = tokenSimilarity(normTargetTitle, normCandTitle) * 0.50f
        val artistScore = if (normTargetArtist.isNotEmpty() && normCandArtist.isNotEmpty()) {
            tokenSimilarity(normTargetArtist, normCandArtist) * 0.35f
        } else {
            0.18f
        }

        val durationScore = when {
            targetDurationMs <= 0L || candidate.durationMs <= 0L -> 0.10f
            else -> {
                val diff = abs(targetDurationMs - candidate.durationMs)
                when {
                    diff <= STRICT_DURATION_TOLERANCE_MS -> 0.15f
                    diff <= 8_000L -> 0.08f
                    diff <= 15_000L -> 0.0f
                    else -> -0.35f
                }
            }
        }

        val targetIsAlt = SmartQueueFilter.isAlternateVersionTitle(targetTitle)
        val candIsAlt = SmartQueueFilter.isAlternateVersionTitle(candidate.title)
        val versionPenalty = if (!targetIsAlt && candIsAlt) -0.40f else 0f

        return (titleScore + artistScore + durationScore + versionPenalty).coerceIn(0f, 1f)
    }

    fun findBestMatch(
        targetTrackId: String,
        targetTitle: String,
        targetArtist: String,
        targetDurationMs: Long,
        candidates: List<Track>,
    ): MatchResult? {
        val best = candidates.map { cand ->
            MatchResult(
                candidate = cand,
                score = scoreCandidate(targetTitle, targetArtist, targetDurationMs, cand),
            )
        }.maxByOrNull { it.score } ?: return null

        if (best.score < MIN_MATCH_SCORE) return null
        cacheVerifiedMapping(targetTrackId, best.candidate.id)
        return best
    }

    private fun tokenSimilarity(a: String, b: String): Float {
        if (a == b) return 1.0f
        if (a.contains(b) || b.contains(a)) return 0.90f
        val ta = a.split(' ').filter { it.isNotBlank() }.toSet()
        val tb = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0f
        val intersect = ta.intersect(tb).size.toFloat()
        val union = ta.union(tb).size.toFloat()
        return if (union > 0f) intersect / union else 0f
    }
}
