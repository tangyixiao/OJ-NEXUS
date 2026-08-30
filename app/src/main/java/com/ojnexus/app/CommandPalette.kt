package com.ojnexus.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import com.ojnexus.R
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

internal fun filterCommands(commands: List<PaletteCommand>, query: String): List<PaletteCommand> {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return commands
    return commands.filter { command ->
        (listOf(command.title, command.description) + command.keywords)
            .any { it.contains(normalized, ignoreCase = true) }
    }
}

@Composable
fun CommandPalette(onDismiss: () -> Unit, onExecute: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val commands = commandList()
    val filtered = remember(commands, query) { filterCommands(commands, query) }
    val colors = NexusTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = colors.surface, shape = NexusRadius.lg) {
            Column(Modifier.fillMaxWidth().padding(NexusSpacing.md)) {
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
                if (filtered.isEmpty()) {
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
private fun commandList(): List<PaletteCommand> = listOf(
    PaletteCommand(
        id = "dashboard",
        title = stringResource(R.string.nav_dashboard),
        description = stringResource(R.string.command_open_dashboard),
        keywords = setOf("home", "overview"),
    ),
    PaletteCommand(
        id = "problems",
        title = stringResource(R.string.nav_problems),
        description = stringResource(R.string.command_open_problems),
        keywords = setOf("library", "catalog"),
    ),
    PaletteCommand(
        id = "training",
        title = stringResource(R.string.nav_training),
        description = stringResource(R.string.command_open_training),
        keywords = setOf("review", "solve"),
    ),
    PaletteCommand(
        id = "analytics",
        title = stringResource(R.string.nav_analytics),
        description = stringResource(R.string.command_open_analytics),
        keywords = setOf("stats", "metrics"),
    ),
    PaletteCommand(
        id = "profile",
        title = stringResource(R.string.nav_profile),
        description = stringResource(R.string.command_open_profile),
        keywords = setOf("player", "card"),
    ),
    PaletteCommand(
        id = "contests",
        title = stringResource(R.string.contest_center_title),
        description = stringResource(R.string.command_open_contests),
        keywords = setOf("arena", "round"),
    ),
    PaletteCommand(
        id = "add_problem",
        title = stringResource(R.string.action_add_problem),
        description = stringResource(R.string.command_add_problem),
        keywords = setOf("create", "local"),
    ),
    PaletteCommand(
        id = "settings",
        title = stringResource(R.string.settings_title),
        description = stringResource(R.string.command_open_settings),
        keywords = setOf("preferences", "backup"),
    ),
)
