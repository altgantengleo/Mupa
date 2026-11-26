package com.xeno.mupa.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Spesifikasi gerak Mupa — DILARANG memakai spring overshoot/bounce (dampingRatio < 1f).
 * Semua transisi memakai kurva decelerate/emphasized halus atau critically damped (dampingRatio = 1f)
 * agar nyaman di mata pada 60Hz maupun 120Hz.
 */
object MupaMotion {
    const val DurationShortMs = 180
    const val DurationMediumMs = 280
    const val DurationLongMs = 360
    const val DurationColorCrossfadeMs = 420

    /** Material 3 Emphasized Decelerate — cepat di awal, melambat sangat halus tanpa pantulan. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Standard Decelerate untuk perubahan tab, indikator, dan fade. */
    val StandardDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    /** Standard Accelerate untuk keluar cepat yang tetap terkendali. */
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)

    /** Konstanta wajib agar selalu >= 1.0f (nol overshoot). */
    const val NoBounceDampingRatio: Float = Spring.DampingRatioNoBouncy

    fun <T> emphasizedTween(durationMillis: Int = DurationMediumMs): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = EmphasizedDecelerate)

    fun <T> standardTween(durationMillis: Int = DurationShortMs): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = StandardDecelerate)

    /** Hanya untuk gesture settle yang perlu membawa momentum kecepatan jari tanpa overshoot. */
    fun <T> criticallyDampedSpring(stiffness: Float = 320f): FiniteAnimationSpec<T> =
        spring(dampingRatio = NoBounceDampingRatio, stiffness = stiffness)
}
