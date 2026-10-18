package com.ojnexus.feature.problems

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusMetric
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.model.Attempt
import com.ojnexus.core.model.FailureCategory
import com.ojnexus.core.model.FailureEntry
import com.ojnexus.core.model.NoteEditorField
import com.ojnexus.core.model.NoteSlot
import com.ojnexus.core.model.NoteTemplate
import com.ojnexus.core.model.NoteTemplateField
import com.ojnexus.core.model.ProblemDetail
import com.ojnexus.core.model.KnowledgeArea
import com.ojnexus.core.model.autoLinkedKnowledgeAreas
import com.ojnexus.core.model.ReviewResult
import com.ojnexus.core.model.Verdict
import com.ojnexus.core.model.editorFields
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.formatDate
import com.ojnexus.core.ui.formatDateTime
import com.ojnexus.core.ui.labelRes
import com.ojnexus.core.ui.submission.SubmissionDetailSheet
import com.ojnexus.core.ui.tone
import java.time.LocalDate
import java.time.ZoneId

private val IconTouchSize = 32.dp

/** Opens the problem's source page in the system browser (no WebView, per product rules). */
object OpenInBrowser {
    fun open(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // No browser available — nothing sensible to do; ignore rather than crash.
        }
    }
}

@Composable
fun ProblemDetailScreen(
    problemId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenReview: (Long) -> Unit,
    onOpenWorkspace: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ProblemDetailViewModel>(
        key = "problem-detail-$problemId",
        factory = ContainerViewModelFactory(container) {
            ProblemDetailViewModel(
                problemId = problemId,
                problemRepository = it.problemRepository,
                reviewRepository = it.reviewRepository,
                knowledgeRepository = it.knowledgeRepository,
                preferencesRepository = it.userPreferencesRepository,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Persist pending note edits when leaving the screen.
    DisposableEffect(Unit) {
        onDispose { viewModel.flushNotes() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        NexusTopBar(title = stringResource(R.string.nav_problems))
        when (val s = state) {
            Loadable.Loading -> Box(Modifier.fillMaxSize())
            is Loadable.Failed -> NotFoundState()
            is Loadable.Ready -> DetailContent(
                uiState = s.value,
                viewModel = viewModel,
                onBack = onBack,
                onEdit = onEdit,
                onOpenReview = onOpenReview,
                onOpenWorkspace = onOpenWorkspace,
            )
        }
    }
}

@Composable
private fun NotFoundState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.detail_not_found),
            style = NexusTheme.typography.data,
            color = NexusTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun DetailContent(
    uiState: ProblemDetailUiState,
    viewModel: ProblemDetailViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenReview: (Long) -> Unit,
    onOpenWorkspace: (String) -> Unit,
) {
    val context = LocalContext.current
    val detail = uiState.detail
    val colors = NexusTheme.colors
    var showAttemptDialog by remember { mutableStateOf(false) }
    var showFailureDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTemplateSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexusSpacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(NexusSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = detail.problem.key.judge.displayName,
                style = NexusTheme.typography.sectionLabel,
                color = colors.textTertiary,
                modifier = Modifier.padding(end = NexusSpacing.xxs),
            )
            Text(
                text = detail.problem.key.externalId,
                style = NexusTheme.typography.dataLarge,
                color = colors.accent,
                modifier = Modifier.weight(1f),
            )
            FavoriteToggle(favorite = detail.problem.favorite) {
                viewModel.toggleFavorite(detail.problem.favorite)
            }
        }
        Text(
            text = detail.problem.title,
            style = NexusTheme.typography.title,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = NexusSpacing.xxs),
        )
        Spacer(modifier = Modifier.height(NexusSpacing.xs))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
        ) {
            NexusTag(
                text = stringResource(detail.problem.status.labelRes()),
                tone = detail.problem.status.tone(),
                selected = true,
            )
            if (detail.problem.difficulty != null) {
                NexusTag(
                    text = if (detail.problem.difficultySource == com.ojnexus.core.model.DifficultySource.ESTIMATED) {
                        stringResource(R.string.problems_estimated_difficulty, detail.problem.difficulty)
                    } else {
                        detail.problem.difficulty.toString()
                    },
                    tone = NexusTone.Neutral,
                )
            }
            detail.problem.tags.forEach { tag ->
                NexusTag(text = tag, tone = NexusTone.Neutral)
            }
        }

        Spacer(modifier = Modifier.height(NexusSpacing.md))
        NexusDivider()
        Spacer(modifier = Modifier.height(NexusSpacing.md))

        NexusSection(label = stringResource(R.string.detail_section_actions)) {
            Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                    ActionChip(stringResource(R.string.attempt_add)) { showAttemptDialog = true }
                    if (!detail.problem.solved) {
                        ActionChip(stringResource(R.string.action_mark_solved)) {
                            viewModel.addAttempt(Verdict.AC, null, null, null)
                        }
                    }
                    ActionChip(stringResource(R.string.failure_add)) { showFailureDialog = true }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                    if (detail.problem.sourceUrl != null) {
                        ActionChip(stringResource(R.string.action_open_problem)) {
                            OpenInBrowser.open(context, detail.problem.sourceUrl)
                        }
                    } else {
                        NexusTag(text = stringResource(R.string.open_problem_no_url), tone = NexusTone.Neutral)
                    }
                    ActionChip(stringResource(R.string.action_edit)) { onEdit(detail.problem.id) }
                    ActionChip(stringResource(R.string.action_delete), danger = true) {
                        showDeleteDialog = true
                    }
                }
                if (detail.problem.key.judge == com.ojnexus.core.model.JudgeId.LUOGU) {
                    ActionChip(stringResource(R.string.workspace_open)) {
                        onOpenWorkspace(detail.problem.key.externalId)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(NexusSpacing.md))
        NexusDivider()
        Spacer(modifier = Modifier.height(NexusSpacing.md))

        NexusSection(label = stringResource(R.string.detail_section_overview)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                NexusMetric(
                    label = stringResource(R.string.problem_field_attempts),
                    value = detail.problem.attemptCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                NexusMetric(
                    label = stringResource(R.string.problem_field_first_ac),
                    value = detail.problem.firstSolvedAt
                        ?.let { formatDate(it) } ?: stringResource(R.string.problems_no_value),
                    modifier = Modifier.weight(1f),
                )
                NexusMetric(
                    label = stringResource(R.string.problem_field_last_attempt),
                    value = detail.problem.lastAttemptAt
                        ?.let { formatDate(it) } ?: stringResource(R.string.problems_no_value),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(NexusSpacing.md))
        NexusDivider()
        Spacer(modifier = Modifier.height(NexusSpacing.md))

        NexusSection(label = stringResource(R.string.detail_section_knowledge)) {
            Text(
                text = stringResource(R.string.knowledge_relation_hint),
                style = NexusTheme.typography.dataSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(bottom = NexusSpacing.xs),
            )
            val autoLinked = autoLinkedKnowledgeAreas(detail.problem.tags)
            val autoLinkedHere = autoLinked.filter { it in uiState.knowledge }
            if (autoLinkedHere.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.knowledge_auto_link_count,
                        autoLinkedHere.size,
                    ),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = NexusSpacing.xs),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                if (uiState.autoLinkCandidates.isNotEmpty()) {
                    ActionChip(stringResource(R.string.knowledge_auto_link)) {
                        viewModel.autoLinkKnowledge()
                    }
                }
                if (autoLinkedHere.isNotEmpty()) {
                    ActionChip(stringResource(R.string.knowledge_auto_link_clear)) {
                        viewModel.clearAutoLinks()
                    }
                }
            }
            KnowledgeArea.entries.chunked(2).forEach { rowAreas ->
                Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                    rowAreas.forEach { area ->
                        NexusTag(
                            text = stringResource(area.labelRes()),
                            tone = NexusTone.Accent,
                            selected = area in uiState.knowledge,
                            modifier = Modifier.clickable(role = Role.Button) {
                                viewModel.setKnowledge(area, area !in uiState.knowledge)
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(NexusSpacing.xxs))
            }
        }

        Spacer(modifier = Modifier.height(NexusSpacing.md))
        NexusDivider()
        Spacer(modifier = Modifier.height(NexusSpacing.md))

        NexusSection(label = stringResource(R.string.detail_section_review)) {
            val review = detail.review
            if (review == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
                ) {
                    NexusTag(text = stringResource(R.string.review_state_none), tone = NexusTone.Neutral)
                    ActionChip(stringResource(R.string.review_schedule)) { viewModel.scheduleReview() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
                    ) {
                        NexusTag(
                            text = stringResource(R.string.review_stage_label, review.stage + 1),
                            tone = NexusTone.Accent,
                            selected = true,
                        )
                        Text(
                            text = reviewDueLabel(review.dueAt),
                            style = NexusTheme.typography.dataSmall,
                            color = colors.textSecondary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                        ReviewResult.entries.forEach { result ->
                            ActionChip(
                                label = stringResource(result.labelRes()),
                                danger = result == ReviewResult.FAIL,
                            ) { viewModel.completeReview(result) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                        ActionChip(stringResource(R.string.review_start)) { onOpenReview(detail.problem.id) }
                        ActionChip(stringResource(R.string.review_cancel)) { viewModel.cancelReview() }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(NexusSpacing.md))
        NexusDivider()
        Spacer(modifier = Modifier.height(NexusSpacing.md))

        NexusSection(label = stringResource(R.string.detail_section_attempts)) {
            if (detail.attempts.isEmpty()) {
                SectionEmpty(stringResource(R.string.attempt_empty))
            } else {
                detail.attempts.forEachIndexed { index, attempt ->
                    AttemptRow(attempt)
                    if (index != detail.attempts.lastIndex) NexusDivider(insetEnd = NexusSpacing.xxs)
                }
            }
        }

        Spacer(modifier = Modifier.height(NexusSpacing.md))
        NexusDivider()
        Spacer(modifier = Modifier.height(NexusSpacing.md))

        NexusSection(label = stringResource(R.string.detail_section_failures)) {
            if (detail.failures.isEmpty()) {
                SectionEmpty(stringResource(R.string.failure_empty))
            } else {
                detail.failures.forEachIndexed { index, failure ->
                    FailureRow(failure) { viewModel.deleteFailure(failure.id) }
                    if (index != detail.failures.lastIndex) NexusDivider(insetEnd = NexusSpacing.xxs)
                }
            }
        }

        Spacer(modifier = Modifier.height(NexusSpacing.md))
        NexusDivider()
        Spacer(modifier = Modifier.height(NexusSpacing.md))

        NexusSection(
            label = stringResource(R.string.detail_section_insight),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            if (uiState.notesSaving) R.string.notes_saving else R.string.notes_saved,
                        ),
                        style = NexusTheme.typography.sectionLabel,
                        color = if (uiState.notesSaving) colors.accent else colors.textTertiary,
                    )
                    Spacer(modifier = Modifier.width(NexusSpacing.xs))
                    ActionChip(stringResource(R.string.notes_template_action)) {
                        showTemplateSheet = true
                    }
                }
            },
        ) {
            val draft = uiState.notesDraft
            if (draft != null) {
                val fields = uiState.noteTemplate.editorFields(draft.toNotes())
                if (fields.isEmpty()) {
                    SectionEmpty(stringResource(R.string.notes_template_empty))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.sm)) {
                        fields.forEach { field ->
                            NotesField(
                                label = field.label,
                                hint = field.hint,
                                value = field.value,
                                onValueChange = { value ->
                                    viewModel.setNotesField(notesDraftUpdate(field.slot, value))
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(NexusSpacing.xxl))
    }

    if (showAttemptDialog) {
        AddAttemptDialog(
            onConfirm = { verdict, duration, language, note ->
                viewModel.addAttempt(verdict, duration, language, note)
                showAttemptDialog = false
            },
            onDismiss = { showAttemptDialog = false },
        )
    }
    if (showFailureDialog) {
        AddFailureDialog(
            onConfirm = { category, description ->
                viewModel.addFailure(category, description)
                showFailureDialog = false
            },
            onDismiss = { showFailureDialog = false },
        )
    }
    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = NexusTheme.colors.surface,
            titleContentColor = NexusTheme.colors.textPrimary,
            textContentColor = NexusTheme.colors.textSecondary,
            title = {
                Text(text = stringResource(R.string.problem_delete_title), style = NexusTheme.typography.title)
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.problem_delete_confirm,
                        "${detail.problem.key.judge.displayName} ${detail.problem.key.externalId}",
                    ),
                    style = NexusTheme.typography.label,
                )
            },
            confirmButton = {
                DialogTextButton(stringResource(R.string.action_delete), NexusTheme.colors.danger) {
                    showDeleteDialog = false
                    viewModel.deleteProblem(onDeleted = onBack)
                }
            },
            dismissButton = {
                DialogTextButton(stringResource(R.string.action_cancel), NexusTheme.colors.textSecondary) {
                    showDeleteDialog = false
                }
            },
        )
    }
    if (showTemplateSheet) {
        NoteTemplateSheet(
            template = uiState.noteTemplate,
            onDismiss = { showTemplateSheet = false },
            onSave = { template ->
                viewModel.setNoteTemplate(template)
                showTemplateSheet = false
            },
        )
    }
}

@Composable
private fun reviewDueLabel(dueAt: Long): String {
    val zone = ZoneId.systemDefault()
    val dueDay = java.time.Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate().toEpochDay()
    val today = LocalDate.now(zone).toEpochDay()
    return when {
        dueDay < today -> stringResource(R.string.review_overdue, (today - dueDay).toInt())
        dueDay == today -> stringResource(R.string.review_due_today)
        else -> stringResource(R.string.review_due_label, formatDate(dueAt, zone))
    }
}

@Composable
private fun FavoriteToggle(favorite: Boolean, onToggle: () -> Unit) {
    val colors = NexusTheme.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(12.dp)
            .background(
                if (favorite) colors.accent else colors.surface,
                RoundedCornerShape(3.dp),
            )
            .border(1.dp, if (favorite) colors.accent else colors.borderStrong, RoundedCornerShape(3.dp)),
    )
}

@Composable
private fun ActionChip(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val colors = NexusTheme.colors
    val foreground = when {
        danger -> colors.danger
        else -> colors.accent
    }
    Box(
        modifier = Modifier
            .background(colors.surface, NexusRadius.sm)
            .border(1.dp, if (danger) colors.danger else colors.accent, NexusRadius.sm)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = NexusSpacing.sm, vertical = NexusSpacing.xs),
    ) {
        Text(text = label, style = NexusTheme.typography.dataSmall, color = foreground)
    }
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
private fun AttemptRow(attempt: Attempt) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    var sheetVisible by rememberSaveable("attempt-sheet-${attempt.id}") { mutableStateOf(false) }
    // Deep-link to the judge-side submission page when the row carries enough origin data.
    // Only the official URL builder may construct Codeforces URLs (docs/OJ_ADAPTERS.md).
    val submissionUrl: String? = when {
        attempt.sourceJudge != com.ojnexus.core.model.JudgeId.CODEFORCES.id -> null
        attempt.contestId == null || attempt.externalSubmissionId == null -> null
        else -> attempt.contestId.toLongOrNull()?.let { contestId ->
            attempt.externalSubmissionId?.toLongOrNull()?.let { submissionId ->
                com.ojnexus.judge.codeforces.CodeforcesUrls.submission(contestId, submissionId)
            }
        }
    }
    val openRemoteLabel = stringResource(R.string.attempt_open_remote)
    val detailLabel = stringResource(R.string.attempt_open_details)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(NexusSize.tableRowHeight)
            .clickable(
                role = Role.Button,
                onClickLabel = detailLabel,
            ) { sheetVisible = true }
            .semantics { contentDescription = detailLabel },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatDateTime(attempt.timestamp),
            style = NexusTheme.typography.dataSmall,
            color = colors.textTertiary,
            modifier = Modifier.padding(end = NexusSpacing.sm),
        )
        NexusTag(text = stringResource(attempt.verdict.labelRes()), tone = attempt.verdict.tone())
        Spacer(modifier = Modifier.weight(1f))
        if (submissionUrl != null) {
            Text(
                text = stringResource(R.string.attempt_open_remote),
                style = NexusTheme.typography.dataSmall,
                color = colors.accent,
                modifier = Modifier
                    .clickable(role = Role.Button, onClickLabel = openRemoteLabel) {
                        OpenInBrowser.open(context, submissionUrl)
                    }
                    .padding(end = NexusSpacing.xs),
            )
        }
        if (attempt.durationMinutes != null) {
            Text(
                text = stringResource(R.string.format_attempt_duration, attempt.durationMinutes),
                style = NexusTheme.typography.dataSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(end = NexusSpacing.xs),
            )
        }
        if (attempt.language != null) {
            Text(
                text = attempt.language,
                style = NexusTheme.typography.dataSmall,
                color = colors.textTertiary,
            )
        }
    }

    if (sheetVisible) {
        val detail = toSubmissionDetailModel(attempt, submissionUrl)
        SubmissionDetailSheet(
            detail = detail,
            onDismiss = { sheetVisible = false },
        )
    }
}

@Composable
private fun FailureRow(failure: FailureEntry, onDelete: () -> Unit) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NexusSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexusTag(
                    text = stringResource(failure.category.labelRes()),
                    tone = failure.category.tone(),
                    modifier = Modifier.padding(end = NexusSpacing.xs),
                )
                Text(
                    text = formatDateTime(failure.createdAt),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                )
            }
            Text(
                text = failure.description,
                style = NexusTheme.typography.label,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(IconTouchSize)
                .clickable(role = Role.Button, onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "×", style = NexusTheme.typography.data, color = colors.textTertiary)
        }
    }
}

@Composable
private fun NotesField(label: String, value: String, onValueChange: (String) -> Unit) {
    NotesField(label = label, hint = "", value = value, onValueChange = onValueChange)
}

@Composable
private fun NotesField(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
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
                .background(colors.surface, NexusRadius.sm)
                .border(1.dp, colors.border, NexusRadius.sm)
                .padding(horizontal = NexusSpacing.sm, vertical = NexusSpacing.xs),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = NexusTheme.typography.dataSmall.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (hint.isNotBlank()) {
            Text(
                text = hint,
                style = NexusTheme.typography.dataSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = NexusSpacing.xxxs),
            )
        }
    }
}

/** Maps a template slot back onto the draft field it stores into. */
private fun notesDraftUpdate(slot: NoteSlot, value: String): (NotesDraft) -> NotesDraft = when (slot) {
    NoteSlot.KEY_INSIGHT -> { draft -> draft.copy(keyInsight = value) }
    NoteSlot.IMPLEMENTATION -> { draft -> draft.copy(implementationNotes = value) }
    NoteSlot.COMPLEXITY -> { draft -> draft.copy(complexity = value) }
    NoteSlot.GENERAL -> { draft -> draft.copy(general = value) }
}

@Composable
private fun DialogTextButton(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = label,
        style = NexusTheme.typography.data,
        color = color,
        modifier = Modifier
            .clickable(role = Role.Button) { onClick() }
            .padding(NexusSpacing.xs),
    )
}

/**
 * Editable note template. The draft is local to the sheet: nothing is persisted until SAVE,
 * and an invalid draft keeps SAVE disabled with the reason spelled out.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NoteTemplateSheet(
    template: NoteTemplate,
    onDismiss: () -> Unit,
    onSave: (NoteTemplate) -> Unit,
) {
    val colors = NexusTheme.colors
    var draft by remember { mutableStateOf(template) }
    val valid = draft.validate()
    val usedSlots = draft.fields.map { it.slot }.toSet()
    val freeSlots = NoteSlot.entries.filter { it !in usedSlots }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        scrimColor = colors.background.copy(alpha = 0.72f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NexusSpacing.screenHorizontal)
                .padding(bottom = NexusSpacing.lg),
        ) {
            Text(
                text = stringResource(R.string.notes_template_title),
                style = NexusTheme.typography.title,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(NexusSpacing.xxs))
            Text(
                text = stringResource(R.string.notes_template_summary, draft.fields.size),
                style = NexusTheme.typography.sectionLabel,
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            NexusDivider()
            Spacer(modifier = Modifier.height(NexusSpacing.sm))

            if (draft.fields.isEmpty()) {
                Text(
                    text = stringResource(R.string.notes_template_empty),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
            }

            draft.fields.forEachIndexed { index, field ->
                TemplateFieldRow(
                    field = field,
                    canMoveUp = index > 0,
                    canMoveDown = index < draft.fields.lastIndex,
                    canRemove = draft.fields.size > 1,
                    onChange = { updated -> draft = draft.withField(updated) },
                    onMoveUp = {
                        draft = draft.reorder(
                            draft.fields.map { it.slot }.toMutableList().apply {
                                add(index - 1, removeAt(index))
                            },
                        )
                    },
                    onMoveDown = {
                        draft = draft.reorder(
                            draft.fields.map { it.slot }.toMutableList().apply {
                                add(index + 1, removeAt(index))
                            },
                        )
                    },
                    onRemove = { draft = draft.without(field.slot) },
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xs))
            }

            if (freeSlots.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
                ) {
                    freeSlots.forEach { slot ->
                        ActionChip(
                            stringResource(
                                R.string.notes_template_add,
                                NoteTemplate.defaultLabelFor(slot),
                            ),
                        ) {
                            draft = draft.withField(
                                NoteTemplateField(
                                    slot = slot,
                                    label = NoteTemplate.defaultLabelFor(slot),
                                ),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
            }

            if (!valid) {
                val reason = if (draft.fields.isEmpty()) {
                    stringResource(R.string.notes_template_invalid_empty)
                } else {
                    stringResource(R.string.notes_template_invalid_label)
                }
                Text(
                    text = reason,
                    style = NexusTheme.typography.dataSmall,
                    color = colors.danger,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xs))
            }

            NexusDivider()
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs)) {
                ActionChip(stringResource(R.string.notes_template_reset)) {
                    draft = NoteTemplate.DEFAULT
                }
                ActionChip(stringResource(R.string.notes_template_preset)) {
                    draft = NoteTemplate.PRESET_ROOT_CAUSE
                }
                Spacer(modifier = Modifier.weight(1f))
                ActionChip(
                    label = stringResource(R.string.notes_template_cancel),
                    danger = false,
                    onClick = onDismiss,
                )
                Box(
                    modifier = Modifier
                        .background(
                            if (valid) colors.surface else colors.background,
                            NexusRadius.sm,
                        )
                        .border(
                            1.dp,
                            if (valid) colors.accent else colors.border,
                            NexusRadius.sm,
                        )
                        .then(
                            if (valid) {
                                Modifier.clickable(role = Role.Button) { onSave(draft.sanitize()) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = NexusSpacing.sm, vertical = NexusSpacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.notes_template_save),
                        style = NexusTheme.typography.dataSmall,
                        color = if (valid) colors.accent else colors.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateFieldRow(
    field: NoteTemplateField,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onChange: (NoteTemplateField) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = NexusTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, NexusRadius.sm)
            .border(1.dp, colors.border, NexusRadius.sm)
            .padding(NexusSpacing.sm),
    ) {
        DialogField(
            label = stringResource(R.string.notes_template_field_label),
            value = field.label,
            onValueChange = { onChange(field.copy(label = it)) },
        )
        Spacer(modifier = Modifier.height(NexusSpacing.xs))
        DialogField(
            label = stringResource(R.string.notes_template_field_hint),
            value = field.hint,
            onValueChange = { onChange(field.copy(hint = it)) },
        )
        Spacer(modifier = Modifier.height(NexusSpacing.xs))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
        ) {
            NexusTag(
                text = NoteTemplate.defaultLabelFor(field.slot),
                tone = NexusTone.Neutral,
            )
            Spacer(modifier = Modifier.weight(1f))
            TemplateIconAction(
                symbol = "↑",
                description = stringResource(R.string.notes_template_move_up),
                enabled = canMoveUp,
                onClick = onMoveUp,
            )
            TemplateIconAction(
                symbol = "↓",
                description = stringResource(R.string.notes_template_move_down),
                enabled = canMoveDown,
                onClick = onMoveDown,
            )
            TemplateIconAction(
                symbol = "×",
                description = stringResource(R.string.notes_template_remove),
                enabled = canRemove,
                onClick = onRemove,
            )
        }
    }
}

@Composable
private fun TemplateIconAction(
    symbol: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = NexusTheme.colors
    Box(
        modifier = Modifier
            .size(IconTouchSize)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = description,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = NexusTheme.typography.data,
            color = if (enabled) colors.accent else colors.textTertiary,
        )
    }
}

@Composable
private fun AddAttemptDialog(
    onConfirm: (Verdict, Int?, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var verdict by remember { mutableStateOf(Verdict.WA) }
    var duration by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusTheme.colors.surface,
        titleContentColor = NexusTheme.colors.textPrimary,
        textContentColor = NexusTheme.colors.textSecondary,
        title = { Text(text = stringResource(R.string.attempt_add), style = NexusTheme.typography.title) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.attempt_verdict),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xxs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxxs),
                ) {
                    Verdict.entries.forEach { v ->
                        NexusTag(
                            text = stringResource(v.labelRes()),
                            tone = v.tone(),
                            selected = verdict == v,
                            modifier = Modifier.clickable(role = Role.Button) { verdict = v },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                DialogField(
                    label = stringResource(R.string.attempt_duration),
                    value = duration,
                    onValueChange = { duration = it },
                    keyboardType = KeyboardType.Number,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xxs))
                DialogField(
                    label = stringResource(R.string.attempt_language),
                    value = language,
                    onValueChange = { language = it },
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xxs))
                DialogField(
                    label = stringResource(R.string.attempt_note),
                    value = note,
                    onValueChange = { note = it },
                )
            }
        },
        confirmButton = {
            DialogTextButton(stringResource(R.string.action_save), NexusTheme.colors.accent) {
                onConfirm(verdict, duration.trim().toIntOrNull(), language.trim(), note.trim())
            }
        },
        dismissButton = {
            DialogTextButton(stringResource(R.string.action_cancel), NexusTheme.colors.textSecondary) { onDismiss() }
        },
    )
}

@Composable
private fun AddFailureDialog(
    onConfirm: (FailureCategory, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var category by remember { mutableStateOf(FailureCategory.THINKING) }
    var description by remember { mutableStateOf("") }
    val descriptionValid = description.isNotBlank()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusTheme.colors.surface,
        titleContentColor = NexusTheme.colors.textPrimary,
        textContentColor = NexusTheme.colors.textSecondary,
        title = { Text(text = stringResource(R.string.failure_add), style = NexusTheme.typography.title) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.failure_category),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xxs))
                Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.xxxs)) {
                    FailureCategory.entries.chunked(3).forEach { rowCategories ->
                        Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxxs)) {
                            rowCategories.forEach { c ->
                                NexusTag(
                                    text = stringResource(c.labelRes()),
                                    tone = c.tone(),
                                    selected = category == c,
                                    modifier = Modifier.clickable(role = Role.Button) { category = c },
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                DialogField(
                    label = stringResource(R.string.failure_description),
                    value = description,
                    onValueChange = { description = it },
                )
                if (!descriptionValid) {
                    Spacer(modifier = Modifier.height(NexusSpacing.xxxs))
                    Text(
                        text = stringResource(R.string.failure_description_required),
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.danger,
                    )
                }
            }
        },
        confirmButton = {
            DialogTextButton(
                stringResource(R.string.action_save),
                if (descriptionValid) NexusTheme.colors.accent else NexusTheme.colors.textTertiary,
                enabled = descriptionValid,
            ) {
                onConfirm(category, description.trim())
            }
        },
        dismissButton = {
            DialogTextButton(stringResource(R.string.action_cancel), NexusTheme.colors.textSecondary) { onDismiss() }
        },
    )
}

@Composable
private fun DialogTextButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = NexusTheme.typography.data,
        color = if (enabled) color else NexusTheme.colors.textTertiary,
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button) { onClick() }
            .padding(NexusSpacing.xs),
    )
}

@Composable
private fun DialogField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = NexusTheme.colors
    Column {
        Text(
            text = label,
            style = NexusTheme.typography.sectionLabel,
            color = colors.textTertiary,
        )
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
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
                textStyle = NexusTheme.typography.dataSmall.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
