package com.xeno.mupa.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Spesifikasi gerak Mupa Calm — DILARANG memakai spring overshoot/bounce (dampingRatio < 1f).
 * Tween 200ms (kecil), 340ms (sheet), 350ms (auto-scroll lirik); easing Emphasized Decelerate.
 */
object MupaMotion {
    const val DurationShortMs = 200
    const val DurationMediumMs = 260
    const val DurationSheetMs = 340
    const val DurationLongMs = 340
    const val DurationLyricsScrollMs = 350
    const val DurationColorCrossfadeMs = 420

    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val StandardDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)

    /** Wajib >= 1.0f (nol overshoot). */
    const val NoBounceDampingRatio: Float = Spring.DampingRatioNoBouncy

    fun <T> emphasizedTween(durationMillis: Int = DurationSheetMs): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = EmphasizedDecelerate)

    fun <T> standardTween(durationMillis: Int = DurationShortMs): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = StandardDecelerate)

    fun <T> criticallyDampedSpring(stiffness: Float = 320f): FiniteAnimationSpec<T> =
        spring(dampingRatio = NoBounceDampingRatio, stiffness = stiffness)
}
