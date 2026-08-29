package com.ojnexus.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone

/**
 * A label + monospace value pair, optionally with a change indicator (e.g. rating delta).
 * Values must arrive as preformatted strings; number formatting is a data concern, not a
 * component concern.
 */
@Composable
fun NexusMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    change: String? = null,
    changeTone: NexusTone = NexusTone.Neutral,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.xxxs),
    ) {
        Text(
            text = label,
            style = NexusTheme.typography.sectionLabel,
            color = NexusTheme.colors.textTertiary,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = NexusTheme.typography.dataLarge,
                color = NexusTheme.colors.textPrimary,
            )
            if (change != null) {
                Spacer(modifier = Modifier.width(NexusSpacing.xxs))
                Text(
                    text = change,
                    style = NexusTheme.typography.dataSmall,
                    color = changeTone.foregroundColor(NexusTheme.colors),
                )
            }
        }
    }
}
