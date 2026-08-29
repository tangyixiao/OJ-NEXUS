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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojnexus.BuildConfig
import com.ojnexus.R
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Problem
import com.ojnexus.core.model.ProblemStatus
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.labelRes
import com.ojnexus.core.ui.tone
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar

// Library layout metrics.
private val ProblemRowHeight = 56.dp
private val RatingColumnWidth = 56.dp
private val StatusColumnWidth = 92.dp
private val IconTouchSize = 32.dp
private val RemoteProblemRowHeight = 82.dp

private enum class ProblemScope { LIBRARY, CODEFORCES }

@Composable
fun ProblemsScreen(
    onOpenProblem: (Long) -> Unit,
    onAddProblem: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ProblemsViewModel>(
        factory = ContainerViewModelFactory(container) {
            ProblemsViewModel(
                repository = it.problemRepository,
                demoSeeder = it.demoSeeder,
                syncRepository = it.codeforcesSyncRepository,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val remoteState by viewModel.remoteState.collectAsStateWithLifecycle()
    var scope by rememberSaveable { mutableStateOf(ProblemScope.LIBRARY) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        NexusTopBar(
            title = stringResource(R.string.nav_problems),
            trailing = {
                Text(
                    text = when (val s = state) {
                        is Loadable.Ready -> stringResource(R.string.problems_count, s.value.totalCount)
                        else -> ""
                    },
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                )
            },
        )
        when (val s = state) {
            Loadable.Loading -> LoadingState()
            is Loadable.Failed -> ErrorState(s.message)
            is Loadable.Ready -> if (scope == ProblemScope.LIBRARY) {
                LibraryContent(
                    uiState = s.value,
                    viewModel = viewModel,
                    onOpenProblem = onOpenProblem,
                    onAddProblem = onAddProblem,
                    onInsertDemo = { viewModel.insertDemoData() },
                    onClearDemo = { viewModel.clearDemoData() },
                    onOpenRemote = {
                        scope = ProblemScope.CODEFORCES
                        viewModel.enterRemoteCatalog()
                    },
                )
            } else {
                RemoteCatalogContent(
                    state = remoteState,
                    viewModel = viewModel,
                    onBackToLibrary = { scope = ProblemScope.LIBRARY },
                    onOpenProblem = onOpenProblem,
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.sync_state_syncing),
            style = NexusTheme.typography.dataSmall,
            color = NexusTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NexusSpacing.screenHorizontal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = NexusTheme.typography.data,
            color = NexusTheme.colors.danger,
        )
    }
}

@Composable
private fun LibraryContent(
    uiState: ProblemsUiState,
    viewModel: ProblemsViewModel,
    onOpenProblem: (Long) -> Unit,
    onAddProblem: () -> Unit,
    onInsertDemo: () -> Unit,
    onClearDemo: () -> Unit,
    onOpenRemote: () -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<Problem?>(null) }
    val colors = NexusTheme.colors

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "search") {
            Column(modifier = Modifier.padding(horizontal = NexusSpacing.screenHorizontal)) {
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                ScopeSwitcher(
                    selected = ProblemScope.LIBRARY,
                    onSelectRemote = onOpenRemote,
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xs))
                SearchField(
                    query = uiState.filter.query,
                    onQueryChange = viewModel::setQuery,
                    hintText = stringResource(R.string.problems_search_hint),
                )
                if (BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.height(NexusSpacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                        NexusTag(
                            text = stringResource(R.string.demo_insert),
                            tone = NexusTone.Accent,
                            selected = true,
                            modifier = Modifier.clickable(
                                role = Role.Button,
                            ) { onInsertDemo() },
                        )
                        NexusTag(
                            text = stringResource(R.string.demo_clear),
                            modifier = Modifier.clickable(
                                role = Role.Button,
                            ) { onClearDemo() },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(NexusSpacing.xs))
                FilterChipRow(
                    status = uiState.filter.status,
                    judge = uiState.filter.judge,
                    favoriteOnly = uiState.filter.favoriteOnly,
                    sort = uiState.sort,
                    onStatus = viewModel::setStatus,
                    onJudge = viewModel::setJudge,
                    onFavoriteOnly = viewModel::toggleFavoriteOnly,
                    onSort = viewModel::cycleSort,
                )
                if (uiState.allTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(NexusSpacing.xxs))
                    TagChipRow(
                        tags = uiState.allTags,
                        selected = uiState.filter.tag,
                        onSelect = viewModel::setTag,
                    )
                }
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.nav_problems),
                        style = NexusTheme.typography.sectionLabel,
                        color = colors.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    NexusTag(
                        text = stringResource(R.string.action_add_problem),
                        tone = NexusTone.Accent,
                        selected = true,
                        modifier = Modifier.clickable(role = Role.Button) { onAddProblem() },
                    )
                }
                Spacer(modifier = Modifier.height(NexusSpacing.xxs))
                NexusDivider()
            }
        }
        if (uiState.totalCount == 0) {
            item(key = "empty-library") {
                EmptyHint(
                    title = stringResource(R.string.problems_empty_title),
                    hint = stringResource(R.string.problems_empty_hint),
                )
            }
        } else if (uiState.problems.isEmpty()) {
            item(key = "no-match") {
                EmptyHint(
                    title = stringResource(R.string.problems_no_match),
                    hint = "",
                )
            }
        } else {
            items(items = uiState.problems, key = { it.id }) { problem ->
                ProblemRow(
                    problem = problem,
                    onClick = { onOpenProblem(problem.id) },
                    onToggleFavorite = { viewModel.toggleFavorite(problem.id, problem.favorite) },
                    onDelete = { deleteTarget = problem },
                )
                NexusDivider(insetEnd = NexusSpacing.xxs)
            }
        }
        item(key = "footer-space") {
            Spacer(modifier = Modifier.height(NexusSpacing.xxl))
        }
    }

    deleteTarget?.let { target ->
        DeleteProblemDialog(
            problem = target,
            onConfirm = {
                viewModel.deleteProblem(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun EmptyHint(title: String, hint: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NexusSpacing.screenHorizontal, vertical = NexusSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = NexusTheme.typography.data,
            color = NexusTheme.colors.textTertiary,
        )
        if (hint.isNotEmpty()) {
            Spacer(modifier = Modifier.height(NexusSpacing.xxs))
            Text(
                text = hint,
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun ScopeSwitcher(selected: ProblemScope, onSelectRemote: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
    ) {
        FilterChip(
            label = stringResource(R.string.problems_scope_library),
            selected = selected == ProblemScope.LIBRARY,
            onClick = {},
        )
        FilterChip(
            label = stringResource(R.string.problems_scope_codeforces),
            selected = selected == ProblemScope.CODEFORCES,
            onClick = onSelectRemote,
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hintText: String,
) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, NexusRadius.sm)
            .border(1.dp, colors.border, NexusRadius.sm)
            .padding(horizontal = NexusSpacing.sm, vertical = NexusSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "///",
            style = NexusTheme.typography.dataSmall,
            color = colors.accent,
            modifier = Modifier.padding(end = NexusSpacing.xs),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = hintText,
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = NexusTheme.typography.dataSmall.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Text(
                text = "×",
                style = NexusTheme.typography.data,
                color = colors.textTertiary,
                modifier = Modifier
                    .clickable(role = Role.Button) { onQueryChange("") }
                    .padding(start = NexusSpacing.xs),
            )
        }
    }
}

@Composable
private fun RemoteCatalogContent(
    state: RemoteProblemsUiState,
    viewModel: ProblemsViewModel,
    onBackToLibrary: () -> Unit,
    onOpenProblem: (Long) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "remote-controls") {
            Column(modifier = Modifier.padding(horizontal = NexusSpacing.screenHorizontal)) {
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                ScopeSwitcher(
                    selected = ProblemScope.CODEFORCES,
                    onSelectRemote = {},
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xs))
                SearchField(
                    query = state.query,
                    onQueryChange = viewModel::setRemoteQuery,
                    hintText = stringResource(R.string.problems_remote_search_hint),
                )
                Spacer(modifier = Modifier.height(NexusSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                    FilterChip(
                        label = stringResource(R.string.problems_scope_filter_all),
                        selected = state.solvedFilter == 0,
                        onClick = { viewModel.setRemoteSolvedFilter(0) },
                    )
                    FilterChip(
                        label = stringResource(R.string.problems_scope_filter_solved),
                        selected = state.solvedFilter == 1,
                        onClick = { viewModel.setRemoteSolvedFilter(1) },
                    )
                    FilterChip(
                        label = stringResource(R.string.problems_scope_filter_unsolved),
                        selected = state.solvedFilter == 2,
                        onClick = { viewModel.setRemoteSolvedFilter(2) },
                    )
                }
                Spacer(modifier = Modifier.height(NexusSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.problems_scope_codeforces),
                        style = NexusTheme.typography.sectionLabel,
                        color = NexusTheme.colors.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.problems_scope_library),
                        style = NexusTheme.typography.sectionLabel,
                        color = NexusTheme.colors.accent,
                        modifier = Modifier.clickable(role = Role.Button, onClick = onBackToLibrary),
                    )
                }
                Spacer(modifier = Modifier.height(NexusSpacing.xxs))
                NexusDivider()
                if (state.error != null) {
                    Text(
                        text = state.error,
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.danger,
                        modifier = Modifier.padding(vertical = NexusSpacing.xs),
                    )
                }
            }
        }
        if (state.loading && state.problems.isEmpty()) {
            item(key = "remote-loading") {
                LoadingState()
            }
        } else if (!state.loading && state.problems.isEmpty()) {
            item(key = "remote-empty") {
                EmptyHint(
                    title = stringResource(R.string.problems_remote_empty),
                    hint = "",
                )
            }
        } else {
            items(items = state.problems, key = { "remote-${it.externalId}" }) { problem ->
                RemoteProblemRow(
                    problem = problem,
                    addedProblemId = state.addedProblemIds[problem.externalId],
                    onAdd = { viewModel.addRemoteToLibrary(problem) },
                    onOpen = { id -> onOpenProblem(id) },
                )
                NexusDivider(insetEnd = NexusSpacing.xxs)
            }
            if (state.hasMore) {
                item(key = "remote-more") {
                    Text(
                        text = if (state.loading) {
                            stringResource(R.string.sync_state_syncing)
                        } else {
                            stringResource(R.string.problems_load_more)
                        },
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = !state.loading,
                                role = Role.Button,
                                onClick = viewModel::loadMoreRemote,
                            )
                            .padding(NexusSpacing.md),
                    )
                }
            }
        }
        item(key = "remote-footer-space") {
            Spacer(modifier = Modifier.height(NexusSpacing.xxl))
        }
    }
}

@Composable
private fun RemoteProblemRow(
    problem: com.ojnexus.core.database.entity.RemoteProblemEntity,
    addedProblemId: Long?,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RemoteProblemRowHeight)
            .padding(horizontal = NexusSpacing.screenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = problem.externalId,
                    style = NexusTheme.typography.data,
                    color = NexusTheme.colors.accent,
                )
                Text(
                    text = "  ${problem.name}",
                    style = NexusTheme.typography.data,
                    color = NexusTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = listOfNotNull(
                    problem.rating?.toString(),
                    problem.solvedCount?.let { "${it} AC" },
                    problem.tags.split('\u001F').filter { it.isNotBlank() }.take(2).joinToString(" · "),
                ).joinToString(" · ").ifEmpty { stringResource(R.string.problems_no_value) },
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        NexusTag(
            text = if (addedProblemId == null) {
                stringResource(R.string.problems_add_to_training)
            } else {
                stringResource(R.string.problems_in_library)
            },
            tone = if (addedProblemId == null) NexusTone.Accent else NexusTone.Success,
            selected = addedProblemId == null,
            modifier = Modifier.clickable(
                role = Role.Button,
                onClick = { if (addedProblemId == null) onAdd() else onOpen(addedProblemId) },
            ),
        )
    }
}

@Composable
private fun FilterChipRow(
    status: ProblemStatus?,
    judge: JudgeId?,
    favoriteOnly: Boolean,
    sort: ProblemSort,
    onStatus: (ProblemStatus?) -> Unit,
    onJudge: (JudgeId?) -> Unit,
    onFavoriteOnly: () -> Unit,
    onSort: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
        ) {
            FilterChip(
                label = stringResource(R.string.problems_filter_all),
                selected = status == null && !favoriteOnly,
                onClick = { onStatus(null) },
            )
            ProblemStatus.entries.forEach { s ->
                FilterChip(
                    label = stringResource(s.labelRes()),
                    selected = status == s,
                    onClick = { onStatus(if (status == s) null else s) },
                )
            }
            FilterChip(
                label = stringResource(R.string.problems_filter_favorite),
                selected = favoriteOnly,
                onClick = onFavoriteOnly,
            )
        }
        Spacer(modifier = Modifier.height(NexusSpacing.xxs))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
        ) {
            FilterChip(
                label = stringResource(R.string.problems_filter_all),
                selected = judge == null,
                onClick = { onJudge(null) },
            )
            JudgeId.entries.forEach { j ->
                FilterChip(
                    label = j.displayName,
                    selected = judge == j,
                    onClick = { onJudge(if (judge == j) null else j) },
                )
            }
            FilterChip(
                label = stringResource(sort.labelRes),
                selected = true,
                onClick = onSort,
                leading = stringResource(R.string.problems_sort_label),
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: String? = null,
) {
    val colors = NexusTheme.colors
    val foreground = if (selected) colors.accent else colors.textSecondary
    Row(
        modifier = Modifier
            .background(
                if (selected) colors.accentContainer else colors.surface,
                NexusRadius.xs,
            )
            .border(
                1.dp,
                if (selected) colors.accent else colors.border,
                NexusRadius.xs,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = NexusSpacing.xs, vertical = NexusSpacing.xxxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(
                text = leading,
                style = NexusTheme.typography.dataSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(end = NexusSpacing.xxxs),
            )
        }
        Text(
            text = label,
            style = NexusTheme.typography.dataSmall,
            color = foreground,
        )
    }
}

@Composable
private fun TagChipRow(tags: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
        items(items = tags, key = { it }) { tag ->
            FilterChip(
                label = tag,
                selected = selected == tag,
                onClick = { onSelect(if (selected == tag) null else tag) },
            )
        }
    }
}

@Composable
private fun ProblemRow(
    problem: Problem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ProblemRowHeight)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = problem.key.judge.displayName,
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(end = NexusSpacing.xxs),
                )
                Text(
                    text = problem.key.externalId,
                    style = NexusTheme.typography.data,
                    color = colors.accent,
                )
            }
            Text(
                text = problem.title,
                style = NexusTheme.typography.label,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Favorite diamond: filled accent when set, hairline otherwise. Not emoji-based.
        Box(
            modifier = Modifier
                .size(IconTouchSize)
                .clickable(role = Role.Button, onClick = onToggleFavorite)
                .padding(8.dp)
                .background(
                    if (problem.favorite) colors.accent else colors.surface,
                    RoundedCornerShape(2.dp),
                )
                .border(1.dp, if (problem.favorite) colors.accent else colors.borderStrong, RoundedCornerShape(2.dp)),
        )
        Text(
            text = problem.difficulty?.toString() ?: stringResource(R.string.problems_no_value),
            style = NexusTheme.typography.dataSmall,
            color = colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(RatingColumnWidth),
        )
        Row(
            modifier = Modifier
                .width(StatusColumnWidth)
                .padding(start = NexusSpacing.xxs),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NexusTag(
                text = stringResource(problem.status.labelRes()),
                tone = problem.status.tone(),
            )
            Box(
                modifier = Modifier
                    .size(IconTouchSize)
                    .clickable(role = Role.Button, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    style = NexusTheme.typography.data,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun DeleteProblemDialog(
    problem: Problem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusTheme.colors.surface,
        titleContentColor = NexusTheme.colors.textPrimary,
        textContentColor = NexusTheme.colors.textSecondary,
        title = {
            Text(
                text = stringResource(R.string.problem_delete_title),
                style = NexusTheme.typography.title,
            )
        },
        text = {
            Text(
                text = stringResource(
                    R.string.problem_delete_confirm,
                    "${problem.key.judge.displayName} ${problem.key.externalId}",
                ),
                style = NexusTheme.typography.label,
            )
        },
        confirmButton = {
            Text(
                text = stringResource(R.string.action_delete),
                style = NexusTheme.typography.data,
                color = NexusTheme.colors.danger,
                modifier = Modifier
                    .clickable(role = Role.Button) { onConfirm() }
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
