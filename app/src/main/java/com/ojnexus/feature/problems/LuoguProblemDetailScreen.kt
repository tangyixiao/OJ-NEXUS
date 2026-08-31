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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.UrlOpener
import com.ojnexus.judge.luogu.LuoguMarkdownBlock
import com.ojnexus.judge.luogu.LuoguMarkdownParser
import com.ojnexus.judge.luogu.LuoguProblemDetail
import com.ojnexus.judge.luogu.LuoguUrls

@Composable
fun LuoguProblemDetailScreen(
    pid: String,
    onBack: () -> Unit,
    onOpenWorkspace: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<LuoguProblemDetailViewModel>(
        key = "luogu-problem-detail-$pid",
        factory = ContainerViewModelFactory(container) {
            LuoguProblemDetailViewModel(pid, it.luoguProblemDetailRepository)
        },
    )
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        NexusTopBar(
            title = stringResource(R.string.luogu_problem_detail_title),
            trailing = {
                Text(
                    text = stringResource(R.string.action_back),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.accent,
                    modifier = Modifier.clickable(onClick = onBack),
                )
            },
        )
        when (state) {
            Loadable.Loading -> DetailLoading()
            is Loadable.Failed -> DetailError(state.message)
            is Loadable.Ready -> LuoguDetailContent(
                detail = state.value,
                onOpenSource = { UrlOpener.open(context, LuoguUrls.problem(pid)) },
                onOpenWorkspace = { onOpenWorkspace(pid) },
            )
        }
    }
}

@Composable
private fun DetailLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.luogu_problem_loading),
            style = NexusTheme.typography.dataSmall,
            color = NexusTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun DetailError(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(NexusSpacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = NexusTheme.typography.data, color = NexusTheme.colors.danger)
    }
}

@Composable
private fun LuoguDetailContent(
    detail: LuoguProblemDetail,
    onOpenSource: () -> Unit,
    onOpenWorkspace: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexusSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.md),
    ) {
        Spacer(Modifier.height(NexusSpacing.xs))
        Text(detail.pid, style = NexusTheme.typography.dataLarge, color = NexusTheme.colors.accent)
        Text(detail.title, style = NexusTheme.typography.title, color = NexusTheme.colors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
            detail.difficulty?.let { NexusTag(text = stringResource(R.string.luogu_problem_difficulty, it), tone = NexusTone.Neutral) }
            detail.totalAccepted?.let { NexusTag(text = stringResource(R.string.luogu_problem_accepted, it), tone = NexusTone.Success) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.sm)) {
            Text(
                text = stringResource(R.string.luogu_problem_open_source),
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.accent,
                modifier = Modifier.clickable(onClick = onOpenSource).padding(vertical = NexusSpacing.xxs),
            )
            Text(
                text = stringResource(R.string.luogu_problem_workspace),
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.accent,
                modifier = Modifier.clickable(onClick = onOpenWorkspace).padding(vertical = NexusSpacing.xxs),
            )
        }
        NexusDivider()
        MarkdownSection(stringResource(R.string.luogu_problem_background), detail.background)
        MarkdownSection(stringResource(R.string.luogu_problem_description), detail.description)
        MarkdownSection(stringResource(R.string.luogu_problem_input), detail.inputFormat)
        MarkdownSection(stringResource(R.string.luogu_problem_output), detail.outputFormat)
        if (detail.samples.size >= 2) {
            NexusSection(label = stringResource(R.string.luogu_problem_samples)) {
                Text(stringResource(R.string.luogu_problem_sample_input), style = NexusTheme.typography.sectionLabel, color = NexusTheme.colors.textTertiary)
                CodeBlock(detail.samples[0])
                Text(stringResource(R.string.luogu_problem_sample_output), style = NexusTheme.typography.sectionLabel, color = NexusTheme.colors.textTertiary)
                CodeBlock(detail.samples[1])
            }
        }
        MarkdownSection(stringResource(R.string.luogu_problem_hint), detail.hint)
        if (detail.timeLimitMs != null || detail.memoryLimitMb != null) {
            NexusSection(label = stringResource(R.string.luogu_problem_limits)) {
                listOfNotNull(
                    detail.timeLimitMs?.let { stringResource(R.string.luogu_problem_time_limit, it) },
                    detail.memoryLimitMb?.let { stringResource(R.string.luogu_problem_memory_limit, it) },
                ).forEach { Text(it, style = NexusTheme.typography.dataSmall, color = NexusTheme.colors.textSecondary) }
            }
        }
        Spacer(Modifier.height(NexusSpacing.xxl))
    }
}

@Composable
private fun MarkdownSection(label: String, markdown: String) {
    if (markdown.isBlank()) return
    NexusSection(label = label) {
        LuoguMarkdownParser.parse(markdown).forEach { block ->
            when (block) {
                is LuoguMarkdownBlock.Heading -> Text(
                    text = block.text,
                    style = if (block.level == 1) NexusTheme.typography.title else NexusTheme.typography.label,
                    color = NexusTheme.colors.textPrimary,
                    modifier = Modifier.padding(vertical = NexusSpacing.xxs),
                )
                is LuoguMarkdownBlock.Paragraph -> Text(
                    text = block.text,
                    style = NexusTheme.typography.body,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = NexusSpacing.xxs),
                )
                is LuoguMarkdownBlock.Bullet -> Text(
                    text = "• ${block.text}",
                    style = NexusTheme.typography.body,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.padding(start = NexusSpacing.xs, top = NexusSpacing.xxxs, bottom = NexusSpacing.xxxs),
                )
                is LuoguMarkdownBlock.Quote -> Text(
                    text = block.text,
                    style = NexusTheme.typography.body,
                    color = NexusTheme.colors.textTertiary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(NexusSpacing.xxxs, NexusTheme.colors.borderStrong, NexusRadius.xs)
                        .padding(NexusSpacing.xs),
                )
                is LuoguMarkdownBlock.Code -> CodeBlock(block.text)
                LuoguMarkdownBlock.Divider -> NexusDivider(modifier = Modifier.padding(vertical = NexusSpacing.xs))
            }
        }
    }
}

@Composable
private fun CodeBlock(text: String) {
    Text(
        text = text,
        style = NexusTheme.typography.dataSmall,
        color = NexusTheme.colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .background(NexusTheme.colors.surface, NexusRadius.xs)
            .padding(NexusSpacing.sm),
    )
}
