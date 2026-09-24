package com.ojnexus.feature.training

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusMotion
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusMetric
import com.ojnexus.core.model.SessionProblem
import com.ojnexus.core.model.TrainingSession
import com.ojnexus.core.model.Verdict
import com.ojnexus.core.ui.labelRes
import com.ojnexus.core.ui.tone
import kotlinx.coroutines.flow.StateFlow

internal fun normalizeSessionSelection(
    selectedProblemId: Long?,
    problems: List<SessionProblem>,
): Long? = selectedProblemId?.takeIf { selected -> problems.any { it.problemId == selected } }

internal fun sessionProblemIdentity(problem: SessionProblem): String = listOfNotNull(
    problem.judge?.uppercase(),
    problem.externalId,
).joinToString(" ").ifBlank { problem.title }

@Composable
internal fun SessionMomentumRail(
    session: TrainingSession,
    problems: List<SessionProblem>,
    selectedProblemId: Long?,
    elapsedFlow: StateFlow<Long>,
    onOpenNext: (Long) -> Unit,
) {
    val elapsedMs by elapsedFlow.collectAsStateWithLifecycle()
    val momentum = deriveSessionMomentum(
        session = session,
        problems = problems,
        elapsedMs = elapsedMs,
        selectedProblemId = selectedProblemId,
    )
    val remaining = momentum.remainingTargetMs
    val remainingLabel = when {
        remaining == null -> stringResource(R.string.session_summary_none)
        remaining < 60_000L -> stringResource(R.string.session_momentum_less_than_minute)
        else -> stringResource(R.string.session_momentum_remaining_minutes, remaining / 60_000L)
    }
    val next = momentum.next
    val nextIdentity = next?.let(::sessionProblemIdentity)
        ?: stringResource(R.string.session_summary_none)
    val colors = NexusTheme.colors

    NexusSection(
        label = stringResource(R.string.session_momentum_title),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            NexusMetric(
                label = stringResource(R.string.session_momentum_now),
                value = momentum.now?.let(::sessionProblemIdentity)
                    ?: stringResource(R.string.session_summary_none),
                modifier = Modifier.weight(1f),
            )
            NexusMetric(
                label = stringResource(R.string.session_momentum_next),
                value = nextIdentity,
                modifier = Modifier.weight(1f),
            )
            NexusMetric(
                label = stringResource(R.string.session_momentum_left),
                value = stringResource(R.string.session_momentum_pending, momentum.pendingCount),
                change = remainingLabel,
                changeTone = NexusTone.Accent,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(NexusSpacing.xs))
        if (momentum.isComplete) {
            Text(
                text = stringResource(R.string.session_momentum_complete),
                style = NexusTheme.typography.dataSmall,
                color = colors.success,
            )
        } else if (next != null) {
            val description = stringResource(
                R.string.session_momentum_open_next_cd,
                nextIdentity,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NexusSize.sessionQuickActionHeight)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = description,
                    ) { onOpenNext(next.problemId) }
                    .semantics(mergeDescendants = true) { contentDescription = description },
            ) {
                NexusTag(
                    text = stringResource(R.string.session_momentum_open_next),
                    tone = NexusTone.Accent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun SessionQuickActions(
    selectedProblem: SessionProblem,
    onLogResult: (Verdict) -> Unit,
) {
    val colors = NexusTheme.colors
    val identity = sessionProblemIdentity(selectedProblem)

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
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(NexusSize.sessionQuickActionHeight)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = description,
                                ) { onLogResult(verdict) }
                                .semantics(mergeDescendants = true) { contentDescription = description },
                        ) {
                            NexusTag(
                                text = label,
                                tone = verdict.tone(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (index != Verdict.entries.chunked(4).lastIndex) {
                    Spacer(modifier = Modifier.height(NexusSpacing.xxxs))
                }
            }
        }
    }
}
