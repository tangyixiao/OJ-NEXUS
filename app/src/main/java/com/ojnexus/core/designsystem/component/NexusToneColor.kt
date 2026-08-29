package com.ojnexus.core.designsystem.component

import androidx.compose.ui.graphics.Color
import com.ojnexus.core.designsystem.NexusColors
import com.ojnexus.core.designsystem.NexusTone

/**
 * Resolves a semantic tone to its foreground color. Fills that need a dark tinted background
 * use [toneContainer]. Neutral intentionally has no container fill.
 */
fun NexusTone.foregroundColor(colors: NexusColors): Color = when (this) {
    NexusTone.Accent -> colors.accent
    NexusTone.Success -> colors.success
    NexusTone.Danger -> colors.danger
    NexusTone.Warning -> colors.warning
    NexusTone.Neutral -> colors.textSecondary
}

fun NexusTone.containerColor(colors: NexusColors): Color? = when (this) {
    NexusTone.Accent -> colors.accentContainer
    NexusTone.Success -> colors.successContainer
    NexusTone.Danger -> colors.dangerContainer
    NexusTone.Warning -> colors.warningContainer
    NexusTone.Neutral -> null
}
