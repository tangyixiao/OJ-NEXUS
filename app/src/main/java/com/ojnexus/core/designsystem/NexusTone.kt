package com.ojnexus.core.designsystem

import androidx.compose.runtime.Immutable

/**
 * Semantic status tones. Components that express a state take a [NexusTone] instead of a raw
 * color, so the palette stays centralized and states are never communicated by hue alone —
 * every tone also carries a text label at its call site.
 */
@Immutable
enum class NexusTone {
    Accent,
    Success,
    Danger,
    Warning,
    Neutral,
}
