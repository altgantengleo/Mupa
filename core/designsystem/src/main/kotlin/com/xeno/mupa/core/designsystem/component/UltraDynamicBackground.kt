package com.xeno.mupa.core.designsystem.component

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.UltraDynamicPalette
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Renderer Ultra Dynamic native Mupa — 1 Canvas (drawWithCache), 0 blur layar penuh:
 * - Mengikuti komposisi HTML V15: bgDeep + 3 radial gradient besar (kiri-atas primary,
 *   kanan-bawah secondary, tengah-atas tertiary) + vignette lembut + grain statis 3.5% anti-banding.
 * - Crossfade warna antar lagu 600 ms (dibaca hanya di fase draw).
 * - Drift halus (periode 18 detik, mengikuti feel HTML V15 16-21 dtk) dijeda otomatis saat
 *   lifecycle < STARTED, Mode OLED, Mode Performa, atau animator_duration_scale == 0.
 */
@Composable
fun UltraDynamicBackground(
    palette: UltraDynamicPalette,
    modifier: Modifier = Modifier,
    headerOnly: Boolean = false,
    strengthMultiplier: Float = 1.0f,
) {
    val appearance = LocalMupaAppearance.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAppVisible by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> isAppVisible = true
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> isAppVisible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val animatorScale = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
    }

    val isOled = appearance.isOledMode
    val isUdEnabled = appearance.isUltraDynamicEnabled
    val isPerformance = appearance.isPerformanceMode

    val targetPalette = when {
        isOled -> UltraDynamicPalette.OledBlack
        !isUdEnabled -> UltraDynamicPalette(
            bgDeep = MupaPalette.Background,
            glow1 = MupaPalette.Background,
            glow2 = MupaPalette.Background,
            glow3 = MupaPalette.Background,
            onAccent = MupaPalette.LogoCream,
            glowIntensity = 0f,
        )
        else -> palette
    }

    // Crossfade warna halus 600 ms tanpa overshoot (semua nilai hanya dibaca di fase draw!)
    val crossfadeSpec = remember { MupaMotion.standardTween<Color>(600) }
    val bgDeepState = animateColorAsState(targetPalette.bgDeep, crossfadeSpec, label = "udBg")
    val glow1State = animateColorAsState(targetPalette.glow1, crossfadeSpec, label = "udG1")
    val glow2State = animateColorAsState(targetPalette.glow2, crossfadeSpec, label = "udG2")
    val glow3State = animateColorAsState(targetPalette.glow3, crossfadeSpec, label = "udG3")
    val intensityState = animateFloatAsState(
        targetValue = (targetPalette.glowIntensity * strengthMultiplier).coerceIn(0f, 1.25f),
        animationSpec = MupaMotion.standardTween(600),
        label = "udInt",
    )

    val shouldDrift = isUdEnabled && !isOled && !isPerformance && isAppVisible && animatorScale > 0.01f
    val driftProgressState = if (shouldDrift) {
        val infinite = rememberInfiniteTransition(label = "udDrift")
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 18_000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "udPhase",
        )
    } else {
        remember { mutableStateOf(0.5f) }
    }

    val grainTile = remember { createStaticGrainTile() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val grainBrush = ShaderBrush(ImageShader(grainTile, TileMode.Repeated, TileMode.Repeated))

                onDrawBehind {
                    val bg = bgDeepState.value
                    val g1 = glow1State.value
                    val g2 = glow2State.value
                    val g3 = glow3State.value
                    val k = intensityState.value
                    val phase = driftProgressState.value

                    // Lapisan dasar sinkron sejak frame 0 (anti-kedip / anti-hitam)
                    if (!headerOnly) {
                        drawRect(color = bg)
                    }
                    if (isOled || !isUdEnabled || k <= 0.005f) return@onDrawBehind

                    val w = size.width
                    val h = if (headerOnly) (size.height * 0.46f).coerceAtLeast(w * 0.85f) else size.height
                    val maxDim = max(w, h)

                    // Mode Performa: 1 gradient statis murah, tanpa drift & tanpa grain
                    if (isPerformance) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(g1.copy(alpha = (0.34f * k).coerceIn(0f, 0.48f)), Color.Transparent),
                                center = Offset(w * 0.18f, h * 0.08f),
                                radius = maxDim * 0.92f,
                            ),
                        )
                        return@onDrawBehind
                    }

                    // Offset drift sangat halus mengikuti HTML V15 auroraA / auroraB / themeAmbientDrift
                    val angle = (phase - 0.5f) * 3.14159f
                    val dx1 = sin(angle) * w * 0.08f
                    val dy1 = cos(angle) * h * 0.04f
                    val dx2 = -sin(angle) * w * 0.07f
                    val dy2 = -cos(angle) * h * 0.05f
                    val breath = 0.92f + 0.08f * phase

                    if (headerOnly) {
                        // Komposisi 3 lapis puncak halaman (mengikuti .mx-aurora HTML V15 L5458)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(g1.copy(alpha = (0.44f * k * breath).coerceIn(0f, 0.56f)), Color.Transparent),
                                center = Offset(w * 0.12f + dx1, h * 0.02f + dy1),
                                radius = w * 0.88f,
                            ),
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(g2.copy(alpha = (0.34f * k * breath).coerceIn(0f, 0.44f)), Color.Transparent),
                                center = Offset(w * 0.96f + dx2, h * 0.08f - dy2 * 0.5f),
                                radius = w * 0.78f,
                            ),
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(g3.copy(alpha = (0.18f * k).coerceIn(0f, 0.24f)), Color.Transparent),
                                center = Offset(w * 0.48f - dx1 * 0.5f, h * 0.36f),
                                radius = w * 0.72f,
                            ),
                        )
                    } else {
                        // Komposisi penuh Player / Cinema / Hero (mengikuti HTML V15 L2327-2331)
                        // Blob 1: Primary kiri-atas (11% 0%)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    g1.copy(alpha = (0.46f * k * breath).coerceIn(0f, 0.62f)),
                                    g1.copy(alpha = (0.16f * k * breath).coerceIn(0f, 0.24f)),
                                    Color.Transparent,
                                ),
                                center = Offset(w * 0.11f + dx1, h * 0.04f + dy1),
                                radius = maxDim * 0.86f,
                            ),
                        )
                        // Blob 2: Secondary kanan-bawah (93% 96%) — menjaga karakter 2-warna (mis. biru + pasir!)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    g2.copy(alpha = (0.38f * k * breath).coerceIn(0f, 0.52f)),
                                    g2.copy(alpha = (0.12f * k * breath).coerceIn(0f, 0.20f)),
                                    Color.Transparent,
                                ),
                                center = Offset(w * 0.92f + dx2, h * 0.92f + dy2),
                                radius = maxDim * 0.78f,
                            ),
                        )
                        // Blob 3: Tertiary lift zona artwork tengah-atas (50% 34%)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    g3.copy(alpha = (0.22f * k).coerceIn(0f, 0.32f)),
                                    Color.Transparent,
                                ),
                                center = Offset(w * 0.50f - dx1 * 0.4f, h * 0.34f + dy1 * 0.6f),
                                radius = maxDim * 0.60f,
                            ),
                        )
                        // Vignette tepi halus menjaga keterbacaan teks putih/krem (HTML V15 L2453)
                        drawRect(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.48f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.36f),
                                ),
                                center = Offset(w * 0.5f, h * 0.40f),
                                radius = maxDim * 0.76f,
                            ),
                        )
                        // Grain statis ~3.5% anti-banding gradient gelap
                        drawRect(brush = grainBrush, alpha = 0.036f)
                    }
                }
            },
    )
}

/**
 * Tile grain monokrom 64x64 deterministik (1x alokasi di memori, mencegah banding di layar OLED/AMOLED).
 */
private fun createStaticGrainTile(): ImageBitmap {
    val size = 64
    val pixels = IntArray(size * size)
    var seed = 0x51A7F3C9
    for (i in pixels.indices) {
        seed = seed xor (seed shl 13)
        seed = seed xor (seed ushr 17)
        seed = seed xor (seed shl 5)
        val v = seed and 0xFF
        pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
