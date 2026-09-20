package com.ojnexus.feature.problems

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusMotion
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.foregroundColor
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.NoteField
import com.ojnexus.core.model.ProblemNoteEntry
import com.ojnexus.core.model.textFor
import com.ojnexus.core.ui.labelRes
import com.ojnexus.core.ui.tone

// Note index layout metrics.
private val NoteIndexRowHeight = 76.dp
private val NoteStatusRailWidth = 3.dp

/**
 * Read-only projection of the local note index. Every entry already carries real saved text;
 * the screen only filters and renders it.
 */
data class NoteIndexUiState(
    val entries: List<ProblemNoteEntry>,
    val visibleEntries: List<ProblemNoteEntry>,
    val filter: ProblemNoteFilter,
    val summary: ProblemNoteIndexSummary,
) {
    /** Only judges that actually carry notes get a chip, so the row never shows a dead filter. */
    val judges: List<JudgeId>
        get() = entries.map { it.key.judge }.distinct().sortedBy { it.ordinal }
}

@Composable
internal fun NoteIndexContent(
    state: NoteIndexUiState,
    onQueryChange: (String) -> Unit,
    onFieldChange: (NoteField) -> Unit,
    onJudgeChange: (JudgeId?) -> Unit,
    onToggleUnsolved: () -> Unit,
    onClearFilters: () -> Unit,
    onOpenProblem: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenRemote: () -> Unit,
) {
    val colors = NexusTheme.colors
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "notes-header") {
            Column(modifier = Modifier.padding(horizontal = NexusSpacing.screenHorizontal)) {
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                ScopeSwitcher(
                    selected = ProblemScope.NOTES,
                    onSelectLibrary = onOpenLibrary,
                    onSelectRemote = onOpenRemote,
                    onSelectNotes = {},
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xs))
                SearchField(
                    query = state.filter.query,
                    onQueryChange = onQueryChange,
                    hintText = stringResource(R.string.problems_notes_search_hint),
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xs))
                NoteIndexPulse(
                    summary = state.summary,
                    showClear = !state.filter.isDefault,
                    onClear = onClearFilters,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xs))
                NoteFilterChipRow(
                    filter = state.filter,
                    judges = state.judges,
                    onFieldChange = onFieldChange,
                    onJudgeChange = onJudgeChange,
                    onToggleUnsolved = onToggleUnsolved,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                Text(
                    text = stringResource(R.string.problems_scope_notes),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xxs))
                NexusDivider()
            }
        }
        if (state.summary.indexed == 0) {
            item(key = "notes-empty") {
                EmptyHint(
                    title = stringResource(R.string.problems_notes_empty_title),
                    hint = stringResource(R.string.problems_notes_empty_hint),
                )
            }
        } else if (state.visibleEntries.isEmpty()) {
            item(key = "notes-no-match") {
                EmptyHint(
                    title = stringResource(R.string.problems_notes_no_match),
                    hint = "",
                )
            }
        } else {
            items(items = state.visibleEntries, key = { it.problemId }) { entry ->
                NoteIndexRow(
                    entry = entry,
                    field = state.filter.field,
                    onClick = { onOpenProblem(entry.problemId) },
                )
                NexusDivider(insetEnd = NexusSpacing.xxs)
            }
        }
        item(key = "notes-footer-space") {
            Spacer(modifier = Modifier.height(NexusSpacing.xxl))
        }
    }
}

@Composable
private fun NoteIndexPulse(
    summary: ProblemNoteIndexSummary,
    showClear: Boolean,
    onClear: () -> Unit,
) {
    val colors = NexusTheme.colors
    val clearDescription = stringResource(R.string.problems_notes_clear_filters_cd)
    NexusSection(
        label = stringResource(R.string.problems_notes_section_pulse),
        trailing = if (showClear) {
            {
                Text(
                    text = stringResource(R.string.problems_notes_clear_filters),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.accent,
                    modifier = Modifier
                        .clickable(
                            role = Role.Button,
                            onClickLabel = clearDescription,
                            onClick = onClear,
                        )
                        .semantics { contentDescription = clearDescription },
                )
            }
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = if (NexusTheme.reduceMotion) {
                        snap()
                    } else {
                        tween(NexusMotion.DURATION_NORMAL, easing = NexusMotion.EasingStandard)
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
        ) {
            PulseMetric(
                label = stringResource(R.string.problems_notes_pulse_indexed),
                value = summary.indexed,
                modifier = Modifier.weight(1f),
            )
            PulseMetric(
                label = stringResource(R.string.problems_notes_pulse_visible),
                value = summary.visible,
                modifier = Modifier.weight(1f),
            )
            PulseMetric(
                label = stringResource(R.string.problems_notes_pulse_unsolved),
                value = summary.unsolved,
                modifier = Modifier.weight(1f),
            )
            PulseMetric(
                label = stringResource(R.string.problems_notes_pulse_review),
                value = summary.reviewed,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NoteFilterChipRow(
    filter: ProblemNoteFilter,
    judges: List<JudgeId>,
    onFieldChange: (NoteField) -> Unit,
    onJudgeChange: (JudgeId?) -> Unit,
    onToggleUnsolved: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
        ) {
            NoteField.entries.forEach { field ->
                FilterChip(
                    label = stringResource(field.labelRes()),
                    selected = filter.field == field,
                    onClick = { onFieldChange(field) },
                )
            }
        }
        Spacer(modifier = Modifier.height(NexusSpacing.xxs))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
        ) {
            FilterChip(
                label = stringResource(R.string.problems_filter_all),
                selected = filter.judge == null,
                onClick = { onJudgeChange(null) },
            )
            judges.forEach { judge ->
                FilterChip(
                    label = judge.displayName,
                    selected = filter.judge == judge,
                    onClick = { onJudgeChange(if (filter.judge == judge) null else judge) },
                )
            }
            FilterChip(
                label = stringResource(R.string.problems_notes_filter_unsolved),
                selected = filter.unsolvedOnly,
                onClick = onToggleUnsolved,
            )
        }
    }
}

/**
 * One indexed note. Status is always spelled out as text — the rail colour only reinforces it.
 */
@Composable
internal fun NoteIndexRow(
    entry: ProblemNoteEntry,
    field: NoteField,
    onClick: () -> Unit,
) {
    val colors = NexusTheme.colors
    val preview = entry.notes.textFor(field)
    val fieldLabel = stringResource(field.labelRes())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(NoteIndexRowHeight)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(NoteStatusRailWidth)
                .height(NoteIndexRowHeight)
                .background(entry.status.tone().foregroundColor(colors)),
        )
        Spacer(modifier = Modifier.width(NexusSpacing.xs))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.key.judge.displayName,
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(end = NexusSpacing.xxs),
                )
                Text(
                    text = entry.key.externalId,
                    style = NexusTheme.typography.data,
                    color = colors.accent,
                )
                Spacer(modifier = Modifier.width(NexusSpacing.xs))
                NexusTag(
                    text = stringResource(entry.status.labelRes()),
                    tone = entry.status.tone(),
                )
            }
            Text(
                text = entry.title,
                style = NexusTheme.typography.label,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preview?.let {
                    stringResource(R.string.problems_notes_preview, fieldLabel, it)
                } ?: stringResource(R.string.problems_notes_field_empty),
                style = NexusTheme.typography.dataSmall,
                color = if (preview == null) colors.textTertiary else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(NexusSpacing.xs))
    }
}
