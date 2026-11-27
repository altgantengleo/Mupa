package com.xeno.mupa.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pemetaan murni (pure-Kotlin) koreografi animasi cover & elemen Mini <-> Full dari satu progres 0..1.
 * Arah buka dan tutup memakai pemetaan simetris yang sama persis.
 */
object MupaPlayerMorphMath {
    const val OPEN_DURATION_MS = 520
    const val CLOSE_DURATION_MS = 440
    const val MIN_RELEASE_DURATION_MS = 250
    val COVER_MORPH_EASING = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Isi Mini Player memudar penuh 0% -> 25%. */
    fun miniContentAlpha(progress: Float): Float =
        (1f - progress.coerceIn(0f, 1f) / 0.25f).coerceIn(0f, 1f)

    /** Satu composable cover bergerak/membesar 0% -> 100% secara simetris. */
    fun coverMorphFraction(progress: Float): Float =
        progress.coerceIn(0f, 1f)

    /** Latar Ultra Dynamic muncul 0% -> 70%. */
    fun udBackdropAlpha(progress: Float): Float =
        (progress.coerceIn(0f, 1f) / 0.70f).coerceIn(0f, 1f)

    /** Bottom Nav turun & memudar 0% -> 40%. */
    fun bottomNavAlpha(progress: Float): Float =
        (1f - progress.coerceIn(0f, 1f) / 0.40f).coerceIn(0f, 1f)

    fun bottomNavSlideFraction(progress: Float): Float =
        (progress.coerceIn(0f, 1f) / 0.40f).coerceIn(0f, 1f)

    /** Bayangan cover naik seiring progres 0% -> 100%. */
    fun coverShadowFraction(progress: Float): Float =
        progress.coerceIn(0f, 1f)

    /**
     * Header / Judul / Seekbar / Kontrol masuk pada rentang 35% -> 100%
     * dengan jeda bertahap ~40 ms antar elemen (dipetakan ke offset progres ~0.075).
     */
    fun staggeredElementProgress(progress: Float, stepIndex: Int): Float {
        val start = (0.35f + stepIndex * 0.075f).coerceAtMost(0.65f)
        val span = (1f - start).coerceAtLeast(0.25f)
        return ((progress.coerceIn(0f, 1f) - start) / span).coerceIn(0f, 1f)
    }

    /** Translasi vertikal masuk (16dp -> 0dp) untuk elemen bertahap. */
    fun staggeredSlideDp(progress: Float, stepIndex: Int): Float =
        16f * (1f - staggeredElementProgress(progress, stepIndex))

    /** Durasi proporsional sisa jarak saat drag dilepas (min 250 ms, tanpa spring overshoot). */
    fun proportionalReleaseDurationMs(currentProgress: Float, targetProgress: Float): Int {
        val remaining = abs(targetProgress - currentProgress).coerceIn(0f, 1f)
        val base = if (targetProgress >= currentProgress) OPEN_DURATION_MS else CLOSE_DURATION_MS
        return (remaining * base).roundToInt().coerceIn(MIN_RELEASE_DURATION_MS, base)
    }
}

/**
 * State tunggal untuk transisi Mini Player (0f) <-> Full Now Playing (1f).
 * - Buka 520 ms, tutup 440 ms, easing cubic-bezier(0.2, 0, 0, 1), NOL spring overshoot.
 * - Drag mengikuti jari 1:1; lepas drag menyelesaikan via tween proporsional (min 250 ms).
 * - Seluruh nilai progres dibaca di fase layout/draw, bukan composition.
 */
@Stable
class MupaPlayerSheetState(
    private val scope: CoroutineScope,
    initialProgress: Float = 0f,
) {
    private val animatable = Animatable(initialProgress.coerceIn(0f, 1f))

    var travelDistancePx: Float = 1f
        internal set

    var isDragging: Boolean by mutableStateOf(false)
        private set

    val progress: Float
        get() = animatable.value

    val isExpanded: Boolean by derivedStateOf { animatable.value > 0.5f }
    val isCollapsed: Boolean by derivedStateOf { animatable.value < 0.02f }
    val isVisible: Boolean by derivedStateOf { animatable.value > 0.001f }
    val isSettledExpanded: Boolean by derivedStateOf { animatable.value > 0.99f }

    fun expand() {
        isDragging = false
        val dur = MupaPlayerMorphMath.proportionalReleaseDurationMs(animatable.value, 1f)
        scope.launch {
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (animatable.value < 0.02f) MupaPlayerMorphMath.OPEN_DURATION_MS else dur,
                    easing = MupaPlayerMorphMath.COVER_MORPH_EASING,
                ),
            )
        }
    }

    fun collapse() {
        isDragging = false
        val dur = MupaPlayerMorphMath.proportionalReleaseDurationMs(animatable.value, 0f)
        scope.launch {
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = if (animatable.value > 0.98f) MupaPlayerMorphMath.CLOSE_DURATION_MS else dur,
                    easing = MupaPlayerMorphMath.COVER_MORPH_EASING,
                ),
            )
        }
    }

    /** Dipakai pengujian frame-by-frame animasi di progres pasti (0, 0.25, 0.5, 0.75, 1). */
    fun snapToForTest(value: Float) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.snapTo(value.coerceIn(0f, 1f))
        }
    }

    fun onDragStart() {
        isDragging = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.stop()
        }
    }

    /** deltaYPx negatif = seret ke atas (menuju 1f), positif = seret ke bawah (menuju 0f). */
    fun onDragDelta(deltaYPx: Float) {
        val distance = travelDistancePx.coerceAtLeast(1f)
        val deltaProgress = -deltaYPx / distance
        val next = (animatable.value + deltaProgress).coerceIn(0f, 1f)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.snapTo(next)
        }
    }

    /** Lepas drag: tuntaskan ke status terdekat lewat tween proporsional (min 250 ms, 0 overshoot). */
    fun onDragEnd(
        velocityYPxPerSec: Float,
        collapseVelocityThreshold: Float = 950f,
        stayExpandedMinProgress: Float = 0.65f,
    ) {
        isDragging = false
        val shouldExpand = when {
            velocityYPxPerSec < -650f -> true
            velocityYPxPerSec > collapseVelocityThreshold -> false
            else -> animatable.value >= stayExpandedMinProgress
        }
        val target = if (shouldExpand) 1f else 0f
        val durationMs = MupaPlayerMorphMath.proportionalReleaseDurationMs(animatable.value, target)
        scope.launch {
            animatable.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = durationMs,
                    easing = MupaPlayerMorphMath.COVER_MORPH_EASING,
                ),
            )
        }
    }

    companion object {
        val MiniPlayerHeight: Dp = 64.dp
        val BottomNavReservedHeight: Dp = 84.dp
    }
}

@Composable
fun rememberMupaPlayerSheetState(): MupaPlayerSheetState {
    val scope = rememberCoroutineScope()
    var savedExpanded by rememberSaveable { mutableStateOf(false) }
    val state = remember(scope) {
        MupaPlayerSheetState(
            scope = scope,
            initialProgress = if (savedExpanded) 1f else 0f,
        )
    }
    LaunchedEffect(state.isExpanded) {
        savedExpanded = state.isExpanded
    }
    return state
}
