package com.ojnexus.feature.training

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.designsystem.component.foregroundColor
import com.ojnexus.core.model.ReviewResult
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.labelRes
import com.ojnexus.core.ui.tone

/**
 * Focused review view for one problem: recall first, then record the outcome.
 * After an outcome the scheduler's decision is shown; CLOSE returns to the queue.
 */
@Composable
fun ReviewSessionScreen(
    problemId: Long,
    onDone: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ReviewSessionViewModel>(
        key = "review-$problemId",
        factory = ContainerViewModelFactory(container) {
            ReviewSessionViewModel(
                problemId = problemId,
                problemRepository = it.problemRepository,
                reviewRepository = it.reviewRepository,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        NexusTopBar(title = stringResource(R.string.review_session_title))
        when (val s = state) {
            Loadable.Loading -> Box(Modifier.fillMaxSize())
            is Loadable.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.detail_not_found),
                    style = NexusTheme.typography.data,
                    color = NexusTheme.colors.textTertiary,
                )
            }
            is Loadable.Ready -> ReviewSessionContent(
                uiState = s.value,
                onRecord = viewModel::record,
                onDone = onDone,
            )
        }
    }
}

@Composable
private fun ReviewSessionContent(
    uiState: ReviewSessionUiState,
    onRecord: (ReviewResult) -> Unit,
    onDone: () -> Unit,
) {
    val colors = NexusTheme.colors
    val detail = uiState.detail
    val review = detail.review

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexusSpacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(NexusSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = detail.problem.key.judge.displayName,
                style = NexusTheme.typography.sectionLabel,
                color = colors.textTertiary,
                modifier = Modifier.padding(end = NexusSpacing.xxs),
            )
            Text(
                text = detail.problem.key.externalId,
                style = NexusTheme.typography.dataLarge,
                color = colors.accent,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = detail.problem.title,
            style = NexusTheme.typography.title,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = NexusSpacing.xxs),
        )
        Spacer(modifier = Modifier.height(NexusSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
            if (review != null) {
                NexusTag(
                    text = stringResource(R.string.review_stage_label, review.stage + 1),
                    tone = NexusTone.Accent,
                    selected = true,
                )
            }
            if (detail.problem.difficulty != null) {
                NexusTag(text = detail.problem.difficulty.toString(), tone = NexusTone.Neutral)
            }
        }

        Spacer(modifier = Modifier.height(NexusSpacing.lg))
        NexusDivider()
        Spacer(modifier = Modifier.height(NexusSpacing.md))

        val outcome = uiState.lastOutcome
        if (outcome == null) {
            NexusSection(label = stringResource(R.string.review_session_title)) {
                Text(
                    text = stringResource(R.string.review_prompt),
                    style = NexusTheme.typography.body,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.lg))
                Text(
                    text = stringResource(R.string.detail_section_review),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xs))
                Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                        OutcomeButton(ReviewResult.PASS) { onRecord(ReviewResult.PASS) }
                        OutcomeButton(ReviewResult.HARD) { onRecord(ReviewResult.HARD) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                        OutcomeButton(ReviewResult.FAIL) { onRecord(ReviewResult.FAIL) }
                        OutcomeButton(ReviewResult.SKIP) { onRecord(ReviewResult.SKIP) }
                    }
                }
            }
        } else {
            NexusSection(label = stringResource(R.string.review_next_scheduled, outcome.nextIntervalDays)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NexusTag(
                        text = stringResource(outcome.result.labelRes()),
                        tone = outcome.result.tone(),
                        selected = true,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.review_stage_label, outcome.nextStage + 1),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(NexusSpacing.lg))
                Box(
                    modifier = Modifier
                        .background(colors.surface, NexusRadius.sm)
                        .border(1.dp, colors.accent, NexusRadius.sm)
                        .clickable(role = Role.Button) { onDone() }
                        .padding(horizontal = NexusSpacing.md, vertical = NexusSpacing.xs)
                        .align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = stringResource(R.string.action_close),
                        style = NexusTheme.typography.data,
                        color = colors.accent,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(NexusSpacing.xxl))
    }
}

@Composable
private fun OutcomeButton(result: ReviewResult, onClick: () -> Unit) {
    val colors = NexusTheme.colors
    val tone = result.tone().foregroundColor(colors)
    Box(
        modifier = Modifier
            .background(colors.surface, NexusRadius.sm)
            .border(1.dp, tone, NexusRadius.sm)
            .clickable(role = Role.Button) { onClick() }
            .padding(horizontal = NexusSpacing.md, vertical = NexusSpacing.sm),
    ) {
        Text(
            text = stringResource(result.labelRes()),
            style = NexusTheme.typography.data,
            color = tone,
        )
    }
}
