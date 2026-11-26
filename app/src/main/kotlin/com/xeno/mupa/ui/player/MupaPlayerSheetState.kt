package com.xeno.mupa.ui.player

import androidx.compose.animation.core.Animatable
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
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * State tunggal untuk transisi Mini Player (0f) <-> Full Now Playing (1f).
 * - Satu state progress kontinu (0f..1f) dibaca HANYA di fase layout/draw (graphicsLayer/Modifier.layout).
 * - Tween 340ms Emphasized Decelerate untuk tap; critically damped (dampingRatio = 1.0f) tanpa overshoot untuk fling.
 * - Gestur yang diinterupsi di tengah jalan langsung melanjutkan dari progres saat itu.
 * - Status buka/tutup dipulihkan melewati rotasi & process death melalui rememberSaveable.
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
    val isSettledExpanded: Boolean by derivedStateOf { animatable.value > 0.98f }

    fun expand() {
        isDragging = false
        scope.launch {
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationSheetMs),
            )
        }
    }

    fun collapse() {
        isDragging = false
        scope.launch {
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationSheetMs),
            )
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

    /** velocityYPxPerSec negatif = lempar ke atas (buka), positif = lempar ke bawah (tutup). */
    fun onDragEnd(velocityYPxPerSec: Float) {
        isDragging = false
        val distance = travelDistancePx.coerceAtLeast(1f)
        val progressVelocity = (-velocityYPxPerSec / distance).coerceIn(-4.0f, 4.0f)

        val shouldExpand = when {
            velocityYPxPerSec < -550f -> true
            velocityYPxPerSec > 550f -> false
            else -> animatable.value >= 0.40f
        }
        val target = if (shouldExpand) 1f else 0f

        scope.launch {
            if (kotlin.math.abs(progressVelocity) > 0.5f) {
                animatable.animateTo(
                    targetValue = target,
                    animationSpec = MupaMotion.criticallyDampedSpring(stiffness = 300f),
                    initialVelocity = progressVelocity,
                )
            } else {
                animatable.animateTo(
                    targetValue = target,
                    animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationSheetMs),
                )
            }
        }
    }

    companion object {
        val MiniPlayerHeight: Dp = 64.dp
        val BottomNavReservedHeight: Dp = 84.dp
    }
}

@Composable
fun rememberMupaPlayerSheetState(
    initialProgress: Float = 0f,
): MupaPlayerSheetState {
    var savedExpanded by rememberSaveable { mutableStateOf(initialProgress > 0.5f) }
    val scope = rememberCoroutineScope()
    val state = remember(scope) {
        MupaPlayerSheetState(
            scope = scope,
            initialProgress = if (savedExpanded) 1f else initialProgress,
        )
    }
    LaunchedEffect(state.isExpanded) {
        savedExpanded = state.isExpanded
    }
    return state
}
