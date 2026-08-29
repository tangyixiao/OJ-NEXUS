package com.ojnexus.feature.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusStatus
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.model.KnowledgeArea
import com.ojnexus.core.model.TrainingType
import com.ojnexus.core.sample.SampleData
import com.ojnexus.core.ui.formatPercent

// Training screen layout metrics.
private val MasteryTrackHeight = 6.dp
private val MasteryTrackAlpha = 0.35f

@Composable
fun TrainingScreen(state: TrainingUiState = SampleData.training) {
    val colors = NexusTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        NexusTopBar(title = stringResource(R.string.nav_training))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexusSpacing.screenHorizontal),
        ) {
            Spacer(modifier = Modifier.height(NexusSpacing.md))
            SessionSection(state)
            SectionGap()
            TargetSection(state.targets)
            SectionGap()
            WeakAreaSection(state.weakAreas)
            Spacer(modifier = Modifier.height(NexusSpacing.xxl))
        }
    }
}

@Composable
private fun SectionGap() {
    Spacer(modifier = Modifier.height(NexusSpacing.md))
    NexusDivider()
    Spacer(modifier = Modifier.height(NexusSpacing.md))
}

@Composable
private fun SessionSection(state: TrainingUiState) {
    val colors = NexusTheme.colors
    NexusSection(
        label = stringResource(R.string.training_section_session),
        trailing = {
            NexusStatus(
                label = stringResource(R.string.session_state_standby),
                tone = NexusTone.Neutral,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, NexusRadius.md)
                .padding(NexusSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.session_no_active),
                style = NexusTheme.typography.title,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                TrainingType.entries.forEach { type ->
                    NexusTag(
                        text = trainingTypeLabel(type),
                        tone = NexusTone.Accent,
                        selected = type == state.selectedType,
                    )
                }
            }
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.session_label_duration),
                        style = NexusTheme.typography.sectionLabel,
                        color = colors.textTertiary,
                    )
                    Text(
                        text = stringResource(R.string.session_value_minutes, state.durationMinutes),
                        style = NexusTheme.typography.data,
                        color = colors.textSecondary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.session_label_problems),
                        style = NexusTheme.typography.sectionLabel,
                        color = colors.textTertiary,
                    )
                    Text(
                        text = state.targetProblemCount.toString(),
                        style = NexusTheme.typography.data,
                        color = colors.textSecondary,
                    )
                }
                NexusTag(
                    text = stringResource(R.string.session_action_start),
                    tone = NexusTone.Accent,
                    selected = true,
                )
            }
        }
    }
}

@Composable
private fun trainingTypeLabel(type: TrainingType): String = when (type) {
    TrainingType.PRACTICE -> stringResource(R.string.session_type_practice)
    TrainingType.FOCUS -> stringResource(R.string.session_type_focus)
    TrainingType.UPSOLVE -> stringResource(R.string.session_type_upsolve)
    TrainingType.REVIEW -> stringResource(R.string.session_type_review)
}

@Composable
private fun TargetSection(targets: List<TargetItemUi>) {
    NexusSection(label = stringResource(R.string.training_section_target)) {
        targets.forEachIndexed { index, target ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = NexusSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.target_priority, target.priority),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.accent,
                    modifier = Modifier.width(PriorityColumnWidth),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.code,
                        style = NexusTheme.typography.data,
                        color = NexusTheme.colors.textPrimary,
                    )
                    Text(
                        text = targetReasonText(target),
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.textTertiary,
                        maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.target_range_label),
                        style = NexusTheme.typography.sectionLabel,
                        color = NexusTheme.colors.textTertiary,
                    )
                    Text(
                        text = target.targetRange,
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.textSecondary,
                    )
                }
            }
            if (index != targets.lastIndex) {
                NexusDivider(insetEnd = NexusSpacing.xxs)
            }
        }
    }
}

/** Priority column for target rows. */
private val PriorityColumnWidth = 84.dp

@Composable
private fun targetReasonText(target: TargetItemUi): String {
    val parts = target.reasons.map { reasonLabel(it) }.toMutableList()
    target.reviewGapDays?.let { gap ->
        parts.add(stringResource(R.string.format_review_gap, gap))
    }
    return parts.joinToString(separator = stringResource(R.string.reason_separator))
}

@Composable
private fun reasonLabel(reason: TrainingReasonUi): String = when (reason) {
    TrainingReasonUi.WEAK_MASTERY -> stringResource(R.string.reason_weak_mastery)
    TrainingReasonUi.REVIEW_GAP -> stringResource(R.string.reason_review_gap)
    TrainingReasonUi.NO_ATTEMPTS -> stringResource(R.string.reason_no_attempts)
}

@Composable
private fun WeakAreaSection(areas: List<AreaMasteryUi>) {
    val colors = NexusTheme.colors
    NexusSection(label = stringResource(R.string.training_section_weak)) {
        areas.forEachIndexed { index, area ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = NexusSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = areaLabel(area.area),
                    style = NexusTheme.typography.body,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(MasteryTrackHeight)
                        .background(colors.surfaceElevated, NexusRadius.xs),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(area.masteryPercent / 100f)
                            .height(MasteryTrackHeight)
                            .background(
                                colors.accent.copy(alpha = MasteryTrackAlpha),
                                NexusRadius.xs,
                            ),
                    )
                }
                Text(
                    text = formatPercent(area.masteryPercent),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.padding(start = NexusSpacing.sm),
                )
            }
            if (index != areas.lastIndex) {
                NexusDivider(insetEnd = NexusSpacing.xxs)
            }
        }
    }
}

@Composable
private fun areaLabel(area: KnowledgeArea): String = when (area) {
    KnowledgeArea.DATA_STRUCTURE -> stringResource(R.string.area_data_structure)
    KnowledgeArea.GRAPH -> stringResource(R.string.area_graph)
    KnowledgeArea.DYNAMIC_PROGRAMMING -> stringResource(R.string.area_dp)
    KnowledgeArea.STRING -> stringResource(R.string.area_string)
    KnowledgeArea.MATH -> stringResource(R.string.area_math)
    KnowledgeArea.GEOMETRY -> stringResource(R.string.area_geometry)
    KnowledgeArea.GREEDY -> stringResource(R.string.area_greedy)
    KnowledgeArea.SEARCH -> stringResource(R.string.area_search)
    KnowledgeArea.CONSTRUCTION -> stringResource(R.string.area_construction)
    KnowledgeArea.GAME_THEORY -> stringResource(R.string.area_game_theory)
}

@Preview(name = "Training")
@Composable
private fun TrainingPreview() {
    NexusTheme {
        TrainingScreen(state = SampleData.training)
    }
}
