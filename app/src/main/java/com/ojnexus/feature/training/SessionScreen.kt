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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojnexus.R
import kotlinx.coroutines.flow.StateFlow
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusMetric
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusStatus
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.model.SessionState
import com.ojnexus.core.model.TrainingSession
import com.ojnexus.core.model.TrainingType
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.formatDateTime
import com.ojnexus.core.ui.formatDuration
import com.ojnexus.core.ui.labelRes

/**
 * Session surface, one route for three states:
 *  - no live session → creation form (type / target / tag / problem picker)
 *  - RUNNING or PAUSED → timer view (elapsed time ticks only inside its own text)
 *  - FINISHED or CANCELLED → summary
 * [sessionId] == null tracks the active session; a specific id opens a history entry.
 */
@Composable
fun SessionScreen(
    sessionId: Long?,
    onDone: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<SessionViewModel>(
        key = "session-${sessionId ?: "active"}",
        factory = ContainerViewModelFactory(container) {
            SessionViewModel(
                sessionId = sessionId,
                trainingRepository = it.trainingRepository,
                problemRepository = it.problemRepository,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val problems by viewModel.problems.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        NexusTopBar(title = stringResource(R.string.session_running_title))
        when (val s = state) {
            Loadable.Loading -> Box(Modifier.fillMaxSize())
            is Loadable.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = s.message, style = NexusTheme.typography.data, color = NexusTheme.colors.danger)
            }
            is Loadable.Ready -> {
                val surface = s.value
                when {
                    surface.session == null -> SessionCreationForm(
                        problems = problems,
                        actionError = surface.actionError,
                        onCreate = { type, duration, tag, ids ->
                            viewModel.createSession(type, duration, tag, ids)
                        },
                    )
                    surface.session.state == SessionState.FINISHED ||
                        surface.session.state == SessionState.CANCELLED -> SessionSummaryView(
                        session = surface.session,
                        summary = surface.summary,
                        onDone = onDone,
                    )
                    else -> SessionRunningView(
                        session = surface.session,
                        liveProblemCount = surface.liveProblemCount,
                        actionError = surface.actionError,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCreationForm(
    problems: List<com.ojnexus.core.model.Problem>,
    actionError: SessionActionError?,
    onCreate: (TrainingType, Int?, String?, List<Long>) -> Unit,
) {
    val colors = NexusTheme.colors
    var type by rememberSaveable { mutableStateOf(TrainingType.PRACTICE) }
    var duration by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexusSpacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(NexusSpacing.md))
        NexusSection(label = stringResource(R.string.session_create_title)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, NexusRadius.md)
                    .padding(NexusSpacing.md),
            ) {
                Text(
                    text = stringResource(R.string.task_field_type),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xxs))
                Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxxs)) {
                    TrainingType.entries.forEach { t ->
                        NexusTag(
                            text = stringResource(t.labelRes()),
                            tone = NexusTone.Accent,
                            selected = type == t,
                            modifier = Modifier.clickable(role = Role.Button) { type = t },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                LabeledInput(stringResource(R.string.session_target_duration), duration) { duration = it }
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                LabeledInput(stringResource(R.string.session_target_tag), tag) { tag = it }
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                Text(
                    text = stringResource(R.string.session_pick_problems) +
                        " · " + stringResource(R.string.session_picked_count, selected.size),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xxxs))
                if (problems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.problems_empty_title),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textTertiary,
                    )
                }
                Column(
                    modifier = Modifier
                        .height(200.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    problems.forEach { problem ->
                        val picked = problem.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) {
                                    selected = if (picked) selected - problem.id else selected + problem.id
                                }
                                .padding(vertical = NexusSpacing.xxxs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProblemCheckSquare(picked)
                            Text(
                                text = "${problem.key.judge.displayName} ${problem.key.externalId} · ${problem.title}",
                                style = NexusTheme.typography.dataSmall,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                if (actionError != null) {
                    Text(
                        text = when (actionError) {
                            SessionActionError.ActiveExists -> stringResource(R.string.session_active_exists)
                            is SessionActionError.Generic -> actionError.message
                        },
                        style = NexusTheme.typography.dataSmall,
                        color = colors.danger,
                    )
                    Spacer(modifier = Modifier.height(NexusSpacing.xxs))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                    ActionButton(stringResource(R.string.session_start), accent = true) {
                        onCreate(type, duration.trim().toIntOrNull(), tag.trim(), selected.toList())
                    }
                    ActionButton(stringResource(R.string.action_cancel), accent = false) { }
                }
            }
        }
        Spacer(modifier = Modifier.height(NexusSpacing.xxl))
    }
}

@Composable
private fun SessionRunningView(
    session: TrainingSession,
    liveProblemCount: Int?,
    actionError: SessionActionError?,
    viewModel: SessionViewModel,
) {
    val colors = NexusTheme.colors
    val paused = session.pausedAt != null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexusSpacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(NexusSpacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NexusTag(
                text = stringResource(session.type.labelRes()),
                tone = NexusTone.Accent,
                selected = true,
            )
            if (paused) {
                NexusStatus(label = stringResource(R.string.session_paused_label), tone = NexusTone.Warning)
            } else {
                NexusStatus(label = stringResource(R.string.sync_state_syncing), tone = NexusTone.Accent)
            }
        }

        Spacer(modifier = Modifier.height(NexusSpacing.md))
        Text(
            text = stringResource(R.string.session_label_elapsed),
            style = NexusTheme.typography.sectionLabel,
            color = colors.textTertiary,
        )
        // The ONLY per-second recomposing element: elapsed text collects the ticker flow.
        ElapsedText(elapsedFlow = viewModel.elapsedMs, paused = paused)
        Spacer(modifier = Modifier.height(NexusSpacing.md))
        Row(modifier = Modifier.fillMaxWidth()) {
            NexusMetric(
                label = stringResource(R.string.session_label_target),
                value = session.targetDurationMin?.let { stringResource(R.string.session_value_minutes, it) }
                    ?: stringResource(R.string.session_summary_none),
                modifier = Modifier.weight(1f),
            )
            NexusMetric(
                label = stringResource(R.string.session_label_problems),
                value = (liveProblemCount ?: 0).toString(),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(NexusSpacing.md))
        if (actionError != null) {
            Text(
                text = when (actionError) {
                    SessionActionError.ActiveExists -> stringResource(R.string.session_active_exists)
                    is SessionActionError.Generic -> actionError.message
                },
                style = NexusTheme.typography.dataSmall,
                color = colors.danger,
            )
            Spacer(modifier = Modifier.height(NexusSpacing.xs))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
            if (paused) {
                ActionButton(stringResource(R.string.session_action_resume), accent = true) {
                    viewModel.resume(session.id)
                }
            } else {
                ActionButton(stringResource(R.string.session_action_pause), accent = true) {
                    viewModel.pause(session.id)
                }
            }
            ActionButton(stringResource(R.string.session_action_finish), accent = true) {
                viewModel.finish(session.id)
            }
            ActionButton(stringResource(R.string.session_action_cancel), accent = false, danger = true) {
                viewModel.cancel(session.id)
            }
        }
        Spacer(modifier = Modifier.height(NexusSpacing.xxl))
    }
}

@Composable
private fun SessionSummaryView(
    session: TrainingSession,
    summary: SessionSummary?,
    onDone: () -> Unit,
) {
    val colors = NexusTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexusSpacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(NexusSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            NexusTag(
                text = stringResource(session.type.labelRes()),
                tone = NexusTone.Accent,
                selected = true,
            )
            if (session.state == SessionState.CANCELLED) {
                NexusTag(
                    text = stringResource(R.string.session_cancelled_note),
                    tone = NexusTone.Warning,
                    modifier = Modifier.padding(start = NexusSpacing.xxs),
                )
            }
        }
        Spacer(modifier = Modifier.height(NexusSpacing.sm))
        NexusSection(label = stringResource(R.string.session_summary_title)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                NexusMetric(
                    label = stringResource(R.string.session_summary_duration),
                    value = summary?.let { formatDuration(it.durationMs / 60_000) }
                        ?: stringResource(R.string.session_summary_none),
                    modifier = Modifier.weight(1f),
                )
                NexusMetric(
                    label = stringResource(R.string.session_summary_solved),
                    value = (summary?.solvedCount ?: 0).toString(),
                    changeTone = NexusTone.Success,
                    modifier = Modifier.weight(1f),
                )
                NexusMetric(
                    label = stringResource(R.string.session_summary_attempts),
                    value = (summary?.attemptCount ?: 0).toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            Row(modifier = Modifier.fillMaxWidth()) {
                NexusMetric(
                    label = stringResource(R.string.session_summary_ac),
                    value = (summary?.acCount ?: 0).toString(),
                    changeTone = NexusTone.Success,
                    modifier = Modifier.weight(1f),
                )
                NexusMetric(
                    label = stringResource(R.string.session_summary_wa),
                    value = (summary?.waCount ?: 0).toString(),
                    changeTone = NexusTone.Danger,
                    modifier = Modifier.weight(1f),
                )
                NexusMetric(
                    label = stringResource(R.string.session_summary_avg),
                    value = summary?.averageDifficulty?.toString()
                        ?: stringResource(R.string.session_summary_none),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            NexusDivider()
            Spacer(modifier = Modifier.height(NexusSpacing.xxs))
            Row {
                Text(
                    text = stringResource(R.string.contest_label_start),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatDateTime(session.startedAt),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textSecondary,
                )
            }
            Row(modifier = Modifier.padding(top = NexusSpacing.xxxs)) {
                Text(
                    text = stringResource(R.string.session_summary_title),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = session.finishedAt?.let { formatDateTime(it) }
                        ?: stringResource(R.string.session_summary_none),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textSecondary,
                )
            }
        }

        if (summary != null && summary.problems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(NexusSpacing.md))
            NexusDivider()
            Spacer(modifier = Modifier.height(NexusSpacing.md))
            NexusSection(label = stringResource(R.string.session_label_problems)) {
                summary.problems.forEachIndexed { index, problem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(NexusSize.tableRowHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = problem.title,
                            style = NexusTheme.typography.data,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = problem.attempts.toString(),
                            style = NexusTheme.typography.dataSmall,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(end = NexusSpacing.sm),
                        )
                        NexusTag(
                            text = if (problem.solved) {
                                stringResource(R.string.verdict_ac)
                            } else {
                                stringResource(R.string.problems_filter_unsolved)
                            },
                            tone = if (problem.solved) NexusTone.Success else NexusTone.Neutral,
                        )
                    }
                    if (index != summary.problems.lastIndex) NexusDivider(insetEnd = NexusSpacing.xxs)
                }
            }
        }

        Spacer(modifier = Modifier.height(NexusSpacing.lg))
        ActionButton(stringResource(R.string.action_close), accent = true) { onDone() }
        Spacer(modifier = Modifier.height(NexusSpacing.xxl))
    }
}

@Composable
private fun ElapsedText(elapsedFlow: StateFlow<Long>, paused: Boolean) {
    val elapsedMs by elapsedFlow.collectAsStateWithLifecycle()
    Text(
        text = formatDuration(elapsedMs / 60_000),
        style = NexusTheme.typography.displayData,
        color = if (paused) NexusTheme.colors.warning else NexusTheme.colors.accent,
    )
}

@Composable
private fun ProblemCheckSquare(picked: Boolean) {
    val colors = NexusTheme.colors
    Box(
        modifier = Modifier
            .size(20.dp)
            .padding(4.dp)
            .background(if (picked) colors.accent else colors.surface, RoundedCornerShape(2.dp))
            .border(1.dp, if (picked) colors.accent else colors.borderStrong, RoundedCornerShape(2.dp)),
    )
}

@Composable
private fun LabeledInput(label: String, value: String, onValueChange: (String) -> Unit) {
    val colors = NexusTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = NexusTheme.typography.sectionLabel,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(NexusSpacing.xxxs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background, NexusRadius.sm)
                .border(1.dp, colors.border, NexusRadius.sm)
                .padding(horizontal = NexusSpacing.xs, vertical = NexusSpacing.xxxs),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                ),
                textStyle = NexusTheme.typography.dataSmall.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActionButton(label: String, accent: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val colors = NexusTheme.colors
    val foreground = when {
        danger -> colors.danger
        accent -> colors.accent
        else -> colors.textSecondary
    }
    Box(
        modifier = Modifier
            .background(colors.surface, NexusRadius.sm)
            .border(1.dp, foreground, NexusRadius.sm)
            .clickable(role = Role.Button) { onClick() }
            .padding(horizontal = NexusSpacing.md, vertical = NexusSpacing.xs),
    ) {
        Text(text = label, style = NexusTheme.typography.data, color = foreground)
    }
}
