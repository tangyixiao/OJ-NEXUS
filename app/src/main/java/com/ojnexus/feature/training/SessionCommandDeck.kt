package com.ojnexus.feature.training

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusMotion
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.model.SessionProblem
import com.ojnexus.core.model.Verdict
import com.ojnexus.core.ui.labelRes
import com.ojnexus.core.ui.tone

internal fun normalizeSessionSelection(
    selectedProblemId: Long?,
    problems: List<SessionProblem>,
): Long? = selectedProblemId?.takeIf { selected -> problems.any { it.problemId == selected } }

@Composable
internal fun SessionQuickActions(
    selectedProblem: SessionProblem,
    onLogResult: (Verdict) -> Unit,
) {
    val colors = NexusTheme.colors
    val identity = listOfNotNull(
        selectedProblem.judge?.uppercase(),
        selectedProblem.externalId,
    ).joinToString(" ").ifBlank { selectedProblem.title }

    NexusSection(label = stringResource(R.string.session_quick_log_result)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = if (NexusTheme.reduceMotion) snap() else tween(
                        NexusMotion.DURATION_NORMAL,
                        easing = NexusMotion.EasingStandard,
                    ),
                ),
        ) {
            Text(
                text = stringResource(R.string.session_quick_selected, identity),
                style = NexusTheme.typography.dataSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = NexusSpacing.xs),
            )
            Verdict.entries.chunked(4).forEachIndexed { index, verdictRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxxs),
                ) {
                    verdictRow.forEach { verdict ->
                        val label = stringResource(verdict.labelRes())
                        val description = stringResource(
                            R.string.session_quick_verdict_cd,
                            label,
                            identity,
                        )
                        NexusTag(
                            text = label,
                            tone = verdict.tone(),
                            selected = verdict == Verdict.AC,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = description }
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = description,
                                ) { onLogResult(verdict) },
                        )
                    }
                }
                if (index != Verdict.entries.chunked(4).lastIndex) {
                    Spacer(modifier = Modifier.height(NexusSpacing.xxxs))
                }
            }
        }
    }
}
