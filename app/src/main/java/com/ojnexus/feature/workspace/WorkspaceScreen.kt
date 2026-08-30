package com.ojnexus.feature.workspace

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.judge.luogu.open.LuoguOpenEvaluation

private val EditorMinHeight = 240.dp
private val InputMinHeight = 96.dp

@Composable
fun WorkspaceScreen(
    pid: String,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<WorkspaceViewModel>(
        key = "workspace-$pid",
        factory = ContainerViewModelFactory(container) {
            WorkspaceViewModel(
                pid = pid,
                title = null,
                gateway = it.luoguSubmissionRepository,
                credentialStore = it.luoguOpenCredentialStore,
                history = it.luoguSubmissionRepository,
            )
        },
    )
    val state = viewModel.state.collectAsStateWithLifecycle().value

    Column(Modifier.fillMaxSize().background(NexusTheme.colors.background)) {
        NexusTopBar(
            title = stringResource(R.string.workspace_title),
            trailing = {
                Text(
                    text = stringResource(R.string.action_back),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.accent,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onBack),
                )
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = NexusSpacing.screenHorizontal),
        ) {
            Spacer(Modifier.height(NexusSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                NexusTag(
                    text = stringResource(R.string.workspace_luogu),
                    tone = com.ojnexus.core.designsystem.NexusTone.Accent,
                    selected = true,
                )
                Text(state.pid, style = NexusTheme.typography.dataLarge, color = NexusTheme.colors.accent)
            }
            Spacer(Modifier.height(NexusSpacing.sm))
            NexusSection(label = stringResource(R.string.workspace_mode)) {
                Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                    WorkspaceAction(
                        label = stringResource(R.string.workspace_mode_run),
                        selected = state.mode == WorkspaceMode.RUN,
                    ) { viewModel.setMode(WorkspaceMode.RUN) }
                    WorkspaceAction(
                        label = stringResource(R.string.workspace_mode_submit),
                        selected = state.mode == WorkspaceMode.SUBMIT,
                    ) { viewModel.setMode(WorkspaceMode.SUBMIT) }
                }
                Spacer(Modifier.height(NexusSpacing.xs))
                Text(
                    text = stringResource(
                        if (state.mode == WorkspaceMode.RUN) {
                            R.string.workspace_mode_run_hint
                        } else {
                            R.string.workspace_mode_submit_hint
                        },
                    ),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                )
            }
            Spacer(Modifier.height(NexusSpacing.md))
            NexusSection(label = stringResource(R.string.workspace_code)) {
                CodeField(
                    value = state.code,
                    onValueChange = viewModel::setCode,
                    placeholder = stringResource(R.string.workspace_code_hint),
                )
            }
            if (state.mode == WorkspaceMode.RUN) {
                Spacer(Modifier.height(NexusSpacing.md))
                NexusSection(label = stringResource(R.string.workspace_input)) {
                    CodeField(
                        value = state.input,
                        onValueChange = viewModel::setInput,
                        placeholder = stringResource(R.string.workspace_input_hint),
                        minHeight = InputMinHeight,
                    )
                }
            }
            Spacer(Modifier.height(NexusSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                WorkspaceAction(
                    label = stringResource(if (state.busy) R.string.workspace_working else R.string.workspace_execute),
                    enabled = state.credentialConfigured && state.code.isNotBlank() && !state.busy,
                    selected = true,
                    onClick = viewModel::submit,
                )
                state.requestId?.let {
                    WorkspaceAction(
                        label = stringResource(R.string.workspace_check_result),
                        enabled = !state.busy,
                        onClick = viewModel::checkResult,
                    )
                }
            }
            if (!state.credentialConfigured) {
                Spacer(Modifier.height(NexusSpacing.xs))
                Text(
                    text = stringResource(R.string.workspace_credential_required),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.warning,
                )
            }
            state.error?.let { error ->
                Spacer(Modifier.height(NexusSpacing.xs))
                Text(
                    text = stringResource(error.labelRes),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.danger,
                )
            }
            state.requestId?.let { requestId ->
                Spacer(Modifier.height(NexusSpacing.md))
                NexusSection(label = stringResource(R.string.workspace_result)) {
                    Text(
                        text = stringResource(R.string.workspace_request_id, requestId),
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.textTertiary,
                    )
                    Spacer(Modifier.height(NexusSpacing.xs))
                    when (state.resultState) {
                        WorkspaceResultState.PENDING -> NexusTag(
                            text = stringResource(R.string.workspace_result_pending),
                            tone = com.ojnexus.core.designsystem.NexusTone.Warning,
                            selected = true,
                        )
                        WorkspaceResultState.READY -> state.evaluation?.let { EvaluationContent(it) }
                        WorkspaceResultState.IDLE -> Unit
                    }
                }
            }
            Spacer(Modifier.height(NexusSpacing.xxl))
        }
    }
}

@Composable
private fun CodeField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minHeight: Dp = EditorMinHeight,
) {
    val colors = NexusTheme.colors
    Box(
        Modifier.fillMaxWidth()
            .heightIn(min = minHeight)
            .background(colors.surface, NexusRadius.sm)
            .border(NexusSize.dividerThickness, colors.border, NexusRadius.sm)
            .padding(NexusSpacing.xs),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = NexusTheme.typography.dataSmall, color = colors.textTertiary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = NexusTheme.typography.dataSmall.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WorkspaceAction(
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = NexusTheme.colors
    Text(
        text = label,
        style = NexusTheme.typography.dataSmall,
        color = when {
            !enabled -> colors.textTertiary
            selected -> colors.accent
            else -> colors.textSecondary
        },
        modifier = Modifier
            .background(if (selected) colors.accentContainer else colors.surface, NexusRadius.sm)
            .border(NexusSize.dividerThickness, if (selected) colors.accent else colors.border, NexusRadius.sm)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = NexusSpacing.sm, vertical = NexusSpacing.xs),
    )
}

@Composable
private fun EvaluationContent(evaluation: LuoguOpenEvaluation) {
    val colors = NexusTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
        evaluation.status?.let { status ->
            Text(
                text = stringResource(R.string.workspace_status, status),
                style = NexusTheme.typography.data,
                color = if (status == 12) colors.success else colors.warning,
            )
        }
        evaluation.score?.let { Text(stringResource(R.string.workspace_score, it), style = NexusTheme.typography.dataSmall, color = colors.textSecondary) }
        evaluation.timeMs?.let { Text(stringResource(R.string.workspace_time, it), style = NexusTheme.typography.dataSmall, color = colors.textSecondary) }
        evaluation.memoryKiB?.let { Text(stringResource(R.string.workspace_memory, it), style = NexusTheme.typography.dataSmall, color = colors.textSecondary) }
        evaluation.exitCode?.let { Text(stringResource(R.string.workspace_exit_code, it), style = NexusTheme.typography.dataSmall, color = colors.textSecondary) }
        evaluation.compileMessage?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.workspace_compile_message), style = NexusTheme.typography.sectionLabel, color = colors.textTertiary)
            Text(it, style = NexusTheme.typography.dataSmall, color = colors.textSecondary)
        }
        evaluation.output?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.workspace_output), style = NexusTheme.typography.sectionLabel, color = colors.textTertiary)
            Text(it, style = NexusTheme.typography.dataSmall, color = colors.textSecondary)
        }
    }
}

private val WorkspaceError.labelRes: Int
    get() = when (this) {
        WorkspaceError.CREDENTIAL_MISSING -> R.string.workspace_error_credential
        WorkspaceError.INVALID_REQUEST -> R.string.workspace_error_invalid
        WorkspaceError.UNAUTHORIZED -> R.string.workspace_error_unauthorized
        WorkspaceError.FORBIDDEN -> R.string.workspace_error_forbidden
        WorkspaceError.QUOTA_EXCEEDED -> R.string.workspace_error_quota
        WorkspaceError.NOT_FOUND -> R.string.workspace_error_not_found
        WorkspaceError.NETWORK -> R.string.workspace_error_network
        WorkspaceError.SERVER -> R.string.workspace_error_server
    }
