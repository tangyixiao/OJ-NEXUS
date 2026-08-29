package com.ojnexus.feature.contests

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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.formatDateTime
import com.ojnexus.core.ui.formatCountdown
import com.ojnexus.core.ui.formatDuration
import com.ojnexus.judge.codeforces.mapper.ContestPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ContestCenterScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ContestCenterViewModel>(
        factory = ContainerViewModelFactory(container) {
            ContestCenterViewModel(
                accountRepository = it.judgeAccountRepository,
                syncRepository = it.codeforcesSyncRepository,
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
            if (rows.upcoming.isEmpty() && rows.live.isEmpty() && rows.recent.isEmpty()) {
                Text(
                    text = stringResource(R.string.contest_empty),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                    modifier = Modifier.padding(vertical = NexusSpacing.xs),
                )
            }
            if (rows.live.isNotEmpty()) {
                ContestGroup(stringResource(R.string.contest_section_live), rows.live, tone = NexusTone.Danger)
            }
            if (rows.upcoming.isNotEmpty()) {
                ContestGroup(stringResource(R.string.contest_section_upcoming), rows.upcoming, tone = NexusTone.Accent)
            }
            if (rows.recent.isNotEmpty()) {
                ContestGroup(stringResource(R.string.contest_section_recent), rows.recent, tone = NexusTone.Neutral)
            }
            Spacer(modifier = Modifier.height(NexusSpacing.xxl))
        }
    }
}

@Composable
private fun ContestGroup(label: String, rows: List<ContestRow>, tone: NexusTone) {
    NexusSection(label = label) {
        rows.forEachIndexed { index, row ->
            ContestRowView(row, tone)
            if (index != rows.lastIndex) NexusDivider(insetEnd = NexusSpacing.xxs)
        }
    }
    Spacer(modifier = Modifier.height(NexusSpacing.md))
}

@Composable
private fun ContestRowView(row: ContestRow, tone: NexusTone) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                row.contestId.toLongOrNull()
                    ?.let(com.ojnexus.judge.codeforces.CodeforcesUrls::contest)
                    ?.let { url -> com.ojnexus.core.ui.UrlOpener.open(context, url) }
            }
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
                    color = if (row.phase == ContestPhase.ENDED) {
                        NexusTheme.colors.textTertiary
                    } else {
                        NexusTheme.colors.accent
                    },
                )
            }
        }
    }
}

private fun ContestPhase.labelRes(): Int = when (this) {
    ContestPhase.UPCOMING -> com.ojnexus.R.string.contest_status_upcoming
    ContestPhase.LIVE -> com.ojnexus.R.string.contest_status_live
    ContestPhase.ENDED -> com.ojnexus.R.string.contest_status_ended
}
