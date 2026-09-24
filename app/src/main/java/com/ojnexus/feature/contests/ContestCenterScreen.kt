package com.ojnexus.feature.contests

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusMotion
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusMetric
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.formatDateTime
import com.ojnexus.core.ui.formatCountdown
import com.ojnexus.core.ui.formatCount
import com.ojnexus.core.ui.formatDuration
import com.ojnexus.core.domain.ContestTimeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ContestCenterScreen(
    onBack: () -> Unit,
    onOpenFocus: (judge: String, contestId: String) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ContestCenterViewModel>(
        factory = ContainerViewModelFactory(container) {
            ContestCenterViewModel(
                dataRepository = it.judgeDataRepository,
                clock = it.clock,
            )
        },
    )
    val envelope by viewModel.envelope.collectAsStateWithLifecycle()
    val nowSeconds by produceState(viewModel.currentEpochSecond(), viewModel) {
        while (isActive) {
            delay(1_000)
            value = viewModel.currentEpochSecond()
        }
    }
    val rows = viewModel.rows(envelope, nowSeconds)
    var phaseFilter by rememberSaveable { mutableStateOf(ContestPhaseFilter.ALL) }
    val summary = summarizeContestCenter(rows)
    val visibleRows = filterContestCenter(rows, phaseFilter)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        NexusTopBar(
            title = stringResource(R.string.contest_center_title),
            trailing = {
                Text(
                    text = stringResource(R.string.action_back),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.accent,
                    modifier = Modifier.clickable(onClick = onBack),
                )
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexusSpacing.screenHorizontal),
        ) {
            Spacer(modifier = Modifier.height(NexusSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                JudgeFilterTag(stringResource(R.string.problems_scope_filter_all), envelope.selectedJudge == null) {
                    viewModel.setJudgeFilter(null)
                }
                contestJudgeFilters().forEach { judge ->
                    JudgeFilterTag(judge.displayName, envelope.selectedJudge == judge) {
                        viewModel.setJudgeFilter(judge)
                    }
                }
            }
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            ContestPulse(summary = summary, onOpenFocus = onOpenFocus)
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            ContestPhaseControls(selected = phaseFilter, onSelect = { phaseFilter = it })
            Spacer(modifier = Modifier.height(NexusSpacing.xs))
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
                if (rows.upcoming.isEmpty() && rows.live.isEmpty() && rows.recent.isEmpty()) {
                    Text(
                        text = stringResource(R.string.contest_empty),
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.textTertiary,
                        modifier = Modifier.padding(vertical = NexusSpacing.xs),
                    )
                } else if (visibleRows.upcoming.isEmpty() && visibleRows.live.isEmpty() && visibleRows.recent.isEmpty()) {
                    Text(
                        text = stringResource(R.string.contest_phase_empty),
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.textTertiary,
                        modifier = Modifier.padding(vertical = NexusSpacing.xs),
                    )
                }
                if (visibleRows.live.isNotEmpty()) {
                    ContestGroup(
                        label = stringResource(R.string.contest_section_live),
                        rows = visibleRows.live,
                        tone = NexusTone.Danger,
                        onOpenFocus = onOpenFocus,
                    )
                }
                if (visibleRows.upcoming.isNotEmpty()) {
                    ContestGroup(
                        label = stringResource(R.string.contest_section_upcoming),
                        rows = visibleRows.upcoming,
                        tone = NexusTone.Accent,
                        onOpenFocus = onOpenFocus,
                    )
                }
                if (visibleRows.recent.isNotEmpty()) {
                    ContestGroup(
                        label = stringResource(R.string.contest_section_recent),
                        rows = visibleRows.recent,
                        tone = NexusTone.Neutral,
                        onOpenFocus = onOpenFocus,
                    )
                }
            }
            Spacer(modifier = Modifier.height(NexusSpacing.xxl))
        }
    }
}

@Composable
private fun ContestPulse(
    summary: ContestCenterSummary,
    onOpenFocus: (String, String) -> Unit,
) {
    val next = summary.nextUpcoming
    val actionLabel = if (next == null) {
        stringResource(R.string.contest_pulse_no_upcoming)
    } else {
        stringResource(R.string.contest_pulse_open_next)
    }
    val actionDescription = if (next == null) {
        stringResource(R.string.contest_pulse_no_upcoming_cd)
    } else {
        stringResource(R.string.contest_pulse_open_next_cd)
    }

    NexusSection(label = stringResource(R.string.contest_section_pulse)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
        ) {
            ContestPulseMetric(
                label = stringResource(R.string.contest_pulse_live),
                value = summary.live,
                modifier = Modifier.weight(1f),
            )
            ContestPulseMetric(
                label = stringResource(R.string.contest_pulse_upcoming),
                value = summary.upcoming,
                modifier = Modifier.weight(1f),
            )
            ContestPulseMetric(
                label = stringResource(R.string.contest_pulse_recent),
                value = summary.recent,
                modifier = Modifier.weight(1f),
            )
            NexusMetric(
                label = stringResource(R.string.contest_pulse_next),
                value = next?.let { formatCountdown(it.countdownSeconds) }
                    ?: stringResource(R.string.contest_pulse_no_upcoming),
                modifier = Modifier.weight(1.5f),
            )
        }
        Spacer(modifier = Modifier.height(NexusSpacing.sm))
        NexusTag(
            text = actionLabel,
            tone = if (next == null) NexusTone.Neutral else NexusTone.Accent,
            selected = next != null,
            modifier = Modifier
                .clickable(
                    enabled = next != null,
                    role = Role.Button,
                    onClickLabel = actionDescription,
                    onClick = { next?.let { onOpenFocus(it.judge.id, it.contestId) } },
                )
                .semantics { contentDescription = actionDescription },
        )
    }
}

@Composable
private fun ContestPulseMetric(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
) {
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = if (NexusTheme.reduceMotion) snap() else tween(
            NexusMotion.DURATION_NORMAL,
            easing = NexusMotion.EasingStandard,
        ),
        label = "contest pulse $label",
    )
    NexusMetric(label = label, value = formatCount(animatedValue), modifier = modifier)
}

@Composable
private fun ContestPhaseControls(
    selected: ContestPhaseFilter,
    onSelect: (ContestPhaseFilter) -> Unit,
) {
    val filters = listOf(
        ContestPhaseFilter.ALL to R.string.contest_filter_all,
        ContestPhaseFilter.LIVE to R.string.contest_filter_live,
        ContestPhaseFilter.UPCOMING to R.string.contest_filter_upcoming,
        ContestPhaseFilter.RECENT to R.string.contest_filter_recent,
    )
    val descriptions = mapOf(
        ContestPhaseFilter.ALL to R.string.contest_filter_all_cd,
        ContestPhaseFilter.LIVE to R.string.contest_filter_live_cd,
        ContestPhaseFilter.UPCOMING to R.string.contest_filter_upcoming_cd,
        ContestPhaseFilter.RECENT to R.string.contest_filter_recent_cd,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
    ) {
        filters.forEach { (filter, labelRes) ->
            val description = stringResource(descriptions.getValue(filter))
            NexusTag(
                text = stringResource(labelRes),
                tone = if (filter == selected) NexusTone.Accent else NexusTone.Neutral,
                selected = filter == selected,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = description,
                        onClick = { onSelect(filter) },
                    )
                    .semantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun ContestGroup(
    label: String,
    rows: List<ContestRow>,
    tone: NexusTone,
    onOpenFocus: (String, String) -> Unit,
) {
    NexusSection(label = label) {
        rows.forEachIndexed { index, row ->
            ContestRowView(row, tone, onOpenFocus)
            if (index != rows.lastIndex) NexusDivider(insetEnd = NexusSpacing.xxs)
        }
    }
    Spacer(modifier = Modifier.height(NexusSpacing.md))
}

@Composable
private fun ContestRowView(
    row: ContestRow,
    tone: NexusTone,
    onOpenFocus: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenFocus(row.judge.id, row.contestId) }
            .padding(vertical = NexusSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.judge.displayName,
                style = NexusTheme.typography.sectionLabel,
                color = NexusTheme.colors.textTertiary,
                modifier = Modifier.padding(end = NexusSpacing.xxs),
            )
            Text(
                text = row.name,
                style = NexusTheme.typography.data,
                color = NexusTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            NexusTag(
                text = stringResource(row.phase.labelRes()),
                tone = tone,
                selected = true,
                modifier = Modifier.padding(start = NexusSpacing.xxs),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.startTimeSeconds?.let { formatDateTime(it * 1000) }
                    ?: stringResource(R.string.problems_no_value),
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDuration(row.durationSeconds / 60),
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.textTertiary,
            )
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = NexusSpacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.contest_label_countdown),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.textTertiary,
                )
                Text(
                    text = formatCountdown(row.countdownSeconds),
                    style = NexusTheme.typography.dataSmall,
                    color = if (row.phase == ContestTimeState.ENDED) {
                        NexusTheme.colors.textTertiary
                    } else {
                        NexusTheme.colors.accent
                    },
                )
            }
        }
    }
}

@Composable
private fun JudgeFilterTag(label: String, selected: Boolean, onClick: () -> Unit) {
    NexusTag(
        text = label,
        tone = if (selected) NexusTone.Accent else NexusTone.Neutral,
        selected = selected,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

private fun ContestTimeState.labelRes(): Int = when (this) {
    ContestTimeState.UPCOMING -> com.ojnexus.R.string.contest_status_upcoming
    ContestTimeState.LIVE -> com.ojnexus.R.string.contest_status_live
    ContestTimeState.ENDED -> com.ojnexus.R.string.contest_status_ended
}
