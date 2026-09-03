package com.ojnexus.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusMotion
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme

data class PaletteCommand(
    val id: String,
    val title: String,
    val description: String,
    val keywords: Set<String> = emptySet(),
)

internal data class PaletteCommandSpec(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val keywords: Set<String> = emptySet(),
    @StringRes val keywordRes: Set<Int> = emptySet(),
)

internal fun paletteCommandSpecs(): List<PaletteCommandSpec> = listOf(
    PaletteCommandSpec(
        id = "dashboard",
        titleRes = R.string.nav_dashboard,
        descriptionRes = R.string.command_open_dashboard,
        keywords = setOf("home", "overview"),
    ),
    PaletteCommandSpec(
        id = "problems",
        titleRes = R.string.nav_problems,
        descriptionRes = R.string.command_open_problems,
        keywords = setOf("library", "catalog"),
    ),
    PaletteCommandSpec(
        id = "training",
        titleRes = R.string.nav_training,
        descriptionRes = R.string.command_open_training,
        keywords = setOf("review", "solve"),
    ),
    PaletteCommandSpec(
        id = "analytics",
        titleRes = R.string.nav_analytics,
        descriptionRes = R.string.command_open_analytics,
        keywords = setOf("stats", "metrics"),
    ),
    PaletteCommandSpec(
        id = "profile",
        titleRes = R.string.nav_profile,
        descriptionRes = R.string.command_open_profile,
        keywords = setOf("player", "card"),
    ),
    PaletteCommandSpec(
        id = "contests",
        titleRes = R.string.contest_center_title,
        descriptionRes = R.string.command_open_contests,
        keywords = setOf("arena", "round"),
    ),
    PaletteCommandSpec(
        id = "submissions",
        titleRes = R.string.submissions_title,
        descriptionRes = R.string.submissions_section_recent,
        keywordRes = setOf(
            R.string.submissions_title,
            R.string.submissions_check_result,
            R.string.workspace_open,
        ),
    ),
    PaletteCommandSpec(
        id = "add_problem",
        titleRes = R.string.action_add_problem,
        descriptionRes = R.string.command_add_problem,
        keywords = setOf("create", "local"),
    ),
    PaletteCommandSpec(
        id = "settings",
        titleRes = R.string.settings_title,
        descriptionRes = R.string.command_open_settings,
        keywords = setOf("preferences", "backup"),
    ),
)

internal fun filterCommands(commands: List<PaletteCommand>, query: String): List<PaletteCommand> {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return commands
    return commands.filter { command ->
        (listOf(command.title, command.description) + command.keywords)
            .any { it.contains(normalized, ignoreCase = true) }
    }
}

@Composable
fun CommandPalette(
    onDismiss: () -> Unit,
    onExecute: (String) -> Unit,
    onSearchProblems: (PaletteQuery.SearchProblems) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val commands = commandList()
    val filtered = remember(commands, query) { filterCommands(commands, query) }
    val directQuery = remember(query) { parsePaletteQuery(query) as? PaletteQuery.SearchProblems }
    val colors = NexusTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = colors.surface, shape = NexusRadius.lg) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(NexusSpacing.md)
                    .animateContentSize(
                        animationSpec = if (NexusTheme.reduceMotion) snap() else tween(
                            NexusMotion.DURATION_NORMAL,
                            easing = NexusMotion.EasingStandard,
                        ),
                    ),
            ) {
                Text(
                    text = stringResource(R.string.command_palette_title),
                    style = NexusTheme.typography.title,
                    color = colors.textPrimary,
                )
                Box(
                    Modifier.padding(top = NexusSpacing.sm)
                        .fillMaxWidth()
                        .background(colors.background, NexusRadius.sm)
                        .border(NexusSize.dividerThickness, colors.border, NexusRadius.sm)
                        .padding(horizontal = NexusSpacing.sm, vertical = NexusSpacing.xs),
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = NexusTheme.typography.data.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { field ->
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.command_palette_hint),
                                    style = NexusTheme.typography.data,
                                    color = colors.textTertiary,
                                )
                            }
                            field()
                        },
                    )
                }
                directQuery?.let { parsed ->
                    val judgeLabel = parsed.judge?.displayName
                        ?: stringResource(R.string.command_palette_any_judge)
                    val targetLabel = stringResource(
                        R.string.command_palette_direct_query_target,
                        judgeLabel,
                        parsed.query,
                    )
                    Row(
                        modifier = Modifier
                            .padding(top = NexusSpacing.sm)
                            .fillMaxWidth()
                            .background(colors.background, NexusRadius.sm)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(R.string.command_palette_direct_query_cd),
                            ) { onSearchProblems(parsed) }
                            .semantics { contentDescription = targetLabel }
                            .padding(NexusSpacing.sm),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(NexusSize.commandPaletteRailWidth)
                                .height(NexusSize.commandPaletteRailHeight)
                                .background(colors.accent, NexusRadius.xs),
                        )
                        Column(
                            modifier = Modifier.padding(start = NexusSpacing.sm),
                        ) {
                            Text(
                                text = stringResource(R.string.command_palette_direct_query),
                                style = NexusTheme.typography.sectionLabel,
                                color = colors.accent,
                            )
                            Text(
                                text = targetLabel,
                                style = NexusTheme.typography.dataSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = stringResource(R.string.command_palette_direct_query_hint),
                                style = NexusTheme.typography.dataSmall,
                                color = colors.textTertiary,
                            )
                        }
                    }
                }
                if (filtered.isEmpty() && directQuery == null) {
                    Text(
                        text = stringResource(R.string.command_palette_empty),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(top = NexusSpacing.md),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(top = NexusSpacing.sm)
                            .heightIn(max = NexusSize.commandPaletteMaxHeight),
                        verticalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
                    ) {
                        items(filtered, key = { it.id }) { command ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable(role = Role.Button) { onExecute(command.id) }
                                    .padding(vertical = NexusSpacing.xs),
                            ) {
                                Text(command.title, style = NexusTheme.typography.data, color = colors.textPrimary)
                                Text(command.description, style = NexusTheme.typography.dataSmall, color = colors.textTertiary)
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.action_close),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.accent,
                    modifier = Modifier.padding(top = NexusSpacing.sm)
                        .clickable(role = Role.Button, onClick = onDismiss),
                )
            }
        }
    }
}

@Composable
private fun commandList(): List<PaletteCommand> = paletteCommandSpecs().map { spec ->
    PaletteCommand(
        id = spec.id,
        title = stringResource(spec.titleRes),
        description = stringResource(spec.descriptionRes),
        keywords = spec.keywords + spec.keywordRes.map { stringResource(it) },
    )
}
