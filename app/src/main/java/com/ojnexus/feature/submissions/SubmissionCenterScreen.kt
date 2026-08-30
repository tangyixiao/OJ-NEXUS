package com.ojnexus.feature.submissions

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojnexus.R
import com.ojnexus.core.database.entity.SubmissionJobEntity
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.formatDateTime
import com.ojnexus.judge.luogu.open.SubmissionJobKind
import com.ojnexus.judge.luogu.open.SubmissionJobStatus

@Composable
fun SubmissionCenterScreen(
    onBack: () -> Unit,
    onOpenWorkspace: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<SubmissionCenterViewModel>(
        factory = ContainerViewModelFactory(container) {
            SubmissionCenterViewModel(it.luoguSubmissionRepository)
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
                onOpenWorkspace = onOpenWorkspace,
            )
        }
    }
}

@Composable
private fun SubmissionCenterContent(
    state: SubmissionCenterUiState,
    onCheckResult: (String) -> Unit,
    onOpenWorkspace: (String) -> Unit,
) {
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
        if (state.jobs.isEmpty()) {
            NexusSection(label = stringResource(R.string.submissions_section_recent)) {
                Text(
                    text = stringResource(R.string.submissions_empty),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                )
            }
            Spacer(Modifier.padding(top = NexusSpacing.xxl))
            return
        }

        NexusSection(label = stringResource(R.string.submissions_section_recent)) {
            state.jobs.forEachIndexed { index, job ->
                SubmissionJobCard(
                    job = job,
                    busy = job.requestId in state.busyRequestIds,
                    onCheckResult = onCheckResult,
                    onOpenWorkspace = onOpenWorkspace,
                )
                if (index != state.jobs.lastIndex) {
                    NexusDivider(insetEnd = NexusSpacing.xxs)
                }
            }
        }
        Spacer(Modifier.padding(top = NexusSpacing.xxl))
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
    onCheckResult: (String) -> Unit,
    onOpenWorkspace: (String) -> Unit,
) {
    val pidValue = job.pid?.takeIf { it.isNotBlank() } ?: stringResource(R.string.problems_no_value)
    val canOpenWorkspace = job.kind == SubmissionJobKind.PROBLEM.name && job.pid?.isNotBlank() == true
    val canCheckResult = job.status == SubmissionJobStatus.PENDING.name || job.status == SubmissionJobStatus.FAILED.name
    val rowDescription = stringResource(
        R.string.submissions_row_cd,
        kindLabel(job.kind),
        pidValue,
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
                        text = it,
                        style = NexusTheme.typography.data,
                        color = NexusTheme.colors.textPrimary,
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
            }
            if (canOpenWorkspace) {
                SubmissionActionTag(
                    label = stringResource(R.string.workspace_open),
                    onClickLabel = stringResource(
                        R.string.submissions_open_workspace_cd,
                        job.pid.orEmpty(),
                    ),
                    enabled = true,
                    onClick = { onOpenWorkspace(job.pid.orEmpty()) },
                )
            }
        }
    }
}

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
