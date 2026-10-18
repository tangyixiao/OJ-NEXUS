package com.ojnexus.feature.luogu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.judge.luogu.api.dto.LuoguTrainingProblemDto

/**
 * Ordered problem ladder of one PUBLIC Luogu training. Tapping a row jumps to the local
 * Luogu problem detail; nothing here is editable and no credential flow is involved.
 */
@Composable
fun LuoguTrainingDetailScreen(
    trainingId: Long,
    onBack: () -> Unit,
    onOpenLuoguProblem: (pid: String) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<LuoguTrainingDetailViewModel>(
        key = "training-$trainingId",
        factory = ContainerViewModelFactory(container) {
            LuoguTrainingDetailViewModel(it.luoguTrainingRepository, trainingId)
        },
    )
    val state by viewModel.detail.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        NexusTopBar(
            title = stringResource(R.string.luogu_training_title),
            trailing = {
                Text(
                    text = stringResource(R.string.action_back),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.accent,
                    modifier = Modifier.clickable(onClick = onBack),
                )
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexusSpacing.screenHorizontal),
        ) {
            Spacer(modifier = Modifier.height(NexusSpacing.md))
            when (val current = state) {
                LuoguTrainingUiState.Loading ->
                    StateLine(stringResource(R.string.luogu_training_loading))
                LuoguTrainingUiState.Empty ->
                    StateLine(stringResource(R.string.luogu_training_empty))
                is LuoguTrainingUiState.Failed ->
                    StateLine(
                        if (current.notFound) {
                            stringResource(R.string.luogu_training_missing)
                        } else {
                            stringResource(R.string.luogu_training_failed)
                        },
                    )
                is LuoguTrainingUiState.Ready -> {
                    Text(
                        text = current.value.name,
                        style = NexusTheme.typography.title,
                        color = NexusTheme.colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(NexusSpacing.xs))
                    Text(
                        text = stringResource(R.string.luogu_training_ladder_section),
                        style = NexusTheme.typography.sectionLabel,
                        color = NexusTheme.colors.textSecondary,
                        modifier = Modifier.padding(bottom = NexusSpacing.xs),
                    )
                    current.value.problems.forEachIndexed { index, problem ->
                        TrainingProblemRow(index, problem, onOpenLuoguProblem)
                        if (index != current.value.problems.lastIndex) NexusDivider()
                    }
                }
            }
            Spacer(modifier = Modifier.height(NexusSpacing.xxl))
        }
    }
}

@Composable
private fun TrainingProblemRow(
    position: Int,
    problem: LuoguTrainingProblemDto,
    onOpenLuoguProblem: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = problem.pid.isNotBlank(),
                onClickLabel = stringResource(R.string.luogu_training_problem_open_cd),
                role = androidx.compose.ui.semantics.Role.Button,
            ) { onOpenLuoguProblem(problem.pid) }
            .padding(vertical = NexusSpacing.sm),
    ) {
        Text(
            text = (position + 1).toString().padStart(2, '0'),
            style = NexusTheme.typography.dataSmall,
            color = NexusTheme.colors.textTertiary,
        )
        Spacer(modifier = Modifier.padding(NexusSpacing.xs))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trainingProblemTitle(problem),
                style = NexusTheme.typography.body,
                color = NexusTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    problem.pid,
                    problem.totalAccepted?.let {
                        stringResource(R.string.luogu_training_accepted_count, it)
                    },
                ).joinToString(" · "),
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.padding(NexusSpacing.xs))
        NexusTag(text = problem.pid, tone = NexusTone.Neutral)
    }
}