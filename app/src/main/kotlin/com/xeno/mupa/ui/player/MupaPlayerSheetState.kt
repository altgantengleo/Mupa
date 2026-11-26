package com.xeno.mupa.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * State tunggal untuk transisi Mini Player (0f) <-> Full Now Playing (1f).
 *
 * Prinsip anti-jank & anti-kedip (sesuai docs/AUDIT.md):
 * - Satu state progress kontinu (0f..1f); dibaca lewat lambda di fase layout/draw, bukan composition.
 * - Tidak pernah di-recreate saat WindowInsets/bounds berubah (bounds disimpan di field mutable).
 * - Drag 1:1 memakai CoroutineStart.UNDISPATCHED agar responsif di layar 60/120 Hz.
 * - Nol spring overshoot (memakai emphasizedTween untuk tap dan criticallyDampedSpring damping=1f untuk fling).
 */
@Stable
class MupaPlayerSheetState(
    private val scope: CoroutineScope,
    initialProgress: Float = 0f,
) {
    private val animatable = Animatable(initialProgress.coerceIn(0f, 1f))

    /** Jarak tempuh vertikal (px) antara collapsed mini player dan expanded full player. */
    var travelDistancePx: Float = 1f
        internal set

    var isDragging: Boolean by mutableStateOf(false)
        private set

    /** Nilai kontinu 0f (mini) .. 1f (full). Baca hanya di dalam graphicsLayer / layout / draw! */
    val progress: Float
        get() = animatable.value

    val isExpanded: Boolean by derivedStateOf { animatable.value > 0.5f }
    val isCollapsed: Boolean by derivedStateOf { animatable.value < 0.02f }
    val isSettledExpanded: Boolean by derivedStateOf { animatable.value > 0.98f }

    fun expand() {
        isDragging = false
        scope.launch {
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationLongMs),
            )
        }
    }

    fun collapse() {
        isDragging = false
        scope.launch {
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationLongMs),
            )
        }
    }

    fun onDragStart() {
        isDragging = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.stop()
        }
    }

    /**
     * deltaY negatif = seret ke atas (menuju 1f), deltaY positif = seret ke bawah (menuju 0f).
     */
    fun onDragDelta(deltaYPx: Float) {
        val distance = travelDistancePx.coerceAtLeast(1f)
        val deltaProgress = -deltaYPx / distance
        val next = (animatable.value + deltaProgress).coerceIn(0f, 1f)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.snapTo(next)
        }
    }

    /**
     * velocityYPxPerSec negatif = lempar ke atas (buka), positif = lempar ke bawah (tutup).
     */
    fun onDragEnd(velocityYPxPerSec: Float) {
        isDragging = false
        val distance = travelDistancePx.coerceAtLeast(1f)
        val progressVelocity = (-velocityYPxPerSec / distance).coerceIn(-4.5f, 4.5f)

        val shouldExpand = when {
            velocityYPxPerSec < -650f -> true
            velocityYPxPerSec > 650f -> false
            else -> animatable.value >= 0.40f
        }
        val target = if (shouldExpand) 1f else 0f

        scope.launch {
            animatable.animateTo(
                targetValue = target,
                animationSpec = MupaMotion.criticallyDampedSpring(stiffness = 300f),
                initialVelocity = progressVelocity,
            )
        }
    }

    companion object {
        val MiniPlayerHeight: Dp = 64.dp
        val BottomNavReservedHeight: Dp = 88.dp
    }
}

@Composable
fun rememberMupaPlayerSheetState(
    initialProgress: Float = 0f,
): MupaPlayerSheetState {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        MupaPlayerSheetState(
            scope = scope,
            initialProgress = initialProgress,
        )
    }
}
