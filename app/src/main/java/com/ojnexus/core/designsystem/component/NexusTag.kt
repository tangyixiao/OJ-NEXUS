package com.ojnexus.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone

/**
 * Compact bordered label used for verdicts, judges, tags and filters.
 * Selected/active states use the tone foreground + container; inactive ones stay hairline gray.
 */
@Composable
fun NexusTag(
    text: String,
    modifier: Modifier = Modifier,
    tone: NexusTone = NexusTone.Neutral,
    selected: Boolean = false,
) {
    val colors = NexusTheme.colors
    val foreground = if (selected || tone != NexusTone.Neutral) {
        tone.foregroundColor(colors)
    } else {
        colors.textSecondary
    }
    val background = if (selected) {
        tone.containerColor(colors) ?: colors.surfaceElevated
    } else {
        Color.Transparent
    }
    val border = if (selected) {
        BorderStroke(1.dp, foreground)
    } else {
        BorderStroke(1.dp, colors.border)
    }

    Box(
        modifier = modifier
            .background(background, NexusRadius.xs)
            .border(border, NexusRadius.xs)
            .padding(horizontal = NexusSpacing.xs, vertical = NexusSpacing.xxxs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = NexusTheme.typography.dataSmall,
            color = foreground,
        )
    }
}
