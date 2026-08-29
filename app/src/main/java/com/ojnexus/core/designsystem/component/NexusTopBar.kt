package com.ojnexus.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme

/**
 * Flat single-line top bar: uppercase label left, status/actions right, hairline below.
 * Deliberately not a Material app bar — no elevation, no scrolling behavior.
 */
@Composable
fun NexusTopBar(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = NexusTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NexusSize.topBarHeight)
                .background(colors.background)
                .padding(horizontal = NexusSpacing.screenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = NexusTheme.typography.sectionLabel,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        NexusDivider()
    }
}
