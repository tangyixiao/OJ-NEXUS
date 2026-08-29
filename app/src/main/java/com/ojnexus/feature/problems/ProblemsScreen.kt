package com.ojnexus.feature.problems

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.sample.SampleData
import com.ojnexus.core.ui.formatPercent

// Problem table column widths.
private val RatingColumnWidth = 64.dp
private val StatusColumnWidth = 100.dp
private val MasteryColumnWidth = 64.dp

@Composable
fun ProblemsScreen(state: ProblemsUiState = SampleData.problems) {
    val colors = NexusTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        NexusTopBar(
            title = stringResource(R.string.nav_problems),
            trailing = {
                Text(
                    text = stringResource(R.string.problems_count, state.totalCount),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
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
            FilterRow()
            Spacer(modifier = Modifier.height(NexusSpacing.md))
            NexusSection(label = stringResource(R.string.problems_section_library)) {
                TableHeader()
                NexusDivider()
                state.rows.forEachIndexed { index, row ->
                    ProblemRow(row)
                    if (index != state.rows.lastIndex) {
                        NexusDivider(insetEnd = NexusSpacing.xxs)
                    }
                }
            }
            Spacer(modifier = Modifier.height(NexusSpacing.lg))
            Text(
                text = stringResource(R.string.problems_footer_sample),
                style = NexusTheme.typography.sectionLabel,
                color = NexusTheme.colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(NexusSpacing.xxl))
        }
    }
}

/** Filters are display-only in Phase 0; the query engine wires them up in a later phase. */
@Composable
private fun FilterRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
    ) {
        NexusTag(
            text = stringResource(R.string.problems_filter_all),
            selected = true,
        )
        NexusTag(text = stringResource(R.string.problems_filter_unsolved))
        NexusTag(text = JudgeId.CODEFORCES.displayName)
        NexusTag(text = stringResource(R.string.problems_filter_difficulty))
        NexusTag(text = stringResource(R.string.problems_filter_tag_dp))
    }
}

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NexusSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.problems_header_problem),
            style = NexusTheme.typography.sectionLabel,
            color = NexusTheme.colors.textTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.problems_header_rating),
            style = NexusTheme.typography.sectionLabel,
            color = NexusTheme.colors.textTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(RatingColumnWidth),
        )
        Text(
            text = stringResource(R.string.problems_header_status),
            style = NexusTheme.typography.sectionLabel,
            color = NexusTheme.colors.textTertiary,
            modifier = Modifier.width(StatusColumnWidth),
        )
        Text(
            text = stringResource(R.string.problems_header_mastery),
            style = NexusTheme.typography.sectionLabel,
            color = NexusTheme.colors.textTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(MasteryColumnWidth),
        )
    }
}

@Composable
private fun ProblemRow(row: ProblemRowUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(NexusSize.tableRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
                ProblemTitleCell(row, Modifier.weight(1f))
        Text(
            text = row.rating?.toString() ?: stringResource(R.string.problems_no_value),
            style = NexusTheme.typography.dataSmall,
            color = NexusTheme.colors.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(RatingColumnWidth),
        )
        NexusTag(
            text = statusLabel(row.status),
            tone = statusTone(row.status),
            modifier = Modifier.width(StatusColumnWidth),
        )
        Text(
            text = row.masteryPercent?.let { stringResource(R.string.format_mastery, it) }
                ?: stringResource(R.string.problems_no_value),
            style = NexusTheme.typography.dataSmall,
            color = NexusTheme.colors.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(MasteryColumnWidth),
        )
    }
}

@Composable
private fun ProblemTitleCell(row: ProblemRowUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.judge.displayName,
                style = NexusTheme.typography.sectionLabel,
                color = NexusTheme.colors.textTertiary,
                modifier = Modifier.padding(end = NexusSpacing.xxs),
            )
            Text(
                text = row.code,
                style = NexusTheme.typography.data,
                color = NexusTheme.colors.accent,
            )
        }
        Text(
            text = row.title,
            style = NexusTheme.typography.label,
            color = NexusTheme.colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun statusLabel(status: ProblemStatusUi): String = when (status) {
    ProblemStatusUi.SOLVED -> stringResource(R.string.problem_status_solved)
    ProblemStatusUi.ATTEMPTED -> stringResource(R.string.problem_status_attempted)
    ProblemStatusUi.UNSOLVED -> stringResource(R.string.problem_status_unsolved)
}

private fun statusTone(status: ProblemStatusUi): NexusTone = when (status) {
    ProblemStatusUi.SOLVED -> NexusTone.Success
    ProblemStatusUi.ATTEMPTED -> NexusTone.Warning
    ProblemStatusUi.UNSOLVED -> NexusTone.Neutral
}

@Preview(name = "Problems")
@Composable
private fun ProblemsPreview() {
    NexusTheme {
        ProblemsScreen(state = SampleData.problems)
    }
}
