package com.ojnexus.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing

/**
 * OJ NEXUS motion tokens.
 *
 * Animation is meaningful and short: 120–300ms. Nothing loops or idles on screen;
 * every animated value must be able to run through a reduce-motion switch later.
 */
object NexusMotion {
    /** State ticks, pulses, indicator snaps. */
    const val DURATION_FAST = 120

    /** Fade/slide between destinations, value changes. */
    const val DURATION_NORMAL = 200

    /** Graph path draws, larger transitions. */
    const val DURATION_SLOW = 300

    val EasingStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EasingExit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
}
