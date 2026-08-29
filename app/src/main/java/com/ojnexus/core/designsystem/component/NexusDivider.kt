package com.ojnexus.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusTheme

/**
 * Hairline separator — the primary structural element of the UI.
 * [insetStart]/[insetEnd] indent the line to align with row content when a divider
 * sits inside a list.
 */
@Composable
fun NexusDivider(
    modifier: Modifier = Modifier,
    insetStart: Dp? = null,
    insetEnd: Dp? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = insetStart ?: 0.dp, end = insetEnd ?: 0.dp)
            .height(NexusSize.dividerThickness)
            .background(NexusTheme.colors.border),
    )
}
