package com.ojnexus.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone

/**
 * Inline status indicator: a tone-colored dot plus an explicit text label.
 * The label is mandatory — state is never conveyed by color alone.
 */
@Composable
fun NexusStatus(
    label: String,
    tone: NexusTone,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .size(NexusSize.statusDot)
                .background(tone.foregroundColor(NexusTheme.colors), CircleShape),
        )
        Text(
            text = label,
            style = NexusTheme.typography.dataSmall,
            color = NexusTheme.colors.textSecondary,
        )
    }
}
