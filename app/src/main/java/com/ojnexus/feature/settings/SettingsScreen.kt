package com.ojnexus.feature.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojnexus.R
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusStatus
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.GlobalContext
import com.ojnexus.core.ui.LocalAppContainer
import java.time.ZoneId

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel>(
        factory = ContainerViewModelFactory(container) {
            SettingsViewModel(it.judgeAccountRepository, it.codeforcesSyncRepository)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val connecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    var showDisconnectDialog by rememberSaveable { mutableStateOf(false) }
    var purgeCache by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        NexusTopBar(
            title = stringResource(R.string.settings_title),
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
            NexusSection(label = stringResource(R.string.settings_section_judges)) {
                val account = state.account
                val syncState = state.syncState
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexusTheme.colors.surface, NexusRadius.md)
                        .padding(NexusSpacing.md),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_codeforces),
                            style = NexusTheme.typography.data,
                            color = NexusTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        when {
                            account != null && syncState?.state == SyncPhase.SYNCING.name ->
                                NexusStatus(stringResource(R.string.settings_state_syncing), NexusTone.Accent)
                            account != null && syncState?.state == SyncPhase.PARTIAL.name ->
                                NexusStatus(stringResource(R.string.settings_state_partial), NexusTone.Warning)
                            account != null && syncState?.state == SyncPhase.ERROR.name ->
                                NexusStatus(stringResource(R.string.settings_state_failed), NexusTone.Danger)
                            account != null ->
                                NexusStatus(stringResource(R.string.settings_state_connected), NexusTone.Success)
                            else ->
                                NexusStatus(stringResource(R.string.dash_not_connected), NexusTone.Neutral)
                        }
                    }

                    if (account != null) {
                        Spacer(modifier = Modifier.height(NexusSpacing.sm))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.canonicalHandle,
                                    style = NexusTheme.typography.dataLarge,
                                    color = NexusTheme.colors.accent,
                                )
                                state.profile?.rating?.let { rating ->
                                    Text(
                                        text = "${stringResource(R.string.metric_rating)} $rating · ${stringResource(R.string.rating_rated_contests)}",
                                        style = NexusTheme.typography.dataSmall,
                                        color = NexusTheme.colors.textTertiary,
                                    )
                                }
                                lastSyncLabel(syncState)?.let { label ->
                                    Text(
                                        text = "${stringResource(R.string.sync_last_sync)} $label",
                                        style = NexusTheme.typography.dataSmall,
                                        color = NexusTheme.colors.textTertiary,
                                    )
                                }
                                if (syncState?.state == SyncPhase.SYNCING.name &&
                                    syncState.currentStage == com.ojnexus.core.data.sync.SyncStage.SUBMISSIONS.name
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.sync_imported_count,
                                            syncState.submissionsImported ?: 0,
                                        ),
                                        style = NexusTheme.typography.dataSmall,
                                        color = NexusTheme.colors.accent,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(NexusSpacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                            SettingsAction(stringResource(R.string.settings_sync_now)) {
                                viewModel.syncNow(account.id)
                            }
                            SettingsAction(
                                stringResource(R.string.settings_disconnect),
                                danger = true,
                            ) { showDisconnectDialog = true }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(NexusSpacing.sm))
                        Text(
                            text = stringResource(R.string.settings_public_handle_hint),
                            style = NexusTheme.typography.dataSmall,
                            color = NexusTheme.colors.textTertiary,
                        )
                        Spacer(modifier = Modifier.height(NexusSpacing.xs))
                        HandleInput(onConnect = viewModel::connect, connecting = connecting)
                        error?.let { connectError ->
                            Spacer(modifier = Modifier.height(NexusSpacing.xxs))
                            Text(
                                text = errorLabel(connectError),
                                style = NexusTheme.typography.dataSmall,
                                color = NexusTheme.colors.danger,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(NexusSpacing.xxl))
        }
    }

    if (showDisconnectDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            containerColor = NexusTheme.colors.surface,
            titleContentColor = NexusTheme.colors.textPrimary,
            textContentColor = NexusTheme.colors.textSecondary,
            title = { Text(stringResource(R.string.settings_disconnect_title), style = NexusTheme.typography.title) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_disconnect_body), style = NexusTheme.typography.label)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = NexusSpacing.xs),
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = purgeCache,
                            onCheckedChange = { purgeCache = it },
                        )
                        Text(
                            text = stringResource(R.string.settings_purge_cache),
                            style = NexusTheme.typography.dataSmall,
                            color = NexusTheme.colors.textSecondary,
                        )
                    }
                }
            },
            confirmButton = {
                DialogText(stringResource(R.string.settings_disconnect), NexusTheme.colors.danger) {
                    showDisconnectDialog = false
                    state.account?.let { viewModel.disconnect(it.id, purgeCache) }
                }
            },
            dismissButton = {
                DialogText(stringResource(R.string.action_cancel), NexusTheme.colors.textSecondary) {
                    showDisconnectDialog = false
                }
            },
        )
    }
}

@Composable
private fun errorLabel(error: SettingsViewModel.ConnectError): String = when (error) {
    SettingsViewModel.ConnectError.HandleEmpty -> stringResource(R.string.settings_handle_empty)
    SettingsViewModel.ConnectError.InvalidHandle -> stringResource(R.string.settings_handle_invalid)
    SettingsViewModel.ConnectError.UserNotFound -> stringResource(R.string.settings_handle_not_found)
    SettingsViewModel.ConnectError.RateLimited -> stringResource(R.string.settings_rate_limited)
    SettingsViewModel.ConnectError.Network -> stringResource(R.string.settings_network_unavailable)
    SettingsViewModel.ConnectError.ApiFailed -> stringResource(R.string.settings_api_failed)
}

/** Minute-granularity relative time for LAST SYNC — never refreshed per second. */
@Composable
private fun lastSyncLabel(syncState: com.ojnexus.core.database.entity.SyncStateEntity?): String? {
    val last = syncState?.lastSuccessfulSyncAt ?: return null
    val minutes = ((System.currentTimeMillis() - last) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.last_sync_just_now)
        minutes < 60 -> stringResource(R.string.format_last_sync_min, minutes.toInt())
        else -> stringResource(R.string.format_last_sync_hour, (minutes / 60).toInt())
    }
}

@Composable
private fun HandleInput(onConnect: (String) -> Unit, connecting: Boolean) {
    var handle by rememberSaveable { mutableStateOf("") }
    val colors = NexusTheme.colors
    Column {
        Text(
            text = stringResource(R.string.settings_handle_hint),
            style = NexusTheme.typography.sectionLabel,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(NexusSpacing.xxxs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background, NexusRadius.sm)
                .border(1.dp, colors.border, NexusRadius.sm)
                .padding(horizontal = NexusSpacing.xs, vertical = NexusSpacing.xxxs),
        ) {
            BasicTextField(
                value = handle,
                onValueChange = { handle = it },
                singleLine = true,
                textStyle = NexusTheme.typography.dataSmall.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(NexusSpacing.sm))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .background(colors.accentContainer, NexusRadius.sm)
                    .border(1.dp, colors.accent, NexusRadius.sm)
                    .clickable(enabled = !connecting, role = Role.Button) { onConnect(handle) }
                    .padding(horizontal = NexusSpacing.md, vertical = NexusSpacing.xs),
            ) {
                Text(
                    text = stringResource(
                        if (connecting) R.string.settings_connecting else R.string.settings_connect,
                    ),
                    style = NexusTheme.typography.data,
                    color = colors.accent,
                )
            }
        }
    }
}

@Composable
private fun SettingsAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val colors = NexusTheme.colors
    val foreground = if (danger) colors.danger else colors.accent
    Box(
        modifier = Modifier
            .background(colors.surface, NexusRadius.sm)
            .border(1.dp, foreground, NexusRadius.sm)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = NexusSpacing.sm, vertical = NexusSpacing.xs),
    ) {
        Text(text = label, style = NexusTheme.typography.dataSmall, color = foreground)
    }
}

@Composable
private fun DialogText(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = label,
        style = NexusTheme.typography.data,
        color = color,
        modifier = Modifier
            .clickable(role = Role.Button) { onClick() }
            .padding(NexusSpacing.xs),
    )
}
