package com.ojnexus.feature.contests

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojnexus.R
import com.ojnexus.core.data.repository.ContestFocusProblem
import com.ojnexus.core.data.repository.ContestFocusSnapshot
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusMetric
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.domain.ContestTimeState
import com.ojnexus.core.domain.ContestTimeStateCalculator
import com.ojnexus.core.model.ContestMarker
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.UrlOpener
import com.ojnexus.core.ui.formatCountdown
import com.ojnexus.core.ui.formatDateTime
import com.ojnexus.core.ui.formatDuration
import com.ojnexus.judge.atcoder.AtCoderUrls
import com.ojnexus.judge.codeforces.CodeforcesUrls
import com.ojnexus.judge.luogu.LuoguUrls
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ContestFocusScreen(
    judge: String,
    contestId: String,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ContestFocusViewModel>(
        key = "contest-focus-$judge-$contestId",
        factory = ContainerViewModelFactory(container) {
            ContestFocusViewModel(
                judge = judge,
                contestId = contestId,
                repository = it.contestFocusRepository,
                clock = it.clock,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nowSeconds by produceState(viewModel.currentEpochSecond(), viewModel) {
        while (isActive) {
            delay(1_000)
            value = viewModel.currentEpochSecond()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        NexusTopBar(
            title = stringResource(R.string.contest_focus_title),
            trailing = {
                Text(
                    text = stringResource(R.string.action_back),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.accent,
                    modifier = Modifier.clickable(onClick = onBack),
                )
            },
        )
        when (val content = state) {
            Loadable.Loading -> Box(Modifier.fillMaxSize())
            is Loadable.Failed -> FocusMessage(stringResource(R.string.contest_focus_error))
            is Loadable.Ready -> ContestFocusContent(
                snapshot = content.value,
                nowSeconds = nowSeconds,
                onCycleMarker = viewModel::cycleMarker,
            )
        }
    }
}

@Composable
private fun ContestFocusContent(
    snapshot: ContestFocusSnapshot,
    nowSeconds: Long,
    onCycleMarker: (String) -> Unit,
) {
    val contest = snapshot.contest
    if (contest == null) {
        FocusMessage(stringResource(R.string.contest_focus_empty))
        return
    }
    val context = LocalContext.current
    val judge = JudgeId.fromId(contest.judge) ?: JudgeId.LOCAL
    val timeState = contest.startTimeSeconds?.let {
        ContestTimeStateCalculator.calculate(it, contest.durationSeconds, nowSeconds)
    } ?: ContestTimeState.ENDED
    val countdown = when (timeState) {
        ContestTimeState.UPCOMING -> contest.startTimeSeconds?.minus(nowSeconds)?.coerceAtLeast(0) ?: 0
        ContestTimeState.LIVE -> contest.startTimeSeconds?.plus(contest.durationSeconds)
            ?.minus(nowSeconds)?.coerceAtLeast(0) ?: 0
        ContestTimeState.ENDED -> 0
    }
    val solved = snapshot.problems.count { it.solved }
    val marked = snapshot.problems.count { it.marker != ContestMarker.NONE }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexusSpacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(NexusSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = judge.displayName,
                style = NexusTheme.typography.sectionLabel,
                color = NexusTheme.colors.textTertiary,
                modifier = Modifier.padding(end = NexusSpacing.xxs),
            )
            Text(
                text = contest.externalContestId,
                style = NexusTheme.typography.dataLarge,
                color = NexusTheme.colors.accent,
                modifier = Modifier.weight(1f),
            )
            NexusTag(
                text = stringResource(timeState.labelRes()),
                tone = timeState.tone(),
                selected = true,
            )
        }
        Text(
            text = contest.name,
            style = NexusTheme.typography.title,
            color = NexusTheme.colors.textPrimary,
            modifier = Modifier.padding(top = NexusSpacing.xxs),
        )
        Row(
            modifier = Modifier.padding(top = NexusSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xl),
        ) {
            NexusMetric(
                label = stringResource(R.string.contest_label_countdown),
                value = formatCountdown(countdown),
            )
            NexusMetric(
                label = stringResource(R.string.contest_focus_progress),
                value = stringResource(R.string.contest_focus_solved, solved, snapshot.problems.size),
            )
            NexusMetric(
                label = stringResource(R.string.contest_focus_marked_label),
                value = marked.toString(),
            )
        }
        Row(
            modifier = Modifier.padding(top = NexusSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
        ) {
            NexusTag(
                text = stringResource(R.string.contest_action_open),
                tone = NexusTone.Accent,
                selected = true,
                modifier = Modifier.clickable(role = Role.Button) {
                    UrlOpener.open(context, contestUrl(judge, contest.externalContestId))
                },
            )
            contest.startTimeSeconds?.let {
                Text(
                    text = formatDateTime(it * 1000),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }

        Spacer(modifier = Modifier.height(NexusSpacing.xl))
        NexusSection(
            label = stringResource(R.string.contest_focus_problems),
            trailing = {
                Text(
                    text = stringResource(R.string.contest_focus_problem_count, snapshot.problems.size),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.textTertiary,
                )
            },
        ) {
            if (snapshot.problems.isEmpty()) {
                Text(
                    text = stringResource(R.string.contest_focus_no_problems),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                    modifier = Modifier.padding(vertical = NexusSpacing.xs),
                )
            } else {
                snapshot.problems.forEachIndexed { index, problem ->
                    ContestFocusProblemRow(
                        problem = problem,
                        judge = judge,
                        contestId = contest.externalContestId,
                        context = context,
                        onCycleMarker = { onCycleMarker(problem.externalId) },
                    )
                    if (index != snapshot.problems.lastIndex) NexusDivider(insetEnd = NexusSpacing.xxs)
                }
            }
        }
        Spacer(modifier = Modifier.height(NexusSpacing.xxl))
    }
}

@Composable
private fun ContestFocusProblemRow(
    problem: ContestFocusProblem,
    judge: JudgeId,
    contestId: String,
    context: Context,
    onCycleMarker: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = NexusSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = problem.index ?: problem.externalId,
                style = NexusTheme.typography.dataLarge,
                color = NexusTheme.colors.accent,
                modifier = Modifier.padding(end = NexusSpacing.sm),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = problem.name,
                    style = NexusTheme.typography.data,
                    color = NexusTheme.colors.textPrimary,
                    maxLines = 2,
                )
                Text(
                    text = problem.externalId,
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                )
            }
            NexusTag(
                text = stringResource(problem.marker.labelRes()),
                tone = problem.marker.tone(),
                selected = problem.marker != ContestMarker.NONE,
                modifier = Modifier.clickable(role = Role.Button, onClick = onCycleMarker),
            )
        }
        Row(
            modifier = Modifier.padding(start = NexusSpacing.xl, top = NexusSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = problem.rating?.toString() ?: stringResource(R.string.problems_no_value),
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.contest_focus_attempts, problem.attemptCount),
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.textTertiary,
                modifier = Modifier.padding(start = NexusSpacing.sm),
            )
            problem.latestVerdict?.let {
                NexusTag(
                    text = it,
                    tone = if (problem.solved) NexusTone.Success else NexusTone.Danger,
                    modifier = Modifier.padding(start = NexusSpacing.xs),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.contest_focus_open_problem),
                style = NexusTheme.typography.sectionLabel,
                color = NexusTheme.colors.accent,
                modifier = Modifier.clickable(role = Role.Button) {
                    UrlOpener.open(context, problemUrl(judge, contestId, problem.externalId, problem.index))
                },
            )
        }
    }
}

@Composable
private fun FocusMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = NexusTheme.typography.data, color = NexusTheme.colors.textTertiary)
    }
}

private fun contestUrl(judge: JudgeId, contestId: String): String? = when (judge) {
    JudgeId.CODEFORCES -> contestId.toLongOrNull()?.let(CodeforcesUrls::contest)
    JudgeId.ATCODER -> AtCoderUrls.contest(contestId)
    JudgeId.LUOGU -> LuoguUrls.contest(contestId)
    else -> null
}

private fun problemUrl(judge: JudgeId, contestId: String, externalId: String, index: String?): String? = when (judge) {
    JudgeId.CODEFORCES -> {
        val contest = contestId.toLongOrNull()
        val problemIndex = index ?: externalId.substringAfterLast('_', "")
        if (contest == null || problemIndex.isBlank()) null else CodeforcesUrls.problem(contest, problemIndex)
    }
    JudgeId.ATCODER -> AtCoderUrls.problem(contestId, externalId)
    JudgeId.LUOGU -> LuoguUrls.problem(externalId)
    else -> null
}

private fun ContestTimeState.labelRes(): Int = when (this) {
    ContestTimeState.UPCOMING -> R.string.contest_status_upcoming
    ContestTimeState.LIVE -> R.string.contest_status_live
    ContestTimeState.ENDED -> R.string.contest_status_ended
}

private fun ContestTimeState.tone(): NexusTone = when (this) {
    ContestTimeState.UPCOMING -> NexusTone.Accent
    ContestTimeState.LIVE -> NexusTone.Danger
    ContestTimeState.ENDED -> NexusTone.Neutral
}

private fun ContestMarker.labelRes(): Int = when (this) {
    ContestMarker.NONE -> R.string.contest_marker_none
    ContestMarker.WORKING -> R.string.contest_marker_working
    ContestMarker.SOLVED -> R.string.contest_marker_solved
    ContestMarker.SKIPPED -> R.string.contest_marker_skipped
}

private fun ContestMarker.tone(): NexusTone = when (this) {
    ContestMarker.NONE -> NexusTone.Neutral
    ContestMarker.WORKING -> NexusTone.Accent
    ContestMarker.SOLVED -> NexusTone.Success
    ContestMarker.SKIPPED -> NexusTone.Warning
}
