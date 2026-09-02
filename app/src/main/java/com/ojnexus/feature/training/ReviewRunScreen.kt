package com.ojnexus.feature.training

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusMotion
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusMetric
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusStatus
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.designsystem.component.foregroundColor
import com.ojnexus.core.model.ReviewQueueItem
import com.ojnexus.core.model.ReviewResult
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.labelRes
import com.ojnexus.core.ui.tone

private val ReviewRunRailHeight = 4.dp
private val ReviewRunActionHeight = 48.dp

@Composable
fun ReviewRunScreen(onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ReviewRunViewModel>(
        key = "review-run",
        factory = ContainerViewModelFactory(container) {
            ReviewRunViewModel(
                reviewRepository = it.reviewRepository,
                clock = it.clock,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        NexusTopBar(title = stringResource(R.string.review_run_title))
        when (val screenState = state) {
            Loadable.Loading -> Box(Modifier.fillMaxSize())
            is Loadable.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = screenState.message,
                    style = NexusTheme.typography.data,
                    color = NexusTheme.colors.danger,
                )
            }
            is Loadable.Ready -> ReviewRunContent(
                uiState = screenState.value,
                onRecord = viewModel::record,
                onNext = viewModel::next,
                onDone = onDone,
            )
        }
    }
}

@Composable
private fun ReviewRunContent(
    uiState: ReviewRunUiState,
    onRecord: (ReviewResult) -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = NexusTheme.colors
    val reduceMotion = NexusTheme.reduceMotion
    val progress by animateFloatAsState(
        targetValue = reviewRunProgress(uiState.total, uiState.completedCount),
        animationSpec = if (reduceMotion) snap() else tween(
            NexusMotion.DURATION_NORMAL,
            easing = NexusMotion.EasingStandard,
        ),
        label = "review run progress",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexusSpacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(NexusSpacing.md))
        NexusSection(label = stringResource(R.string.review_run_progress)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
            ) {
                NexusMetric(
                    label = stringResource(R.string.review_run_done),
                    value = uiState.completedCount.toString(),
                    changeTone = NexusTone.Success,
                    modifier = Modifier.weight(1f),
                )
                NexusMetric(
                    label = stringResource(R.string.review_run_left),
                    value = uiState.left.toString(),
                    changeTone = if (uiState.left == 0) NexusTone.Success else NexusTone.Accent,
                    modifier = Modifier.weight(1f),
                )
                NexusMetric(
                    label = stringResource(R.string.review_run_total),
                    value = uiState.total.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ReviewRunRailHeight)
                    .background(colors.surface, NexusRadius.xs),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(colors.accent, NexusRadius.xs),
                )
            }
        }

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            Text(
                text = uiState.error,
                style = NexusTheme.typography.dataSmall,
                color = colors.danger,
            )
        }

        Spacer(modifier = Modifier.height(NexusSpacing.md))
        Column(
            modifier = Modifier.animateContentSize(
                animationSpec = if (reduceMotion) snap() else tween(
                    NexusMotion.DURATION_NORMAL,
                    easing = NexusMotion.EasingStandard,
                ),
            ),
        ) {
            when {
                uiState.active != null -> ReviewRunActiveItem(
                    item = uiState.active,
                    isRecording = uiState.isRecording,
                    onRecord = onRecord,
                )
                uiState.lastOutcome != null -> ReviewRunResult(
                    item = uiState.completedItem,
                    outcome = uiState.lastOutcome,
                    isComplete = uiState.isComplete,
                    onNext = onNext,
                    onDone = onDone,
                )
                else -> ReviewRunEmpty(onDone = onDone)
            }
        }
        Spacer(modifier = Modifier.height(NexusSpacing.xxl))
    }
}

@Composable
private fun ReviewRunActiveItem(
    item: ReviewQueueItem,
    isRecording: Boolean,
    onRecord: (ReviewResult) -> Unit,
) {
    val colors = NexusTheme.colors
    NexusSection(label = stringResource(R.string.review_run_current)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.judge.displayName,
                style = NexusTheme.typography.sectionLabel,
                color = colors.accent,
            )
            Spacer(modifier = Modifier.width(NexusSpacing.xxs))
            Text(
                text = stringResource(R.string.review_stage_label, item.stage + 1),
                style = NexusTheme.typography.dataSmall,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.weight(1f))
            item.difficulty?.let { difficulty ->
                NexusTag(text = difficulty.toString(), tone = NexusTone.Neutral)
            }
        }
        Text(
            text = item.problemTitle,
            style = NexusTheme.typography.title,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = NexusSpacing.xxs),
        )
        Spacer(modifier = Modifier.height(NexusSpacing.md))
        NexusDivider()
        Spacer(modifier = Modifier.height(NexusSpacing.md))
        Text(
            text = stringResource(R.string.review_prompt),
            style = NexusTheme.typography.body,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(NexusSpacing.md))
        ReviewRunOutcomeRow(
            first = ReviewResult.PASS,
            second = ReviewResult.HARD,
            enabled = !isRecording,
            onRecord = onRecord,
        )
        Spacer(modifier = Modifier.height(NexusSpacing.xxs))
        ReviewRunOutcomeRow(
            first = ReviewResult.FAIL,
            second = ReviewResult.SKIP,
            enabled = !isRecording,
            onRecord = onRecord,
        )
    }
}

@Composable
private fun ReviewRunOutcomeRow(
    first: ReviewResult,
    second: ReviewResult,
    enabled: Boolean,
    onRecord: (ReviewResult) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
    ) {
        ReviewRunOutcomeButton(first, enabled, onRecord, Modifier.weight(1f))
        ReviewRunOutcomeButton(second, enabled, onRecord, Modifier.weight(1f))
    }
}

@Composable
private fun ReviewRunOutcomeButton(
    result: ReviewResult,
    enabled: Boolean,
    onRecord: (ReviewResult) -> Unit,
    modifier: Modifier,
) {
    val colors = NexusTheme.colors
    val foreground = result.tone().foregroundColor(colors)
    val label = stringResource(result.labelRes())
    Box(
        modifier = modifier
            .height(ReviewRunActionHeight)
            .background(colors.surface, NexusRadius.sm)
            .border(1.dp, foreground, NexusRadius.sm)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
            ) { onRecord(result) }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = NexusTheme.typography.data, color = foreground)
    }
}

@Composable
private fun ReviewRunResult(
    item: ReviewQueueItem?,
    outcome: ReviewOutcome,
    isComplete: Boolean,
    onNext: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = NexusTheme.colors
    NexusSection(
        label = if (isComplete) {
            stringResource(R.string.review_run_complete)
        } else {
            stringResource(R.string.review_next_scheduled, outcome.nextIntervalDays)
        },
    ) {
        item?.let {
            Text(
                text = it.problemTitle,
                style = NexusTheme.typography.title,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(NexusSpacing.xs))
        }
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
        ReviewRunActionButton(
            label = stringResource(
                if (isComplete) R.string.review_run_close else R.string.review_run_next_item,
            ),
            description = stringResource(
                if (isComplete) R.string.review_run_close_cd else R.string.review_run_next_cd,
            ),
            onClick = if (isComplete) onDone else onNext,
        )
    }
}

@Composable
private fun ReviewRunEmpty(onDone: () -> Unit) {
    NexusSection(label = stringResource(R.string.review_run_current)) {
        NexusStatus(
            label = stringResource(R.string.review_run_no_due),
            tone = NexusTone.Neutral,
        )
        Spacer(modifier = Modifier.height(NexusSpacing.md))
        ReviewRunActionButton(
            label = stringResource(R.string.review_run_close),
            description = stringResource(R.string.review_run_close_cd),
            onClick = onDone,
        )
    }
}

@Composable
private fun ReviewRunActionButton(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    val colors = NexusTheme.colors
    Box(
        modifier = Modifier
            .height(ReviewRunActionHeight)
            .background(colors.surface, NexusRadius.sm)
            .border(1.dp, colors.accent, NexusRadius.sm)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = NexusSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = NexusTheme.typography.data, color = colors.accent)
    }
}
