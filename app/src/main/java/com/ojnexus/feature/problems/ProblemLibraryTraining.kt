package com.ojnexus.feature.problems

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusMotion
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.model.Problem

internal fun buildTrainingProblemIds(problems: List<Problem>): List<Long> =
    problems.map { it.id }.distinct()

@Composable
internal fun LibraryTrainingActionRail(
    problemCount: Int,
    onClick: () -> Unit,
) {
    if (problemCount <= 0) return

    val colors = NexusTheme.colors
    val actionDescription = stringResource(R.string.problems_build_from_view_cd, problemCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceElevated, NexusRadius.sm)
            .border(NexusSize.dividerThickness, colors.border, NexusRadius.sm)
            .clickable(
                role = Role.Button,
                onClickLabel = actionDescription,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = actionDescription
            }
            .padding(NexusSpacing.sm)
            .animateContentSize(
                animationSpec = if (NexusTheme.reduceMotion) {
                    snap()
                } else {
                    tween(
                        NexusMotion.DURATION_NORMAL,
                        easing = NexusMotion.EasingStandard,
                    )
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .width(NexusSize.libraryActionRailWidth)
                .height(NexusSize.libraryActionRailHeight)
                .background(colors.accent, NexusRadius.xs),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.problems_build_from_view),
                style = NexusTheme.typography.sectionLabel,
                color = colors.accent,
            )
            Text(
                text = stringResource(R.string.problems_build_from_view_hint, problemCount),
                style = NexusTheme.typography.dataSmall,
                color = colors.textPrimary,
            )
        }
    }
}
