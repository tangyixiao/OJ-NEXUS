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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojnexus.R
import kotlinx.coroutines.flow.StateFlow
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusMotion
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
import com.ojnexus.core.designsystem.component.foregroundColor
import com.ojnexus.core.model.SessionProblem
import com.ojnexus.core.model.SessionState
import com.ojnexus.core.model.TrainingSession
import com.ojnexus.core.model.TrainingType
import com.ojnexus.core.model.Verdict
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.formatDateTime
import com.ojnexus.core.ui.formatDuration
import com.ojnexus.core.ui.labelRes
import com.ojnexus.core.ui.tone

private val SessionStatusRailWidth = 3.dp
private val SessionProgressRailHeight = 4.dp
private val SessionOpenHeight = 48.dp

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
    onOpenProblem: (Long) -> Unit,
    onOpenReview: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<SessionViewModel>(
        key = "session-${sessionId ?: "active"}",
        factory = ContainerViewModelFactory(container) {
            SessionViewModel(
                sessionId = sessionId,
                trainingRepository = it.trainingRepository,
                problemRepository = it.problemRepository,
                reviewRepository = it.reviewRepository,
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
                        problems = surface.problems,
                        onDone = onDone,
                        onOpenProblem = onOpenProblem,
                        onOpenReview = onOpenReview,
                        onScheduleReviews = { ids -> viewModel.scheduleReviews(ids) },
                        actionError = surface.actionError,
                    )
                    else -> SessionRunningView(
                        session = surface.session,
                        liveProblemCount = surface.liveProblemCount,
                        problems = surface.problems,
                        actionError = surface.actionError,
                        viewModel = viewModel,
                        onOpenProblem = onOpenProblem,
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
    problems: List<SessionProblem>,
    actionError: SessionActionError?,
    viewModel: SessionViewModel,
    onOpenProblem: (Long) -> Unit,
) {
    val colors = NexusTheme.colors
    val paused = session.pausedAt != null
    var selectedProblemId by rememberSaveable { mutableStateOf<Long?>(null) }
    val lastLoggedProblemId by viewModel.lastLoggedProblemId.collectAsStateWithLifecycle()
    val lastLoggedSequence by viewModel.lastLoggedSequence.collectAsStateWithLifecycle()
    val normalizedSelection = normalizeSessionSelection(selectedProblemId, problems)
    val selectedProblem = problems.firstOrNull { it.problemId == normalizedSelection }
    LaunchedEffect(problems, selectedProblemId) {
        if (selectedProblemId != normalizedSelection) {
            selectedProblemId = normalizedSelection
        }
    }
    LaunchedEffect(lastLoggedSequence, lastLoggedProblemId) {
        if (lastLoggedSequence > 0L && lastLoggedProblemId != null) {
            val next = problems.firstOrNull {
                !it.solved && it.problemId != lastLoggedProblemId
            } ?: problems.firstOrNull { !it.solved }
            selectedProblemId = next?.problemId ?: lastLoggedProblemId
        }
    }
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
        SessionProgressBoard(
            problems = problems,
            selectedProblemId = normalizedSelection,
            onSelectProblem = { selectedProblemId = it },
            onOpenProblem = onOpenProblem,
        )

        Spacer(modifier = Modifier.height(NexusSpacing.md))
        SessionMomentumRail(
            session = session,
            problems = problems,
            selectedProblemId = normalizedSelection,
            elapsedFlow = viewModel.elapsedMs,
            onOpenNext = { problemId ->
                selectedProblemId = problemId
                onOpenProblem(problemId)
            },
        )

        selectedProblem?.let { problem ->
            Spacer(modifier = Modifier.height(NexusSpacing.md))
            SessionQuickActions(
                selectedProblem = problem,
                onLogResult = { verdict -> viewModel.logAttempt(problem.problemId, verdict) },
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
    problems: List<SessionProblem>,
    onDone: () -> Unit,
    onOpenProblem: (Long) -> Unit,
    onOpenReview: (Long) -> Unit,
    onScheduleReviews: (List<Long>) -> Unit,
    actionError: SessionActionError?,
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
        SessionDebriefPanel(
            problems = problems,
            onOpenProblem = onOpenProblem,
            onOpenReview = onOpenReview,
            onScheduleReviews = onScheduleReviews,
        )

        Spacer(modifier = Modifier.height(NexusSpacing.lg))
        ActionButton(stringResource(R.string.action_close), accent = true) { onDone() }
        Spacer(modifier = Modifier.height(NexusSpacing.xxl))
    }
}

@Composable
private fun SessionDebriefPanel(
    problems: List<SessionProblem>,
    onOpenProblem: (Long) -> Unit,
    onOpenReview: (Long) -> Unit,
    onScheduleReviews: (List<Long>) -> Unit,
) {
    val colors = NexusTheme.colors
    val reduceMotion = NexusTheme.reduceMotion
    var selectedLane by rememberSaveable { mutableStateOf<SessionDebriefLane?>(null) }
    val pulse = deriveSessionDebriefPulse(problems)
    val visibleProblems = filterSessionDebrief(problems, selectedLane)
    val reviewCandidates = sessionReviewCandidates(problems)
    val queuedReviewCount = problems.count { it.inReview }

    NexusSection(label = stringResource(R.string.session_debrief_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxxs),
        ) {
            DebriefFilter(
                label = stringResource(R.string.session_debrief_all),
                selected = selectedLane == null,
                onClick = { selectedLane = null },
            )
            DebriefFilter(
                label = stringResource(R.string.session_debrief_solved),
                selected = selectedLane == SessionDebriefLane.SOLVED,
                onClick = { selectedLane = SessionDebriefLane.SOLVED },
            )
            DebriefFilter(
                label = stringResource(R.string.session_debrief_attention),
                selected = selectedLane == SessionDebriefLane.ATTENTION,
                onClick = { selectedLane = SessionDebriefLane.ATTENTION },
            )
            DebriefFilter(
                label = stringResource(R.string.session_debrief_pending),
                selected = selectedLane == SessionDebriefLane.PENDING,
                onClick = { selectedLane = SessionDebriefLane.PENDING },
            )
        }
        if (reviewCandidates.isNotEmpty() || queuedReviewCount > 0) {
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (reviewCandidates.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.session_debrief_review_candidates,
                            reviewCandidates.size,
                        ),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    ActionButton(
                        label = stringResource(R.string.session_debrief_schedule_attention),
                        accent = true,
                        description = stringResource(
                            R.string.session_debrief_schedule_attention_cd,
                            reviewCandidates.size,
                        ),
                    ) {
                        onScheduleReviews(reviewCandidates.map { it.problemId })
                    }
                } else {
                    NexusStatus(
                        label = stringResource(R.string.session_debrief_review_ready),
                        tone = NexusTone.Accent,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(NexusSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
        ) {
            NexusMetric(
                label = stringResource(R.string.session_debrief_solved),
                value = pulse.solved.toString(),
                changeTone = NexusTone.Success,
                modifier = Modifier.weight(1f),
            )
            NexusMetric(
                label = stringResource(R.string.session_debrief_attention),
                value = pulse.attention.toString(),
                changeTone = NexusTone.Warning,
                modifier = Modifier.weight(1f),
            )
            NexusMetric(
                label = stringResource(R.string.session_debrief_pending),
                value = pulse.pending.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(NexusSpacing.sm))
        Column(
            modifier = Modifier.animateContentSize(
                animationSpec = if (reduceMotion) snap() else tween(
                    NexusMotion.DURATION_NORMAL,
                    easing = NexusMotion.EasingStandard,
                ),
            ),
        ) {
            if (visibleProblems.isEmpty()) {
                Text(
                    text = stringResource(R.string.session_debrief_empty),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                )
            } else {
                visibleProblems.forEachIndexed { index, problem ->
                    SessionDebriefRow(
                        problem = problem,
                        onOpenProblem = onOpenProblem,
                        onOpenReview = onOpenReview,
                    )
                    if (index != visibleProblems.lastIndex) {
                        NexusDivider(insetEnd = NexusSpacing.xxs)
                    }
                }
            }
        }
    }
}

@Composable
private fun DebriefFilter(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NexusTag(
        text = label,
        tone = NexusTone.Accent,
        selected = selected,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun SessionDebriefRow(
    problem: SessionProblem,
    onOpenProblem: (Long) -> Unit,
    onOpenReview: (Long) -> Unit,
) {
    val colors = NexusTheme.colors
    val lane = problem.debriefLane()
    val laneTone = when (lane) {
        SessionDebriefLane.SOLVED -> NexusTone.Success
        SessionDebriefLane.ATTENTION -> NexusTone.Warning
        SessionDebriefLane.PENDING -> NexusTone.Neutral
    }
    val laneLabel = when (lane) {
        SessionDebriefLane.SOLVED -> R.string.session_debrief_solved
        SessionDebriefLane.ATTENTION -> R.string.session_debrief_attention
        SessionDebriefLane.PENDING -> R.string.session_debrief_pending
    }
    val actionLabel = if (problem.inReview) {
        R.string.session_debrief_open_review
    } else {
        R.string.session_debrief_open
    }
    val actionText = stringResource(actionLabel)
    val actionDescription = stringResource(R.string.session_debrief_open_cd, actionText)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NexusSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(SessionStatusRailWidth)
                .height(NexusSize.tableRowHeight)
                .background(laneTone.foregroundColor(colors), NexusRadius.xs),
        )
        Spacer(modifier = Modifier.width(NexusSpacing.xs))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = problem.judge?.uppercase().orEmpty(),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.accent,
                )
                Spacer(modifier = Modifier.width(NexusSpacing.xxs))
                Text(
                    text = problem.externalId.orEmpty(),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.width(NexusSpacing.xxs))
                problem.latestVerdict?.let { verdict ->
                    NexusTag(
                        text = stringResource(verdict.labelRes()),
                        tone = verdict.tone(),
                        selected = verdict == Verdict.AC,
                    )
                }
            }
            Text(
                text = problem.title,
                style = NexusTheme.typography.data,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(laneLabel),
                    style = NexusTheme.typography.dataSmall,
                    color = laneTone.foregroundColor(colors),
                )
                Spacer(modifier = Modifier.width(NexusSpacing.xs))
                Text(
                    text = stringResource(R.string.session_debrief_attempts, problem.attempts),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                )
            }
        }
        Spacer(modifier = Modifier.width(NexusSpacing.xxs))
        Box(
            modifier = Modifier
                .height(SessionOpenHeight)
                .widthIn(min = SessionOpenHeight)
                .semantics { contentDescription = actionDescription }
                .clickable(role = Role.Button) {
                    if (problem.inReview) onOpenReview(problem.problemId) else onOpenProblem(problem.problemId)
                }
                .padding(horizontal = NexusSpacing.xxs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = actionText,
                style = NexusTheme.typography.dataSmall,
                color = colors.accent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SessionProgressBoard(
    problems: List<SessionProblem>,
    selectedProblemId: Long?,
    onSelectProblem: (Long) -> Unit,
    onOpenProblem: (Long) -> Unit,
) {
    val colors = NexusTheme.colors
    val pulse = deriveSessionProgressPulse(problems)
    val targetFraction = sessionProgressFraction(pulse)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = if (NexusTheme.reduceMotion) snap() else tween(
            NexusMotion.DURATION_NORMAL,
            easing = NexusMotion.EasingStandard,
        ),
        label = "session progress rail",
    )

    NexusSection(label = stringResource(R.string.session_section_pulse)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
        ) {
            NexusMetric(
                label = stringResource(R.string.session_label_problems),
                value = pulse.total.toString(),
                modifier = Modifier.weight(1f),
            )
            NexusMetric(
                label = stringResource(R.string.session_progress_solved),
                value = pulse.solved.toString(),
                changeTone = NexusTone.Success,
                modifier = Modifier.weight(1f),
            )
            NexusMetric(
                label = stringResource(R.string.session_progress_attempted),
                value = pulse.attempted.toString(),
                changeTone = NexusTone.Warning,
                modifier = Modifier.weight(1f),
            )
            NexusMetric(
                label = stringResource(R.string.session_progress_pending),
                value = pulse.pending.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(NexusSpacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SessionProgressRailHeight)
                .background(colors.border, NexusRadius.xs),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .background(
                        if (pulse.total > 0) colors.accent else colors.border,
                        NexusRadius.xs,
                    ),
            )
        }
        Spacer(modifier = Modifier.height(NexusSpacing.xxs))
        Text(
            text = if (problems.isEmpty()) {
                stringResource(R.string.session_queue_empty)
            } else {
                stringResource(R.string.session_progress_complete, pulse.solved, pulse.total)
            },
            style = NexusTheme.typography.dataSmall,
            color = if (problems.isEmpty()) colors.textTertiary else colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(NexusSpacing.md))
        NexusSection(label = stringResource(R.string.session_queue_title)) {
            Column(
                modifier = Modifier.animateContentSize(
                    animationSpec = if (NexusTheme.reduceMotion) snap() else tween(
                        NexusMotion.DURATION_NORMAL,
                        easing = NexusMotion.EasingStandard,
                    ),
                ),
            ) {
                problems.forEachIndexed { index, problem ->
                    SessionProblemQueueRow(
                        problem = problem,
                        selected = problem.problemId == selectedProblemId,
                        onSelect = { onSelectProblem(problem.problemId) },
                        onOpenProblem = onOpenProblem,
                    )
                    if (index != problems.lastIndex) NexusDivider(insetEnd = NexusSpacing.xxs)
                }
            }
        }
    }
}

@Composable
private fun SessionProblemQueueRow(
    problem: SessionProblem,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpenProblem: (Long) -> Unit,
) {
    val colors = NexusTheme.colors
    val openDescription = stringResource(R.string.session_problem_open_cd)
    val selectedDescription = stringResource(R.string.session_problem_selected)
    val statusTone = when {
        problem.solved -> NexusTone.Success
        problem.attempts > 0 -> NexusTone.Warning
        else -> NexusTone.Neutral
    }
    val statusLabel = when {
        problem.solved -> R.string.session_progress_solved
        problem.attempts > 0 -> R.string.session_progress_attempted
        else -> R.string.session_progress_pending
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.border(NexusSize.dividerThickness, colors.accent, NexusRadius.sm)
                else Modifier,
            )
            .padding(vertical = NexusSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(SessionStatusRailWidth)
                .height(NexusSize.tableRowHeight)
                .background(statusTone.foregroundColor(colors), NexusRadius.xs),
        )
        Spacer(modifier = Modifier.width(NexusSpacing.xs))
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics {
                    if (selected) contentDescription = selectedDescription
                }
                .clickable(role = Role.Button, onClick = onSelect),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = problem.judge?.uppercase().orEmpty(),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.accent,
                )
                Spacer(modifier = Modifier.width(NexusSpacing.xxs))
                Text(
                    text = problem.externalId.orEmpty(),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.width(NexusSpacing.xxs))
                NexusTag(
                    text = stringResource(statusLabel),
                    tone = statusTone,
                    selected = problem.solved,
                )
            }
            Text(
                text = problem.title,
                style = NexusTheme.typography.data,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.session_summary_attempts) + " " + problem.attempts,
                style = NexusTheme.typography.dataSmall,
                color = colors.textTertiary,
            )
        }
        Spacer(modifier = Modifier.width(NexusSpacing.xxs))
        Box(
            modifier = Modifier
                .height(SessionOpenHeight)
                .border(NexusSize.dividerThickness, colors.accent, NexusRadius.xs)
                .clickable(
                    role = Role.Button,
                    onClickLabel = openDescription,
                ) { onOpenProblem(problem.problemId) }
                .semantics { contentDescription = openDescription }
                .padding(horizontal = NexusSpacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.session_problem_open),
                style = NexusTheme.typography.sectionLabel,
                color = colors.accent,
            )
        }
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
private fun ActionButton(
    label: String,
    accent: Boolean,
    danger: Boolean = false,
    description: String? = null,
    onClick: () -> Unit,
) {
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
            .semantics {
                description?.let { contentDescription = it }
            }
            .clickable(role = Role.Button) { onClick() }
            .padding(horizontal = NexusSpacing.md, vertical = NexusSpacing.xs),
    ) {
        Text(text = label, style = NexusTheme.typography.data, color = foreground)
    }
}
