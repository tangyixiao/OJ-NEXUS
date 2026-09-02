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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.ojnexus.core.model.ReviewQueueItem
import com.ojnexus.core.model.TaskType
import com.ojnexus.core.model.TrainingSession
import com.ojnexus.core.model.TrainingTask
import com.ojnexus.core.model.TrainingType
import com.ojnexus.core.data.repository.KnowledgeAreaState
import com.ojnexus.core.domain.MasteryReason
import com.ojnexus.core.domain.TrainingReason
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.formatDate
import com.ojnexus.core.ui.formatDuration
import com.ojnexus.core.ui.formatCount
import com.ojnexus.core.ui.labelRes
import com.ojnexus.core.ui.tone

@Composable
fun TrainingScreen(
    onOpenSession: (Long?) -> Unit,
    onOpenReview: (Long) -> Unit,
    onOpenProblem: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<TrainingViewModel>(
        factory = ContainerViewModelFactory(container) {
            TrainingViewModel(
                trainingRepository = it.trainingRepository,
                reviewRepository = it.reviewRepository,
                problemRepository = it.problemRepository,
                knowledgeRepository = it.knowledgeRepository,
                clock = it.clock,
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
        NexusTopBar(title = stringResource(R.string.nav_training))
        when (val s = state) {
            Loadable.Loading -> Box(Modifier.fillMaxSize())
            is Loadable.Failed -> CenteredError(s.message)
            is Loadable.Ready -> TrainingContent(
                uiState = s.value,
                problems = problems,
                viewModel = viewModel,
                onOpenSession = onOpenSession,
                onOpenReview = onOpenReview,
                onOpenProblem = onOpenProblem,
            )
        }
    }
}

@Composable
private fun CenteredError(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = NexusTheme.typography.data,
            color = NexusTheme.colors.danger,
        )
    }
}

@Composable
private fun TrainingContent(
    uiState: TrainingUiState,
    problems: List<com.ojnexus.core.model.Problem>,
    viewModel: TrainingViewModel,
    onOpenSession: (Long?) -> Unit,
    onOpenReview: (Long) -> Unit,
    onOpenProblem: (Long) -> Unit,
) {
    var showTaskDialog by rememberSaveable { mutableStateOf(false) }
    var showSessionDialog by rememberSaveable { mutableStateOf(false) }
    var showTaskProblemPicker by rememberSaveable { mutableStateOf(false) }
    var reviewFilter by rememberSaveable { mutableStateOf(ReviewQueueFilter.ALL) }
    val reduceMotion = NexusTheme.reduceMotion
    val reviewSummary = reviewQueueSummary(uiState.reviews)
    val visibleReviews = filterReviewBuckets(uiState.reviews, reviewFilter)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexusSpacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(NexusSpacing.md))

        // SESSION
        SessionSection(
            activeSession = uiState.activeSession,
            onOpenSession = onOpenSession,
            onNewSession = { showSessionDialog = true },
        )

        SectionGap()

        ReviewPulse(
            summary = reviewSummary,
            onStartNext = { problemId -> onOpenReview(problemId) },
        )

        SectionGap()

        // REVIEW QUEUE
        NexusSection(
            label = stringResource(R.string.review_section_queue),
            trailing = {
                Text(
                    text = (
                        visibleReviews.overdue.size +
                            visibleReviews.dueToday.size +
                            visibleReviews.upcoming.size
                        ).toString(),
                    style = NexusTheme.typography.data,
                    color = if (
                        visibleReviews.overdue.isNotEmpty() ||
                        visibleReviews.dueToday.isNotEmpty() ||
                        visibleReviews.upcoming.isNotEmpty()
                    ) {
                        NexusTheme.colors.accent
                    } else {
                        NexusTheme.colors.textTertiary
                    },
                )
            },
        ) {
            ReviewFilterControls(
                selected = reviewFilter,
                onSelected = { reviewFilter = it },
            )
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            Column(
                modifier = Modifier.animateContentSize(
                    animationSpec = if (reduceMotion) snap() else tween(
                        NexusMotion.DURATION_NORMAL,
                        easing = NexusMotion.EasingStandard,
                    ),
                ),
            ) {
                val reviews = visibleReviews
                if (reviews.isEmpty) {
                    SectionEmpty(
                        stringResource(
                            if (uiState.reviews.isEmpty) R.string.review_queue_empty else R.string.review_filter_empty,
                        ),
                    )
                } else {
                    ReviewQueueGroups(
                        reviews = reviews,
                        todayEpochDay = uiState.todayEpochDay,
                        onOpenReview = onOpenReview,
                    )
                }
            }
        }

        SectionGap()

        RecommendationSection(uiState.recommendations, onOpenProblem)

        SectionGap()

        KnowledgeSection(uiState.knowledge)

        SectionGap()

        // TODAY tasks
        NexusSection(
            label = stringResource(R.string.dash_section_today),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.tasks.any { it.completed }) {
                        Text(
                            text = stringResource(R.string.task_clear_done),
                            style = NexusTheme.typography.sectionLabel,
                            color = NexusTheme.colors.accent,
                            modifier = Modifier
                                .clickable(role = Role.Button) { viewModel.clearCompleted() }
                                .padding(end = NexusSpacing.sm),
                        )
                    }
                    Text(
                        text = stringResource(R.string.task_add_title),
                        style = NexusTheme.typography.sectionLabel,
                        color = NexusTheme.colors.accent,
                        modifier = Modifier.clickable(role = Role.Button) { showTaskDialog = true },
                    )
                }
            },
        ) {
            if (uiState.tasks.isEmpty()) {
                SectionEmpty(stringResource(R.string.task_empty))
            } else {
                uiState.tasks.forEachIndexed { index, task ->
                    TaskRow(
                        task = task,
                        onToggle = { viewModel.toggleTask(task.id, task.completed) },
                        onDelete = { viewModel.deleteTask(task.id) },
                    )
                    if (index != uiState.tasks.lastIndex) NexusDivider(insetEnd = NexusSpacing.xxs)
                }
            }
        }

        SectionGap()

        // RECENT SESSIONS
        NexusSection(label = stringResource(R.string.session_history)) {
            if (uiState.history.isEmpty()) {
                SectionEmpty(stringResource(R.string.session_history_empty))
            } else {
                uiState.history.forEachIndexed { index, session ->
                    HistoryRow(session) { onOpenSession(session.id) }
                    if (index != uiState.history.lastIndex) NexusDivider(insetEnd = NexusSpacing.xxs)
                }
            }
        }

        Spacer(modifier = Modifier.height(NexusSpacing.xxl))
    }

    if (showTaskDialog) {
        AddTaskDialog(
            problems = problems,
            onConfirm = { type, problemId, title ->
                viewModel.addTask(type, problemId, title)
                showTaskDialog = false
            },
            onDismiss = { showTaskDialog = false },
        )
    }
    if (showSessionDialog) {
        NewSessionDialog(
            problems = problems,
            onConfirm = { type, duration, tag, problemIds ->
                viewModel.startSession(type, duration, tag, problemIds)
                showSessionDialog = false
                onOpenSession(null)
            },
            onDismiss = { showSessionDialog = false },
        )
    }
}

@Composable
private fun RecommendationSection(
    recommendations: List<TrainingRecommendation>,
    onOpenProblem: (Long) -> Unit,
) {
    NexusSection(label = stringResource(R.string.training_section_targets)) {
        if (recommendations.isEmpty()) {
            SectionEmpty(stringResource(R.string.training_target_empty))
        } else {
            recommendations.take(5).forEachIndexed { index, recommendation ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onOpenProblem(recommendation.problemId) }
                        .padding(vertical = NexusSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = recommendation.judge.uppercase(),
                                style = NexusTheme.typography.sectionLabel,
                                color = NexusTheme.colors.textTertiary,
                                modifier = Modifier.padding(end = NexusSpacing.xxs),
                            )
                            Text(
                                text = recommendation.externalId,
                                style = NexusTheme.typography.data,
                                color = NexusTheme.colors.accent,
                            )
                        }
                        Text(
                            text = recommendation.title,
                            style = NexusTheme.typography.label,
                            color = NexusTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TrainingReason.entries.filter { it in recommendation.reasons }.take(2).forEach { reason ->
                        NexusTag(
                            text = stringResource(reason.labelRes()),
                            tone = NexusTone.Warning,
                            modifier = Modifier.padding(end = NexusSpacing.xxs),
                        )
                    }
                    Text(
                        text = stringResource(R.string.training_priority, recommendation.priority),
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.accent,
                    )
                }
                if (index != recommendations.take(5).lastIndex) NexusDivider(insetEnd = NexusSpacing.xxs)
            }
        }
    }
}

@Composable
private fun ReviewPulse(
    summary: ReviewQueueSummary,
    onStartNext: (Long) -> Unit,
) {
    val colors = NexusTheme.colors
    val reduceMotion = NexusTheme.reduceMotion
    val nextProblemId = summary.nextDueProblemId
    val actionDescription = stringResource(
        if (nextProblemId == null) R.string.training_pulse_nothing_due_cd
        else R.string.training_pulse_start_next_cd,
    )

    NexusSection(label = stringResource(R.string.training_section_pulse)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
        ) {
            PulseMetric(
                label = stringResource(R.string.training_pulse_overdue),
                value = summary.overdue,
                modifier = Modifier.weight(1f),
            )
            PulseMetric(
                label = stringResource(R.string.training_pulse_today),
                value = summary.dueToday,
                modifier = Modifier.weight(1f),
            )
            PulseMetric(
                label = stringResource(R.string.training_pulse_upcoming),
                value = summary.upcoming,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(NexusSpacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = NexusSize.dividerThickness,
                    color = if (nextProblemId != null) colors.accent else colors.border,
                    shape = NexusRadius.xs,
                )
                .clickable(
                    enabled = nextProblemId != null,
                    role = Role.Button,
                    onClickLabel = actionDescription,
                ) {
                    nextProblemId?.let(onStartNext)
                }
                .semantics { contentDescription = actionDescription }
                .padding(horizontal = NexusSpacing.sm, vertical = NexusSpacing.xs),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(
                    if (nextProblemId == null) R.string.training_pulse_nothing_due
                    else R.string.training_pulse_start_next,
                ),
                style = NexusTheme.typography.sectionLabel,
                color = if (nextProblemId != null) colors.accent else colors.textTertiary,
            )
        }
    }
}

@Composable
private fun PulseMetric(label: String, value: Int, modifier: Modifier = Modifier) {
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = if (NexusTheme.reduceMotion) snap() else tween(
            NexusMotion.DURATION_NORMAL,
            easing = NexusMotion.EasingStandard,
        ),
        label = "review pulse $label",
    )
    NexusMetric(label = label, value = formatCount(animatedValue), modifier = modifier)
}

@Composable
private fun ReviewFilterControls(
    selected: ReviewQueueFilter,
    onSelected: (ReviewQueueFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
    ) {
        ReviewFilterButton(
            filter = ReviewQueueFilter.ALL,
            label = stringResource(R.string.training_filter_all),
            description = stringResource(R.string.training_filter_all_cd),
            selected = selected == ReviewQueueFilter.ALL,
            onClick = onSelected,
            modifier = Modifier.weight(1f),
        )
        ReviewFilterButton(
            filter = ReviewQueueFilter.DUE_NOW,
            label = stringResource(R.string.training_filter_due_now),
            description = stringResource(R.string.training_filter_due_now_cd),
            selected = selected == ReviewQueueFilter.DUE_NOW,
            onClick = onSelected,
            modifier = Modifier.weight(1f),
        )
        ReviewFilterButton(
            filter = ReviewQueueFilter.UPCOMING,
            label = stringResource(R.string.training_filter_upcoming),
            description = stringResource(R.string.training_filter_upcoming_cd),
            selected = selected == ReviewQueueFilter.UPCOMING,
            onClick = onSelected,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReviewFilterButton(
    filter: ReviewQueueFilter,
    label: String,
    description: String,
    selected: Boolean,
    onClick: (ReviewQueueFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NexusTheme.colors
    Box(
        modifier = modifier
            .height(NexusSize.commandBarHeight)
            .background(if (selected) colors.surface else colors.background, NexusRadius.xs)
            .border(
                width = NexusSize.dividerThickness,
                color = if (selected) colors.accent else colors.border,
                shape = NexusRadius.xs,
            )
            .clickable(role = Role.Button, onClickLabel = description) { onClick(filter) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = NexusTheme.typography.sectionLabel,
            color = if (selected) colors.accent else colors.textTertiary,
        )
    }
}

@Composable
private fun ReviewQueueGroups(
    reviews: ReviewBuckets,
    todayEpochDay: Long,
    onOpenReview: (Long) -> Unit,
) {
    if (reviews.overdue.isNotEmpty()) {
        QueueGroup(
            label = stringResource(R.string.review_filter_overdue),
            items = reviews.overdue,
            todayEpochDay = todayEpochDay,
            onOpenReview = onOpenReview,
        )
    }
    if (reviews.dueToday.isNotEmpty()) {
        QueueGroup(
            label = stringResource(R.string.review_filter_due),
            items = reviews.dueToday,
            todayEpochDay = todayEpochDay,
            onOpenReview = onOpenReview,
        )
    }
    if (reviews.upcoming.isNotEmpty()) {
        QueueGroup(
            label = stringResource(R.string.review_filter_upcoming),
            items = reviews.upcoming,
            todayEpochDay = todayEpochDay,
            onOpenReview = onOpenReview,
        )
    }
}

@Composable
private fun KnowledgeSection(areas: List<KnowledgeAreaState>) {
    NexusSection(
        label = stringResource(R.string.training_section_knowledge),
        trailing = {
            Text(
                text = stringResource(R.string.knowledge_area_count, areas.size),
                style = NexusTheme.typography.sectionLabel,
                color = NexusTheme.colors.textTertiary,
            )
        },
    ) {
        areas.forEachIndexed { index, area ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = NexusSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(area.area.labelRes()),
                    style = NexusTheme.typography.data,
                    color = NexusTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                area.reasons.take(2).forEach { reason ->
                    NexusTag(
                        text = stringResource(reason.labelRes()),
                        tone = if (reason == MasteryReason.FAILURE_LOG) NexusTone.Danger else NexusTone.Warning,
                        modifier = Modifier.padding(end = NexusSpacing.xxs),
                    )
                }
                Text(
                    text = stringResource(R.string.knowledge_score, area.score),
                    style = NexusTheme.typography.dataSmall,
                    color = if (area.score >= 80) NexusTheme.colors.success else NexusTheme.colors.accent,
                )
            }
            if (index != areas.lastIndex) NexusDivider(insetEnd = NexusSpacing.xxs)
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
private fun SectionEmpty(text: String) {
    Text(
        text = text,
        style = NexusTheme.typography.dataSmall,
        color = NexusTheme.colors.textTertiary,
        modifier = Modifier.padding(vertical = NexusSpacing.xs),
    )
}

@Composable
private fun SessionSection(
    activeSession: TrainingSession?,
    onOpenSession: (Long?) -> Unit,
    onNewSession: () -> Unit,
) {
    val colors = NexusTheme.colors
    NexusSection(
        label = stringResource(R.string.training_section_session),
        trailing = {
            if (activeSession != null) {
                NexusStatus(label = stringResource(R.string.sync_state_syncing), tone = NexusTone.Accent)
            } else {
                NexusStatus(label = stringResource(R.string.session_state_standby), tone = NexusTone.Neutral)
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, NexusRadius.md)
                .padding(NexusSpacing.md),
        ) {
            if (activeSession == null) {
                Text(
                    text = stringResource(R.string.session_no_active),
                    style = NexusTheme.typography.title,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                    TrainingType.entries.forEach { type ->
                        NexusTag(text = stringResource(type.labelRes()), tone = NexusTone.Accent, selected = type == TrainingType.PRACTICE)
                    }
                }
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                Box(
                    modifier = Modifier
                        .background(colors.background, NexusRadius.sm)
                        .clickable(role = Role.Button) { onNewSession() }
                        .padding(horizontal = NexusSpacing.md, vertical = NexusSpacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.session_create),
                        style = NexusTheme.typography.data,
                        color = colors.accent,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NexusTag(
                        text = stringResource(activeSession.type.labelRes()),
                        tone = NexusTone.Accent,
                        selected = true,
                    )
                    if (activeSession.pausedAt != null) {
                        Text(
                            text = stringResource(R.string.session_paused_label),
                            style = NexusTheme.typography.dataSmall,
                            color = colors.warning,
                            modifier = Modifier.padding(start = NexusSpacing.xs),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.session_running_title),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textTertiary,
                    )
                }
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                Box(
                    modifier = Modifier
                        .background(colors.background, NexusRadius.sm)
                        .clickable(role = Role.Button) { onOpenSession(null) }
                        .padding(horizontal = NexusSpacing.md, vertical = NexusSpacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.session_action_resume),
                        style = NexusTheme.typography.data,
                        color = colors.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueGroup(
    label: String,
    items: List<ReviewQueueItem>,
    todayEpochDay: Long,
    onOpenReview: (Long) -> Unit,
) {
    Text(
        text = "$label · ${items.size}",
        style = NexusTheme.typography.sectionLabel,
        color = NexusTheme.colors.textTertiary,
        modifier = Modifier.padding(vertical = NexusSpacing.xxs),
    )
    items.forEachIndexed { index, item ->
        QueueRow(item, todayEpochDay, onOpenReview)
        if (index != items.lastIndex) NexusDivider(insetEnd = NexusSpacing.xxs)
    }
    Spacer(modifier = Modifier.height(NexusSpacing.xxs))
}

@Composable
private fun QueueRow(item: ReviewQueueItem, todayEpochDay: Long, onOpenReview: (Long) -> Unit) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(NexusSize.tableRowHeight)
            .clickable { onOpenReview(item.problemId) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.judge.displayName,
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(end = NexusSpacing.xxs),
                )
                Text(
                    text = item.problemTitle,
                    style = NexusTheme.typography.data,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = buildString {
                    append(stringResource(R.string.review_stage_label, item.stage + 1))
                    append(" · ")
                    append(stringResource(R.string.problems_header_rating))
                    append(" ")
                    append(item.difficulty?.toString() ?: stringResource(R.string.problems_no_value))
                },
                style = NexusTheme.typography.dataSmall,
                color = colors.textTertiary,
            )
        }
        Text(
            text = formatDate(item.dueAt),
            style = NexusTheme.typography.dataSmall,
            color = if (item.dueDayIndex <= todayEpochDay) colors.accent else colors.textTertiary,
        )
    }
}

@Composable
private fun TaskRow(task: TrainingTask, onToggle: () -> Unit, onDelete: () -> Unit) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(NexusSize.tableRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Completion square.
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.task_done_cd)) { onToggle() }
                .padding(8.dp)
                .background(
                    if (task.completed) colors.accent else colors.surface,
                    RoundedCornerShapeSmall,
                )
                .border(1.dp, if (task.completed) colors.accent else colors.borderStrong, RoundedCornerShapeSmall),
        )
        val title = task.problemTitle
            ?: task.title
            ?: task.type.name
        Text(
            text = title,
            style = NexusTheme.typography.data,
            color = if (task.completed) colors.textTertiary else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = NexusSpacing.xs),
        )
        NexusTag(
            text = stringResource(task.type.labelRes()),
            tone = NexusTone.Neutral,
            modifier = Modifier.padding(end = NexusSpacing.xxs),
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.task_delete_cd)) { onDelete() },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "×", style = NexusTheme.typography.data, color = colors.textTertiary)
        }
    }
}

private val RoundedCornerShapeSmall = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)

@Composable
private fun HistoryRow(session: TrainingSession, onOpen: () -> Unit) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(NexusSize.tableRowHeight)
            .clickable { onOpen() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NexusTag(
            text = stringResource(session.type.labelRes()),
            tone = if (session.state == com.ojnexus.core.model.SessionState.CANCELLED) NexusTone.Neutral else NexusTone.Accent,
            modifier = Modifier.padding(end = NexusSpacing.xs),
        )
        Text(
            text = formatDateTimeShort(session.startedAt),
            style = NexusTheme.typography.dataSmall,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatDuration(
                (com.ojnexus.core.domain.SessionClock.elapsedMs(
                    startedAt = session.startedAt,
                    totalPausedMs = session.totalPausedMs,
                    pausedAt = session.pausedAt,
                    finishedAt = session.finishedAt,
                    now = session.finishedAt ?: session.startedAt,
                ) / 60_000L),
            ),
            style = NexusTheme.typography.dataSmall,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun formatDateTimeShort(epochMs: Long): String = formatDate(epochMs)

@Composable
private fun AddTaskDialog(
    problems: List<com.ojnexus.core.model.Problem>,
    onConfirm: (TaskType, Long?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(TaskType.SOLVE) }
    var title by rememberSaveable { mutableStateOf("") }
    var selectedProblemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showProblemPicker by rememberSaveable { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusTheme.colors.surface,
        titleContentColor = NexusTheme.colors.textPrimary,
        textContentColor = NexusTheme.colors.textSecondary,
        title = { Text(text = stringResource(R.string.task_add_title), style = NexusTheme.typography.title) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.task_field_type),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xxs))
                Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxxs)) {
                    TaskType.entries.forEach { t ->
                        NexusTag(
                            text = stringResource(t.labelRes()),
                            tone = NexusTone.Accent,
                            selected = type == t,
                            modifier = Modifier.clickable(role = Role.Button) { type = t },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                if (selectedProblemId != null) {
                    val problem = problems.firstOrNull { it.id == selectedProblemId }
                    Text(
                        text = problem?.title ?: stringResource(R.string.review_problem_missing),
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.textPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.task_field_problem),
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.accent,
                        modifier = Modifier.clickable(role = Role.Button) { showProblemPicker = true },
                    )
                }
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                Text(
                    text = stringResource(R.string.task_field_title),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xxxs))
                com.ojnexus.feature.training.NexusPlainField(value = title, onValueChange = { title = it })
            }
        },
        confirmButton = {
            Text(
                text = stringResource(R.string.action_save),
                style = NexusTheme.typography.data,
                color = NexusTheme.colors.accent,
                modifier = Modifier
                    .clickable(role = Role.Button) {
                        onConfirm(type, selectedProblemId, title.ifBlank { null })
                    }
                    .padding(NexusSpacing.xs),
            )
        },
        dismissButton = {
            Text(
                text = stringResource(R.string.action_cancel),
                style = NexusTheme.typography.data,
                color = NexusTheme.colors.textSecondary,
                modifier = Modifier
                    .clickable(role = Role.Button) { onDismiss() }
                    .padding(NexusSpacing.xs),
            )
        },
    )

    if (showProblemPicker) {
        ProblemPickerDialog(
            problems = problems,
            onPick = { problem ->
                selectedProblemId = problem?.id
                showProblemPicker = false
            },
        )
    }
}

@Composable
private fun ProblemPickerDialog(problems: List<com.ojnexus.core.model.Problem>, onPick: (com.ojnexus.core.model.Problem?) -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onPick(null) },
        containerColor = NexusTheme.colors.surface,
        titleContentColor = NexusTheme.colors.textPrimary,
        title = { Text(text = stringResource(R.string.task_field_problem), style = NexusTheme.typography.title) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .height(280.dp),
            ) {
                if (problems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.problems_empty_title),
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.textTertiary,
                    )
                }
                problems.forEach { problem ->
                    Text(
                        text = "${problem.key.judge.displayName} ${problem.key.externalId} · ${problem.title}",
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable(role = Role.Button) { onPick(problem) }
                            .padding(vertical = NexusSpacing.xs),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Text(
                text = stringResource(R.string.action_cancel),
                style = NexusTheme.typography.data,
                color = NexusTheme.colors.textSecondary,
                modifier = Modifier
                    .clickable(role = Role.Button) { onPick(null) }
                    .padding(NexusSpacing.xs),
            )
        },
    )
}

@Composable
private fun NewSessionDialog(
    problems: List<com.ojnexus.core.model.Problem>,
    onConfirm: (TrainingType, Int?, String?, List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(TrainingType.PRACTICE) }
    var duration by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf("") }
    val selected = rememberSaveable { mutableStateOf(setOf<Long>()) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusTheme.colors.surface,
        titleContentColor = NexusTheme.colors.textPrimary,
        textContentColor = NexusTheme.colors.textSecondary,
        title = { Text(text = stringResource(R.string.session_create_title), style = NexusTheme.typography.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.task_field_type),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.textTertiary,
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
                com.ojnexus.feature.training.NexusPlainField(
                    label = stringResource(R.string.session_target_duration),
                    value = duration,
                    onValueChange = { duration = it },
                    number = true,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xxs))
                com.ojnexus.feature.training.NexusPlainField(
                    label = stringResource(R.string.session_target_tag),
                    value = tag,
                    onValueChange = { tag = it },
                )
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                Text(
                    text = stringResource(R.string.session_pick_problems) +
                        " · " + stringResource(R.string.session_picked_count, selected.value.size),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xxxs))
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .height(200.dp),
                ) {
                    problems.forEach { problem ->
                        val picked = problem.id in selected.value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) {
                                    selected.value = if (picked) {
                                        selected.value - problem.id
                                    } else {
                                        selected.value + problem.id
                                    }
                                }
                                .padding(vertical = NexusSpacing.xxxs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(end = NexusSpacing.xs)
                                    .size(NexusSize.statusDot)
                                    .background(
                                        if (picked) NexusTheme.colors.accent else NexusTheme.colors.surface,
                                        androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                                    )
                                    .border(1.dp, if (picked) NexusTheme.colors.accent else NexusTheme.colors.borderStrong, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                            )
                            Text(
                                text = "${problem.key.judge.displayName} ${problem.key.externalId} · ${problem.title}",
                                style = NexusTheme.typography.dataSmall,
                                color = NexusTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Text(
                text = stringResource(R.string.session_start),
                style = NexusTheme.typography.data,
                color = NexusTheme.colors.accent,
                modifier = Modifier
                    .clickable(role = Role.Button) {
                        onConfirm(type, duration.trim().toIntOrNull(), tag.trim(), selected.value.toList())
                    }
                    .padding(NexusSpacing.xs),
            )
        },
        dismissButton = {
            Text(
                text = stringResource(R.string.action_cancel),
                style = NexusTheme.typography.data,
                color = NexusTheme.colors.textSecondary,
                modifier = Modifier
                    .clickable(role = Role.Button) { onDismiss() }
                    .padding(NexusSpacing.xs),
            )
        },
    )
}

/** Minimal single-line text field used inside dialogs. */
@Composable
fun NexusPlainField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    number: Boolean = false,
) {
    Column {
        if (label != null) {
            Text(
                text = label,
                style = NexusTheme.typography.sectionLabel,
                color = NexusTheme.colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(NexusSpacing.xxxs))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusTheme.colors.background, NexusRadius.sm)
                .border(1.dp, NexusTheme.colors.border, NexusRadius.sm)
                .padding(horizontal = NexusSpacing.xs, vertical = NexusSpacing.xxxs),
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (number) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text,
                ),
                textStyle = NexusTheme.typography.dataSmall.copy(color = NexusTheme.colors.textPrimary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(NexusTheme.colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
