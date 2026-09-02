package com.ojnexus.feature.submissions

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojnexus.R
import com.ojnexus.core.database.entity.SubmissionJobEntity
import com.ojnexus.core.designsystem.NexusMotion
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusMetric
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.formatDateTime
import com.ojnexus.core.ui.formatCount
import com.ojnexus.judge.luogu.open.SubmissionJobKind
import com.ojnexus.judge.luogu.open.SubmissionJobStatus

internal data class SubmissionWorkspaceContext(
    val pid: String,
    val title: String?,
)

internal fun submissionWorkspaceContext(pid: String, title: String?): SubmissionWorkspaceContext =
    SubmissionWorkspaceContext(
        pid = pid,
        title = title?.trim()?.takeIf { it.isNotEmpty() },
    )

@Composable
fun SubmissionCenterScreen(
    onBack: () -> Unit,
    onOpenWorkspace: (String, String?) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<SubmissionCenterViewModel>(
        factory = ContainerViewModelFactory(container) {
            SubmissionCenterViewModel(
                submissionCenter = it.luoguSubmissionRepository,
                scheduler = it.luoguResultWorkScheduler,
            )
        },
    )
    val state = viewModel.state.collectAsStateWithLifecycle().value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        NexusTopBar(
            title = stringResource(R.string.submissions_title),
            trailing = {
                Text(
                    text = stringResource(R.string.action_back),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.accent,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onBack),
                )
            },
        )
        when (state) {
            Loadable.Loading -> SubmissionCenterMessage(stringResource(R.string.submissions_loading))
            is Loadable.Failed -> SubmissionCenterMessage(
                message = state.message.ifBlank { stringResource(R.string.submissions_database_error) },
                tone = NexusTone.Danger,
            )
            is Loadable.Ready -> SubmissionCenterContent(
                state = state.value,
                onCheckResult = viewModel::checkResult,
                onQueueRecovery = viewModel::queueRecovery,
                onOpenWorkspace = onOpenWorkspace,
            )
        }
    }
}

@Composable
private fun SubmissionCenterContent(
    state: SubmissionCenterUiState,
    onCheckResult: (String) -> Unit,
    onQueueRecovery: (String) -> Unit,
    onOpenWorkspace: (String, String?) -> Unit,
) {
    var statusFilter by rememberSaveable { mutableStateOf(SubmissionStatusFilter.ALL) }
    val summary = summarizeSubmissionCenter(state.jobs)
    val visibleJobs = filterSubmissionJobs(state.jobs, statusFilter)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexusSpacing.screenHorizontal),
    ) {
        Spacer(Modifier.padding(top = NexusSpacing.md))
        if (state.actionError != null) {
            NexusSection(label = stringResource(R.string.submissions_action_error_section)) {
                Text(
                    text = stringResource(
                        R.string.submissions_action_error_message,
                        state.actionError.requestId,
                    ),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.danger,
                )
            }
            Spacer(Modifier.padding(top = NexusSpacing.md))
        }
        SubmissionPulse(
            summary = summary,
            selected = statusFilter,
            onClear = { statusFilter = SubmissionStatusFilter.ALL },
        )
        Spacer(Modifier.padding(top = NexusSpacing.sm))
        SubmissionStatusControls(
            selected = statusFilter,
            onSelect = { statusFilter = it },
        )
        Spacer(Modifier.padding(top = NexusSpacing.xs))
        Column(
            modifier = Modifier.animateContentSize(
                animationSpec = if (NexusTheme.reduceMotion) snap() else tween(
                    NexusMotion.DURATION_NORMAL,
                    easing = NexusMotion.EasingStandard,
                ),
            ),
        ) {
            if (state.jobs.isEmpty()) {
                NexusSection(label = stringResource(R.string.submissions_section_recent)) {
                    Text(
                        text = stringResource(R.string.submissions_empty),
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.textTertiary,
                    )
                }
            } else if (visibleJobs.isEmpty()) {
                Text(
                    text = stringResource(R.string.submissions_filter_empty),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                    modifier = Modifier.padding(vertical = NexusSpacing.xs),
                )
            } else {
                NexusSection(label = stringResource(R.string.submissions_section_recent)) {
                    visibleJobs.forEachIndexed { index, job ->
                        SubmissionJobCard(
                            job = job,
                            busy = job.requestId in state.busyRequestIds,
                            queued = job.requestId in state.queuedRequestIds,
                            onCheckResult = onCheckResult,
                            onQueueRecovery = onQueueRecovery,
                            onOpenWorkspace = onOpenWorkspace,
                        )
                        if (index != visibleJobs.lastIndex) {
                            NexusDivider(insetEnd = NexusSpacing.xxs)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.padding(top = NexusSpacing.xxl))
    }
}

@Composable
private fun SubmissionPulse(
    summary: SubmissionCenterSummary,
    selected: SubmissionStatusFilter,
    onClear: () -> Unit,
) {
    val colors = NexusTheme.colors
    NexusSection(
        label = stringResource(R.string.submissions_section_pulse),
        trailing = if (selected == SubmissionStatusFilter.ALL) {
            null
        } else {
            {
                val description = stringResource(R.string.submissions_clear_filter_cd)
                Text(
                    text = stringResource(R.string.submissions_clear_filter),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.accent,
                    modifier = Modifier
                        .clickable(
                            role = Role.Button,
                            onClickLabel = description,
                            onClick = onClear,
                        )
                        .semantics { contentDescription = description },
                )
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
        ) {
            SubmissionPulseMetric(
                label = stringResource(R.string.submissions_pulse_total),
                value = summary.total,
                modifier = Modifier.weight(1f),
            )
            SubmissionPulseMetric(
                label = stringResource(R.string.submissions_pulse_pending),
                value = summary.pending,
                modifier = Modifier.weight(1f),
            )
            SubmissionPulseMetric(
                label = stringResource(R.string.submissions_pulse_ready),
                value = summary.ready,
                modifier = Modifier.weight(1f),
            )
            SubmissionPulseMetric(
                label = stringResource(R.string.submissions_pulse_failed),
                value = summary.failed,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SubmissionPulseMetric(
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
        label = "submission pulse $label",
    )
    NexusMetric(
        label = label,
        value = formatCount(animatedValue),
        modifier = modifier,
    )
}

@Composable
private fun SubmissionStatusControls(
    selected: SubmissionStatusFilter,
    onSelect: (SubmissionStatusFilter) -> Unit,
) {
    val filters = listOf(
        SubmissionStatusFilter.ALL to R.string.submissions_filter_all,
        SubmissionStatusFilter.PENDING to R.string.submissions_filter_pending,
        SubmissionStatusFilter.READY to R.string.submissions_filter_ready,
        SubmissionStatusFilter.FAILED to R.string.submissions_filter_failed,
    )
    val descriptions = mapOf(
        SubmissionStatusFilter.ALL to R.string.submissions_filter_all_cd,
        SubmissionStatusFilter.PENDING to R.string.submissions_filter_pending_cd,
        SubmissionStatusFilter.READY to R.string.submissions_filter_ready_cd,
        SubmissionStatusFilter.FAILED to R.string.submissions_filter_failed_cd,
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
private fun SubmissionCenterMessage(
    message: String,
    tone: NexusTone = NexusTone.Neutral,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NexusSpacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        NexusTag(text = message, tone = tone, selected = tone != NexusTone.Neutral)
    }
}

@Composable
private fun SubmissionJobCard(
    job: SubmissionJobEntity,
    busy: Boolean,
    queued: Boolean,
    onCheckResult: (String) -> Unit,
    onQueueRecovery: (String) -> Unit,
    onOpenWorkspace: (String, String?) -> Unit,
) {
    val pidValue = job.pid?.takeIf { it.isNotBlank() } ?: stringResource(R.string.problems_no_value)
    val titleValue = job.title?.trim()?.takeIf { it.isNotEmpty() }
    val problemDisplay = submissionProblemDisplay(pidValue, titleValue)
    val canOpenWorkspace = job.kind == SubmissionJobKind.PROBLEM.name && job.pid?.isNotBlank() == true
    val canCheckResult = job.status == SubmissionJobStatus.PENDING.name || job.status == SubmissionJobStatus.FAILED.name
    val rowDescription = stringResource(
        R.string.submissions_row_cd,
        kindLabel(job.kind),
        problemDisplay,
        job.requestId,
        statusLabel(job.status),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NexusSpacing.xs)
            .semantics { contentDescription = rowDescription },
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                NexusTag(
                    text = kindLabel(job.kind),
                    tone = if (job.kind == SubmissionJobKind.PROBLEM.name) NexusTone.Accent else NexusTone.Neutral,
                    selected = job.kind == SubmissionJobKind.PROBLEM.name,
                )
                job.pid?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = problemDisplay,
                        style = NexusTheme.typography.data,
                        color = NexusTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            NexusTag(
                text = statusLabel(job.status),
                tone = statusTone(job.status),
                selected = true,
            )
        }

        SubmissionMetadataLine(stringResource(R.string.submissions_kind), kindLabel(job.kind))
        SubmissionMetadataLine(stringResource(R.string.submissions_pid), pidValue)
        titleValue?.let {
            SubmissionMetadataLine(stringResource(R.string.submissions_problem_title), it)
        }
        SubmissionMetadataLine(stringResource(R.string.submissions_language), job.language)
        SubmissionMetadataLine(stringResource(R.string.submissions_request_id), job.requestId)
        SubmissionMetadataLine(
            stringResource(R.string.submissions_time),
            formatDateTime(job.updatedAt),
        )
        SubmissionMetadataLine(stringResource(R.string.submissions_status), statusLabel(job.status))
        job.score?.let {
            SubmissionMetadataLine(
                stringResource(R.string.submissions_score),
                stringResource(R.string.submissions_score_value, it),
            )
        }
        job.judgeStatus?.let {
            SubmissionMetadataLine(
                stringResource(R.string.submissions_judge_status),
                stringResource(R.string.submissions_judge_status_value, it),
            )
        }
        job.compileSuccess?.let {
            SubmissionMetadataLine(
                stringResource(R.string.submissions_compile),
                stringResource(
                    if (it) R.string.submissions_compile_success else R.string.submissions_compile_failed,
                ),
            )
        }
        job.compileMessage?.takeIf { it.isNotBlank() }?.let {
            SubmissionMetadataLine(stringResource(R.string.submissions_compile_message), it)
        }
        job.output?.takeIf { it.isNotBlank() }?.let {
            SubmissionMetadataLine(stringResource(R.string.submissions_output), it)
        }
        job.exitCode?.let {
            SubmissionMetadataLine(
                stringResource(R.string.submissions_exit_code),
                stringResource(R.string.submissions_exit_code_value, it),
            )
        }
        job.executionTimeMs?.let {
            SubmissionMetadataLine(
                stringResource(R.string.submissions_execution_time),
                stringResource(R.string.submissions_execution_time_value, it),
            )
        }
        job.memoryKiB?.let {
            SubmissionMetadataLine(
                stringResource(R.string.submissions_memory),
                stringResource(R.string.submissions_memory_value, it),
            )
        }
        job.lastErrorType?.takeIf { it.isNotBlank() }?.let {
            SubmissionMetadataLine(stringResource(R.string.submissions_error), it)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
            if (canCheckResult) {
                SubmissionActionTag(
                    label = stringResource(
                        if (busy) R.string.submissions_checking_result else R.string.submissions_check_result,
                    ),
                    onClickLabel = stringResource(R.string.submissions_check_result_cd, job.requestId),
                    enabled = !busy,
                    onClick = { onCheckResult(job.requestId) },
                )
                SubmissionActionTag(
                    label = stringResource(
                        when {
                            queued -> R.string.submissions_queue_requested
                            job.status == SubmissionJobStatus.FAILED.name -> R.string.submissions_queue_retry
                            else -> R.string.submissions_queue_check
                        },
                    ),
                    onClickLabel = stringResource(R.string.submissions_queue_result_cd, job.requestId),
                    enabled = true,
                    onClick = { onQueueRecovery(job.requestId) },
                )
            }
            if (canOpenWorkspace) {
                SubmissionActionTag(
                    label = stringResource(R.string.workspace_open),
                    onClickLabel = stringResource(
                        R.string.submissions_open_workspace_cd,
                        job.pid.orEmpty(),
                    ),
                    enabled = true,
                    onClick = {
                        val context = submissionWorkspaceContext(job.pid.orEmpty(), job.title)
                        onOpenWorkspace(context.pid, context.title)
                    },
                )
            }
        }
    }
}

internal fun submissionProblemDisplay(pid: String, title: String?): String =
    title?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it · $pid" } ?: pid

@Composable
private fun SubmissionMetadataLine(
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
        Text(
            text = label,
            style = NexusTheme.typography.sectionLabel,
            color = NexusTheme.colors.textTertiary,
        )
        Text(
            text = value,
            style = NexusTheme.typography.dataSmall,
            color = NexusTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun SubmissionActionTag(
    label: String,
    onClickLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    NexusTag(
        text = label,
        tone = if (enabled) NexusTone.Accent else NexusTone.Neutral,
        selected = enabled,
        modifier = Modifier.clickable(
            enabled = enabled,
            role = Role.Button,
            onClickLabel = onClickLabel,
            onClick = onClick,
        ),
    )
}

@Composable
private fun kindLabel(kind: String): String = stringResource(
    when (kind) {
        SubmissionJobKind.PROBLEM.name -> R.string.submissions_kind_problem
        SubmissionJobKind.RUN.name -> R.string.submissions_kind_run
        else -> R.string.submissions_kind_unknown
    },
)

@Composable
private fun statusLabel(status: String): String = stringResource(
    when (status) {
        SubmissionJobStatus.PENDING.name -> R.string.submissions_status_pending
        SubmissionJobStatus.READY.name -> R.string.submissions_status_ready
        SubmissionJobStatus.FAILED.name -> R.string.submissions_status_failed
        else -> R.string.submissions_status_unknown
    },
)

private fun statusTone(status: String): NexusTone = when (status) {
    SubmissionJobStatus.PENDING.name -> NexusTone.Warning
    SubmissionJobStatus.READY.name -> NexusTone.Success
    SubmissionJobStatus.FAILED.name -> NexusTone.Danger
    else -> NexusTone.Neutral
}
