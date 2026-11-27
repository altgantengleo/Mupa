package com.xeno.mupa.core.designsystem.theme

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Palet Ultra Dynamic Mupa (berbasis HTML V15 OKLab + k-means + gamut-safe OKLCH):
 * - bgDeep: permukaan gelap berkarakter cover (L 0.20..0.26, C <= 0.065), kontras teks #F6F4EF >= 4.5:1.
 * - glow1 / glow2 / glow3: 3 warna radial gradient utama (L 0.42..0.60, C 0.10..0.18).
 * - onAccent: warna cerah OKLCH (L >= 0.87) untuk sorotan / wipe lirik.
 * - Cover monokrom/hitam-putih -> netral abu/perak (mengikuti HTML V15 buildNeutralDarkPalette).
 * - Tanpa cover sama sekali -> koral/amber merek Mupa (#FF5A3C / #FFB067).
 */
@Immutable
data class UltraDynamicPalette(
    val bgDeep: Color,
    val glow1: Color,
    val glow2: Color,
    val glow3: Color,
    val onAccent: Color,
    val glowIntensity: Float = 0.85f,
    val isMonochrome: Boolean = false,
    val isNoCoverFallback: Boolean = false,
) {
    fun toDynamicCoverAccent(): DynamicCoverAccent = DynamicCoverAccent(
        primary = glow1,
        secondary = glow2,
    )

    companion object {
        /** Dipakai HANYA bila tidak ada cover sama sekali (placeholder, lokal tanpa artwork, saat memuat). */
        val BrandNoCover = UltraDynamicPalette(
            bgDeep = Color(0xFF1E1719),
            glow1 = MupaPalette.CoralPrimary,
            glow2 = MupaPalette.WarmAmber,
            glow3 = Color(0xFFE06D54),
            onAccent = Color(0xFFFFD8CD),
            glowIntensity = 0.78f,
            isMonochrome = false,
            isNoCoverFallback = true,
        )

        /** Dipakai saat cover monokrom / hitam-putih / abu-abu (mengikuti HTML V15 buildNeutralDarkPalette). */
        val MonochromeNeutral = UltraDynamicPalette(
            bgDeep = Color(0xFF141418),
            glow1 = Color(0xFF9C9CA8),
            glow2 = Color(0xFF70707C),
            glow3 = Color(0xFF52525E),
            onAccent = Color(0xFFE8E8EE),
            glowIntensity = 0.34f,
            isMonochrome = true,
            isNoCoverFallback = false,
        )

        val OledBlack = UltraDynamicPalette(
            bgDeep = MupaPalette.OledBackground,
            glow1 = MupaPalette.OledBackground,
            glow2 = MupaPalette.OledBackground,
            glow3 = MupaPalette.OledBackground,
            onAccent = MupaPalette.LogoCream,
            glowIntensity = 0f,
            isMonochrome = true,
            isNoCoverFallback = false,
        )
    }
}

/**
 * Ekstraktor palet Ultra Dynamic murni Kotlin (tanpa ketergantungan Android pada jalur piksel -> dapat diuji JVM):
 * - Downscale 64x64 dengan center-crop trim tepi 4% (seperti HTML V15).
 * - Konversi ke ruang warna OKLab berbobot Gaussian lembut di tengah.
 * - K-means berbobot k = 5 (deterministik) -> buang klaster abu/hitam/putih (C < 0.04, L < 0.12, L > 0.95).
 * - Pilih 3 warna berdasar populasi x chroma dengan jarak hue minimal >= 25 derajat.
 * - Cover biru + pasir dijamin menghasilkan biru + pasir (bukan rata-rata abu).
 */
object UltraDynamicExtractor {

    private const val SAMPLE_SIZE = 64
    private const val EDGE_TRIM_FRAC = 0.04f
    private const val K_CLUSTERS = 5
    private const val KMEANS_ITERS = 12
    private const val MIN_CHROMA = 0.04f
    private const val MIN_L = 0.12f
    private const val MAX_L = 0.95f
    private const val MIN_HUE_GAP_DEG = 25f

    private data class OkLab(val l: Float, val a: Float, val b: Float) {
        val chroma: Float get() = hypot(a.toDouble(), b.toDouble()).toFloat()
        val hueDeg: Float get() = ((atan2(b.toDouble(), a.toDouble()) * 180.0 / PI) + 360.0).toFloat() % 360f
    }

    private data class WeightedPoint(val l: Float, val a: Float, val b: Float, val w: Float)

    private data class Cluster(
        val l: Float,
        val a: Float,
        val b: Float,
        val chroma: Float,
        val hueDeg: Float,
        val share: Float,
    )

    fun extractFromBitmap(bitmap: Bitmap?): UltraDynamicPalette {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            return UltraDynamicPalette.BrandNoCover
        }
        val safeBmp = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return UltraDynamicPalette.BrandNoCover
        }
        val pixels = IntArray(safeBmp.width * safeBmp.height)
        safeBmp.getPixels(pixels, 0, safeBmp.width, 0, 0, safeBmp.width, safeBmp.height)
        if (safeBmp !== bitmap) safeBmp.recycle()
        return extractFromArgbPixels(pixels, bitmap.width, bitmap.height)
    }

    /**
     * Ekstraksi palet langsung dari array piksel ARGB (dipakai di Android maupun JVM fixture unit test).
     */
    fun extractFromArgbPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): UltraDynamicPalette {
        if (pixels.isEmpty() || width <= 0 || height <= 0) {
            return UltraDynamicPalette.BrandNoCover
        }

        // Center-crop trim 4% seperti HTML V15 agar bingkai hitam/putih thumbnail tidak mengotori palet
        val minSide = min(width, height).toFloat()
        val cropSide = (minSide * (1f - EDGE_TRIM_FRAC * 2f)).coerceAtLeast(1f)
        val startX = (width - cropSide) / 2f
        val startY = (height - cropSide) / 2f

        // Histogram 3D OKLab 12x12x12 untuk kecepatan tinggi & menolak noise 1-piksel (mengikuti HTML V15)
        val bucketSumL = FloatArray(12 * 12 * 12)
        val bucketSumA = FloatArray(12 * 12 * 12)
        val bucketSumB = FloatArray(12 * 12 * 12)
        val bucketSumW = FloatArray(12 * 12 * 12)

        var totalW = 0f
        var satSum = 0f
        var lightW = 0f

        for (sy in 0 until SAMPLE_SIZE) {
            val srcY = (startY + (sy + 0.5f) / SAMPLE_SIZE * cropSide).toInt().coerceIn(0, height - 1)
            val dy = (sy + 0.5f) / SAMPLE_SIZE - 0.5f
            for (sx in 0 until SAMPLE_SIZE) {
                val srcX = (startX + (sx + 0.5f) / SAMPLE_SIZE * cropSide).toInt().coerceIn(0, width - 1)
                val argb = pixels[srcY * width + srcX]
                val alpha = (argb ushr 24) and 0xFF
                if (alpha < 180) continue

                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF

                val lab = srgbIntToOklab(r, g, b)
                val dx = (sx + 0.5f) / SAMPLE_SIZE - 0.5f
                val r2 = dx * dx + dy * dy
                // Bobot spasial Gaussian lembut (tengah 1.30x, tepi 0.60x — persis HTML V15 L11414)
                val w = (0.60 + 0.70 * exp((-r2 * 7.5).toDouble())).toFloat()

                val mx = max(r, max(g, b))
                val mn = min(r, min(g, b))
                val sat = if (mx == 0) 0f else (mx - mn).toFloat() / mx.toFloat()
                val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                satSum += sat * w
                if (lum > 0.76f) lightW += w
                totalW += w

                val qL = (lab.l * 12f).toInt().coerceIn(0, 11)
                val qA = (((lab.a + 0.35f) / 0.70f) * 12f).toInt().coerceIn(0, 11)
                val qB = (((lab.b + 0.35f) / 0.70f) * 12f).toInt().coerceIn(0, 11)
                val bk = (qL * 144) + (qA * 12) + qB
                bucketSumL[bk] += lab.l * w
                bucketSumA[bk] += lab.a * w
                bucketSumB[bk] += lab.b * w
                bucketSumW[bk] += w
            }
        }

        if (totalW <= 1e-4f) return UltraDynamicPalette.BrandNoCover

        val minBucketW = totalW * 0.0008f
        val points = ArrayList<WeightedPoint>(256)
        for (i in bucketSumW.indices) {
            val w = bucketSumW[i]
            if (w >= minBucketW) {
                points.add(WeightedPoint(bucketSumL[i] / w, bucketSumA[i] / w, bucketSumB[i] / w, w))
            }
        }
        if (points.isEmpty()) return UltraDynamicPalette.MonochromeNeutral

        // K-means berbobot k = 5 di OKLab dengan inisialisasi density-aware deterministik
        val k = min(K_CLUSTERS, points.size)
        val cenL = FloatArray(k)
        val cenA = FloatArray(k)
        val cenB = FloatArray(k)

        var firstIdx = 0
        for (i in 1 until points.size) {
            if (points[i].w > points[firstIdx].w) firstIdx = i
        }
        cenL[0] = points[firstIdx].l
        cenA[0] = points[firstIdx].a
        cenB[0] = points[firstIdx].b

        val minDistSq = FloatArray(points.size) { Float.MAX_VALUE }
        var cenCount = 1
        while (cenCount < k) {
            val lastL = cenL[cenCount - 1]
            val lastA = cenA[cenCount - 1]
            val lastB = cenB[cenCount - 1]
            var bestIdx = -1
            var bestVal = -1f
            for (i in points.indices) {
                val p = points[i]
                val d2 = (p.l - lastL) * (p.l - lastL) + (p.a - lastA) * (p.a - lastA) + (p.b - lastB) * (p.b - lastB)
                if (d2 < minDistSq[i]) minDistSq[i] = d2
                val v = minDistSq[i] * p.w.toDouble().pow(0.72).toFloat()
                if (v > bestVal) {
                    bestVal = v
                    bestIdx = i
                }
            }
            if (bestIdx < 0 || bestVal < 1e-7f) break
            cenL[cenCount] = points[bestIdx].l
            cenA[cenCount] = points[bestIdx].a
            cenB[cenCount] = points[bestIdx].b
            cenCount++
        }

        val assign = IntArray(points.size)
        val sumL = FloatArray(cenCount)
        val sumA = FloatArray(cenCount)
        val sumB = FloatArray(cenCount)
        val sumW = FloatArray(cenCount)

        repeat(KMEANS_ITERS) {
            sumL.fill(0f)
            sumA.fill(0f)
            sumB.fill(0f)
            sumW.fill(0f)
            for (i in points.indices) {
                val p = points[i]
                var best = 0
                var bestD = Float.MAX_VALUE
                for (c in 0 until cenCount) {
                    val dl = p.l - cenL[c]
                    val da = p.a - cenA[c]
                    val db = p.b - cenB[c]
                    val d2 = dl * dl + da * da + db * db
                    if (d2 < bestD) {
                        bestD = d2
                        best = c
                    }
                }
                assign[i] = best
                val w = p.w
                sumL[best] += p.l * w
                sumA[best] += p.a * w
                sumB[best] += p.b * w
                sumW[best] += w
            }
            for (c in 0 until cenCount) {
                if (sumW[c] > 0f) {
                    cenL[c] = sumL[c] / sumW[c]
                    cenA[c] = sumA[c] / sumW[c]
                    cenB[c] = sumB[c] / sumW[c]
                }
            }
        }

        val clusterWeights = FloatArray(cenCount)
        var totalClusterW = 0f
        for (i in points.indices) {
            val w = points[i].w
            clusterWeights[assign[i]] += w
            totalClusterW += w
        }

        val allClusters = (0 until cenCount).mapNotNull { c ->
            val share = clusterWeights[c] / max(1e-6f, totalClusterW)
            if (share <= 0.004f) return@mapNotNull null
            val lab = OkLab(cenL[c], cenA[c], cenB[c])
            Cluster(
                l = lab.l,
                a = lab.a,
                b = lab.b,
                chroma = lab.chroma,
                hueDeg = lab.hueDeg,
                share = share,
            )
        }

        // Buang klaster hampir abu/hitam/putih (C < 0.04, L < 0.12, atau L > 0.95)
        val chromatic = allClusters.filter { c ->
            c.chroma >= MIN_CHROMA && c.l >= MIN_L && c.l <= MAX_L
        }
        val chromaticShare = chromatic.sumOf { it.share.toDouble() }.toFloat()
        if (chromatic.isEmpty() || chromaticShare < 0.035f) {
            // Cover benar-benar monokrom/hitam-putih -> abu/perak netral (mengikuti HTML V15)
            return UltraDynamicPalette.MonochromeNeutral
        }

        // Pilih 3 warna berdasarkan populasi x chroma dengan jarak hue minimal >= 25 derajat
        val rankedPrimary = chromatic.sortedByDescending { c ->
            c.share.toDouble().pow(0.62) * c.chroma.toDouble().pow(1.05)
        }
        val chosen = mutableListOf(rankedPrimary.first())

        val rankedAccents = chromatic.sortedByDescending { c ->
            val gap = hueGapDeg(c.hueDeg, chosen[0].hueDeg)
            val divBoost = 0.75 + 0.55 * min(1.0, gap / 45.0)
            c.share.toDouble().pow(0.50) * c.chroma.toDouble().pow(1.15) * divBoost
        }
        for (cand in rankedAccents) {
            if (chosen.size >= 3) break
            val farEnough = chosen.all { existing -> hueGapDeg(cand.hueDeg, existing.hueDeg) >= MIN_HUE_GAP_DEG }
            if (farEnough) chosen.add(cand)
        }

        // Lengkapi jika cover hanya punya 1–2 warna kromatik nyata (harmoni ketat setia pada hue cover)
        val base = chosen[0]
        val offsets = floatArrayOf(28f, -28f, 44f)
        var oi = 0
        while (chosen.size < 3) {
            val h = (base.hueDeg + offsets[oi % offsets.size] + 360f) % 360f
            val c = (base.chroma * 0.88f).coerceIn(0.08f, 0.15f)
            val l = (base.l + if (oi % 2 == 0) 0.06f else -0.06f).coerceIn(0.42f, 0.60f)
            chosen.add(Cluster(l = l, a = 0f, b = 0f, chroma = c, hueDeg = h, share = 0.1f))
            oi++
        }

        val c0 = chosen[0]
        val c1 = chosen[1]
        val c2 = chosen[2]

        // Token warna akhir (terkalibrasi & gamut-safe OKLCH):
        // bgDeep: L 0.20..0.28, chroma <= 0.07 -> kontras teks utama #F6F4EF dijamin >> 4.5:1
        val bgDeep = oklchToColorSafe(
            l = (0.20f + (c0.l - 0.35f) * 0.10f).coerceIn(0.20f, 0.26f),
            c = min(c0.chroma * 0.45f, 0.065f).coerceAtLeast(0.025f),
            hDeg = c0.hueDeg,
        )
        val glow1 = oklchToColorSafe(
            l = c0.l.coerceIn(0.45f, 0.60f),
            c = c0.chroma.coerceIn(0.105f, 0.16f),
            hDeg = c0.hueDeg,
        )
        val glow2 = oklchToColorSafe(
            l = c1.l.coerceIn(0.43f, 0.60f),
            c = c1.chroma.coerceIn(0.10f, 0.16f),
            hDeg = c1.hueDeg,
        )
        val glow3 = oklchToColorSafe(
            l = c2.l.coerceIn(0.42f, 0.58f),
            c = c2.chroma.coerceIn(0.10f, 0.15f),
            hDeg = c2.hueDeg,
        )
        // onAccent: L >= 0.87 untuk sorotan / wipe lirik
        val onAccent = oklchToColorSafe(
            l = 0.90f,
            c = min(c0.chroma * 0.60f, 0.09f).coerceAtLeast(0.03f),
            hDeg = c0.hueDeg,
        )

        val avgSat = (satSum / totalW).coerceIn(0f, 1f)
        val lightRatio = (lightW / totalW).coerceIn(0f, 1f)
        val intensity = (0.56f + avgSat * 0.42f + lightRatio * 0.20f).coerceIn(0.58f, 1.10f)

        return UltraDynamicPalette(
            bgDeep = bgDeep,
            glow1 = glow1,
            glow2 = glow2,
            glow3 = glow3,
            onAccent = onAccent,
            glowIntensity = intensity,
            isMonochrome = false,
            isNoCoverFallback = false,
        )
    }

    fun hueGapDeg(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    fun colorToOklch(color: Color): Triple<Float, Float, Float> {
        val lab = srgbIntToOklab(
            (color.red * 255f).roundToInt().coerceIn(0, 255),
            (color.green * 255f).roundToInt().coerceIn(0, 255),
            (color.blue * 255f).roundToInt().coerceIn(0, 255),
        )
        return Triple(lab.l, lab.chroma, lab.hueDeg)
    }

    fun wcagContrastRatio(fg: Color, bg: Color): Float {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return ((lighter + 0.05) / (darker + 0.05)).toFloat()
    }

    private fun relativeLuminance(c: Color): Double {
        val r = srgbToLin(c.red.toDouble())
        val g = srgbToLin(c.green.toDouble())
        val b = srgbToLin(c.blue.toDouble())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun srgbToLin(v: Double): Double =
        if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

    private fun linToSrgb(x: Double): Double =
        if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1.0 / 2.4) - 0.055

    private fun srgbIntToOklab(r: Int, g: Int, b: Int): OkLab {
        val lr = srgbToLin(r / 255.0)
        val lg = srgbToLin(g / 255.0)
        val lb = srgbToLin(b / 255.0)
        val l = cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
        val m = cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
        val s = cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)
        return OkLab(
            l = (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s).toFloat(),
            a = (1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s).toFloat(),
            b = (0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s).toFloat(),
        )
    }

    private fun oklabToLinRgb(l: Double, a: Double, b: Double): Triple<Double, Double, Double> {
        val l_ = l + 0.3963377774 * a + 0.2158037573 * b
        val m_ = l - 0.1055613458 * a - 0.0638541728 * b
        val s_ = l - 0.0894841775 * a - 1.2914855480 * b
        val lc = l_ * l_ * l_
        val mc = m_ * m_ * m_
        val sc = s_ * s_ * s_
        return Triple(
            4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc,
            -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc,
            -0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc,
        )
    }

    /** Gamut clamp aman di OKLCH (persis oklchToRgbSafe HTML V15 L11360) mempertahankan hue & lightness. */
    fun oklchToColorSafe(l: Float, c: Float, hDeg: Float): Color {
        val hRad = hDeg * PI / 180.0
        val cosH = cos(hRad)
        val sinH = sin(hRad)
        fun eval(ch: Double) = oklabToLinRgb(l.toDouble(), ch * cosH, ch * sinH)
        fun inGamut(v: Triple<Double, Double, Double>): Boolean =
            v.first in -0.0005..1.0005 && v.second in -0.0005..1.0005 && v.third in -0.0005..1.0005

        var rgb = eval(c.toDouble())
        if (!inGamut(rgb)) {
            var lo = 0.0
            var hi = c.toDouble()
            var best: Triple<Double, Double, Double>? = null
            repeat(18) {
                val mid = (lo + hi) * 0.5
                val cand = eval(mid)
                if (inGamut(cand)) {
                    lo = mid
                    best = cand
                } else {
                    hi = mid
                }
            }
            rgb = best ?: eval(0.0)
        }
        val r = linToSrgb(rgb.first.coerceIn(0.0, 1.0)).toFloat().coerceIn(0f, 1f)
        val g = linToSrgb(rgb.second.coerceIn(0.0, 1.0)).toFloat().coerceIn(0f, 1f)
        val b = linToSrgb(rgb.third.coerceIn(0.0, 1.0)).toFloat().coerceIn(0f, 1f)
        return Color(red = r, green = g, blue = b, alpha = 1f)
    }
}

/**
 * Cache LRU 64 palet Ultra Dynamic per id lagu, ekstraksi dilakukan di Dispatchers.Default.
 */
object UltraDynamicPaletteCache {
    private val cache = object : LruCache<String, UltraDynamicPalette>(64) {}

    fun get(key: String): UltraDynamicPalette? = synchronized(cache) { cache.get(key) }

    fun put(key: String, palette: UltraDynamicPalette) {
        synchronized(cache) { cache.put(key, palette) }
    }

    fun clear() {
        synchronized(cache) { cache.evictAll() }
    }

    suspend fun extractOrGet(key: String, bitmap: Bitmap?): UltraDynamicPalette {
        if (bitmap == null) return UltraDynamicPalette.BrandNoCover
        get(key)?.let { return it }
        val extracted = withContext(Dispatchers.Default) {
            UltraDynamicExtractor.extractFromBitmap(bitmap)
        }
        put(key, extracted)
        return extracted
    }
}
