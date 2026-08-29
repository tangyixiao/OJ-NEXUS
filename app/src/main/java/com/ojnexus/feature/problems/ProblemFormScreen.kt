package com.ojnexus.feature.problems

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojnexus.R
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar

/** Ime action for the last field: [android.view.inputmethod.EditorInfo].IME_ACTION_DONE wiring
 *  is handled by the keyboard type/ime config of each [NexusFormField]. */
@Composable
fun ProblemFormScreen(
    editProblemId: Long?,
    onDone: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel =
        androidx.lifecycle.viewmodel.compose.viewModel<ProblemFormViewModel>(
            key = "problem-form-${editProblemId ?: "new"}",
            factory = ContainerViewModelFactory(container) {
                ProblemFormViewModel(it.problemRepository, editProblemId)
            },
        )
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Edit mode: pull current values once before first render of the fields.
    LaunchedEffect(editProblemId) {
        if (editProblemId != null) {
            viewModel.loadForEdit(onLoaded = {})
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        NexusTopBar(
            title = stringResource(
                if (editProblemId == null) R.string.add_problem_title else R.string.edit_problem_title,
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexusSpacing.screenHorizontal),
        ) {
            Spacer(modifier = Modifier.height(NexusSpacing.md))
            NexusSection(label = stringResource(R.string.field_judge)) {
                Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                    JudgeId.entries.forEach { judge ->
                        NexusTag(
                            text = judge.displayName,
                            tone = NexusTone.Accent,
                            selected = state.judge == judge,
                            modifier = Modifier.clickable(
                                role = Role.Button,
                            ) { viewModel.setJudge(judge) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(NexusSpacing.md))
            NexusDivider()
            Spacer(modifier = Modifier.height(NexusSpacing.md))

            NexusFormField(
                label = stringResource(R.string.field_problem_id),
                value = if (state.judge == JudgeId.LOCAL) "" else state.externalId,
                onValueChange = viewModel::setExternalId,
                enabled = state.judge != JudgeId.LOCAL,
                placeholder = if (state.judge == JudgeId.LOCAL) {
                    stringResource(R.string.field_problem_id_local_hint)
                } else {
                    ""
                },
            )
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            NexusFormField(
                label = stringResource(R.string.field_title),
                value = state.title,
                onValueChange = viewModel::setTitle,
            )
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            NexusFormField(
                label = stringResource(R.string.field_difficulty),
                value = state.difficulty,
                onValueChange = viewModel::setDifficulty,
                keyboardType = KeyboardType.Number,
            )
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            NexusFormField(
                label = stringResource(R.string.field_tags),
                value = state.tags,
                onValueChange = viewModel::setTags,
            )
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            NexusFormField(
                label = stringResource(R.string.field_url),
                value = state.sourceUrl,
                onValueChange = viewModel::setSourceUrl,
            )

            state.error?.let { error ->
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                Text(
                    text = errorLabel(error),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.danger,
                )
            }

            Spacer(modifier = Modifier.height(NexusSpacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FormAction(
                    label = stringResource(R.string.action_save),
                    enabled = state.canSave,
                    accent = true,
                    onClick = { viewModel.save(onDone = onDone) },
                )
                FormAction(
                    label = stringResource(R.string.action_cancel),
                    enabled = true,
                    accent = false,
                    onClick = onDone,
                )
                if (state.saving) {
                    Text(
                        text = stringResource(R.string.notes_saving),
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.textTertiary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(NexusSpacing.xxl))
        }
    }
}

@Composable
private fun errorLabel(error: FormError): String = when (error) {
    FormError.IdRequired -> stringResource(R.string.error_id_required)
    FormError.TitleRequired -> stringResource(R.string.error_title_required)
    is FormError.Duplicate -> stringResource(R.string.error_duplicate)
    FormError.DifficultyInvalid -> stringResource(R.string.error_difficulty_invalid)
    FormError.UrlInvalid -> stringResource(R.string.error_url_invalid)
    FormError.SaveFailed -> stringResource(R.string.error_generic)
}

/** Single-line labeled text field using design tokens only. */
@Composable
private fun NexusFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = NexusTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
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
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
                textStyle = NexusTheme.typography.data.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FormAction(label: String, enabled: Boolean, accent: Boolean, onClick: () -> Unit) {
    val colors = NexusTheme.colors
    val foreground = when {
        !enabled -> colors.textTertiary
        accent -> colors.accent
        else -> colors.textSecondary
    }
    Box(
        modifier = Modifier
            .background(colors.surface, NexusRadius.sm)
            .border(1.dp, if (accent && enabled) colors.accent else colors.border, NexusRadius.sm)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = NexusSpacing.md, vertical = NexusSpacing.xs),
    ) {
        Text(text = label, style = NexusTheme.typography.data, color = foreground)
    }
}
