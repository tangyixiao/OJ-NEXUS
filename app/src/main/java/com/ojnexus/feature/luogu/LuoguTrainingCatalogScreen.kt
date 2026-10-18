package com.ojnexus.feature.luogu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.ojnexus.judge.luogu.api.dto.LuoguTrainingSummaryDto

/**
 * Read-only catalog of Luogu's PUBLIC official trainings. No auth, no cookies; the payload
 * comes from the public training pages (see docs/superpowers/specs/2026-10-18-luogu-training-catalog.md).
 */
@Composable
fun LuoguTrainingCatalogScreen(
    onBack: () -> Unit,
    onOpenTraining: (id: Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<LuoguTrainingCatalogViewModel>(
        factory = ContainerViewModelFactory(container) {
            LuoguTrainingCatalogViewModel(it.luoguTrainingRepository)
        },
    )
    val state by viewModel.catalog.collectAsStateWithLifecycle()

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
                LuoguTrainingUiState.Loading -> StateLine(stringResource(R.string.luogu_training_loading))
                LuoguTrainingUiState.Empty -> StateLine(stringResource(R.string.luogu_training_empty))
                is LuoguTrainingUiState.Failed -> StateLine(stringResource(R.string.luogu_training_failed))
                is LuoguTrainingUiState.Ready -> TrainingCatalogRows(current.value, onOpenTraining)
            }
            Spacer(modifier = Modifier.height(NexusSpacing.xxl))
        }
    }
}

@Composable
internal fun StateLine(text: String) {
    Text(
        text = text,
        style = NexusTheme.typography.dataSmall,
        color = NexusTheme.colors.textTertiary,
        modifier = Modifier.padding(vertical = NexusSpacing.xs),
    )
}

@Composable
private fun TrainingCatalogRows(
    trainings: List<LuoguTrainingSummaryDto>,
    onOpenTraining: (Long) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.luogu_training_section),
            style = NexusTheme.typography.sectionLabel,
            color = NexusTheme.colors.textSecondary,
            modifier = Modifier.padding(bottom = NexusSpacing.xs),
        )
        trainings.forEachIndexed { index, training ->
            TrainingRow(training, onOpenTraining)
            if (index != trainings.lastIndex) NexusDivider()
        }
    }
}

@Composable
private fun TrainingRow(
    training: LuoguTrainingSummaryDto,
    onOpenTraining: (Long) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = training.id != 0L,
                onClickLabel = stringResource(R.string.luogu_training_open_cd),
                role = androidx.compose.ui.semantics.Role.Button,
            ) { onOpenTraining(training.id) }
            .padding(vertical = NexusSpacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = training.name,
                style = NexusTheme.typography.body,
                color = NexusTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = trainingCatalogContext(
                    training,
                    problemCountLabel = stringResource(R.string.luogu_training_problem_count, training.problemCount),
                ),
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.padding(NexusSpacing.xs))
        NexusTag(
            text = stringResource(R.string.luogu_training_open),
            tone = NexusTone.Accent,
        )
    }
}
