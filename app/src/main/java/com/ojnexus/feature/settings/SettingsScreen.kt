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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojnexus.R
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.data.preferences.AppLanguage
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusThemeSlot
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusStatus
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.UrlOpener
import com.ojnexus.judge.JudgeCapability
import com.ojnexus.judge.luogu.LuoguUrls

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    focusOpenApp: Boolean = false,
) {
    val container = LocalAppContainer.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel>(
        factory = ContainerViewModelFactory(container) {
                SettingsViewModel(
                    it.judgeAccountRepository,
                    it.judgeDataRepository,
                    it.judgeRegistry,
                    it.backupRepository,
                    it.userPreferencesRepository,
                    it.luoguOpenCredentialStore,
                    it.luoguOpenClient,
                )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val connecting by viewModel.connecting.collectAsStateWithLifecycle()
    val backupResult by viewModel.backup.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val openAppState by viewModel.openApp.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { destination ->
        if (destination != null) {
            viewModel.exportBackup(context.contentResolver, destination)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { source ->
        if (source != null) {
            viewModel.importBackup(context.contentResolver, source)
        }
    }
    var disconnectAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var purgeCache by rememberSaveable { mutableStateOf(false) }
    val appLanguage = AppLanguage.fromLocaleTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    val settingsScrollState = rememberScrollState()
    var openAppContentOffset by remember { mutableStateOf<Int?>(null) }
    var openAppReady by remember { mutableStateOf(false) }

    LaunchedEffect(focusOpenApp, openAppReady, openAppContentOffset) {
        val contentOffset = openAppContentOffset
        if (focusOpenApp && openAppReady && contentOffset != null) {
            withFrameNanos { }
            settingsScrollState.animateScrollTo(
                contentOffset.coerceIn(0, settingsScrollState.maxValue),
            )
        }
    }

    Column(Modifier.fillMaxSize().background(NexusTheme.colors.background)) {
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
            Modifier.fillMaxSize().verticalScroll(settingsScrollState)
                .padding(horizontal = NexusSpacing.screenHorizontal),
        ) {
            Spacer(Modifier.height(NexusSpacing.md))
            NexusSection(label = stringResource(R.string.settings_section_judges)) {
                state.connections.forEachIndexed { index, connection ->
                    if (index > 0) Spacer(Modifier.height(NexusSpacing.sm))
                    JudgeConnectionPanel(
                        connection = connection,
                        error = errors[connection.judge],
                        connecting = connection.judge in connecting,
                        onConnect = { handle -> viewModel.connect(connection.judge, handle) },
                        onSync = { connection.account?.let(viewModel::syncNow) },
                        onDisconnect = { disconnectAccountId = connection.account?.id },
                    )
                }
            }
            Spacer(Modifier.height(NexusSpacing.xl))
            NexusSection(label = stringResource(R.string.settings_section_data)) {
                Text(
                    text = stringResource(R.string.settings_backup_hint),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                )
                Spacer(Modifier.height(NexusSpacing.sm))
                SettingsAction(
                    label = stringResource(R.string.settings_export_backup),
                    onClick = { backupLauncher.launch("oj-nexus-backup.db") },
                )
                Spacer(Modifier.height(NexusSpacing.xs))
                SettingsAction(
                    label = stringResource(R.string.settings_import_backup),
                    onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "application/octet-stream",
                                "application/vnd.sqlite3",
                                "application/x-sqlite3",
                            ),
                        )
                    },
                )
                backupResult?.let { result ->
                    Spacer(Modifier.height(NexusSpacing.xs))
                    Text(
                        text = stringResource(
                            when {
                                result.success && result.operation == BackupOperation.EXPORT ->
                                    R.string.settings_backup_success
                                result.success -> R.string.settings_backup_import_success
                                else -> R.string.settings_backup_failed
                            },
                        ),
                        style = NexusTheme.typography.dataSmall,
                        color = if (result.success) NexusTheme.colors.success else NexusTheme.colors.danger,
                    )
                }
            }
            Spacer(Modifier.height(NexusSpacing.xl))
            NexusSection(label = stringResource(R.string.settings_section_interaction)) {
                SettingsToggle(
                    label = stringResource(R.string.settings_reduce_motion),
                    description = stringResource(R.string.settings_reduce_motion_desc),
                    checked = preferences.reduceMotion,
                    onCheckedChange = viewModel::setReduceMotion,
                )
                Spacer(Modifier.height(NexusSpacing.sm))
                SettingsToggle(
                    label = stringResource(R.string.settings_haptics),
                    description = stringResource(R.string.settings_haptics_desc),
                    checked = preferences.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticsEnabled,
                )
            }
            Spacer(Modifier.height(NexusSpacing.xl))
            NexusSection(
                label = stringResource(R.string.settings_section_luogu_open),
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        if (openAppContentOffset == null) {
                            openAppContentOffset = coordinates.positionInParent().y.roundToInt()
                        }
                        openAppReady = true
                    },
            ) {
                Text(
                    text = stringResource(R.string.settings_openapp_hint),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                )
                Spacer(Modifier.height(NexusSpacing.xs))
                SettingsAction(
                    label = stringResource(R.string.settings_openapp_docs),
                    onClick = { UrlOpener.open(context, LuoguUrls.openPlatformDocs()) },
                )
                Spacer(Modifier.height(NexusSpacing.sm))
                if (openAppState.configured) {
                    Text(
                        text = stringResource(R.string.settings_openapp_configured),
                        style = NexusTheme.typography.data,
                        color = NexusTheme.colors.success,
                    )
                    Spacer(Modifier.height(NexusSpacing.xs))
                    SettingsAction(
                        label = stringResource(R.string.settings_openapp_clear),
                        danger = true,
                        onClick = viewModel::clearOpenAppCredential,
                    )
                    Spacer(Modifier.height(NexusSpacing.sm))
                    SettingsAction(
                        label = stringResource(
                            if (openAppState.checkingQuota) {
                                R.string.settings_openapp_quota_checking
                            } else {
                                R.string.settings_openapp_quota_check
                            },
                        ),
                        onClick = viewModel::checkOpenAppQuota,
                    )
                    openAppState.quota?.let { quota ->
                        Spacer(Modifier.height(NexusSpacing.xs))
                        Text(
                            text = stringResource(
                                R.string.settings_openapp_quota_available,
                                quota.totalAvailablePoints,
                            ),
                            style = NexusTheme.typography.data,
                            color = NexusTheme.colors.accent,
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_openapp_quota_buckets,
                                quota.quotas.size,
                            ),
                            style = NexusTheme.typography.dataSmall,
                            color = NexusTheme.colors.textTertiary,
                        )
                    }
                } else {
                    OpenAppCredentialEditor(
                        saving = openAppState.saving,
                        verifying = openAppState.verifying,
                        error = openAppState.error,
                        onSave = viewModel::saveOpenAppCredential,
                    )
                }
                openAppState.quotaError?.let { error ->
                    Spacer(Modifier.height(NexusSpacing.xxs))
                    Text(
                        text = quotaErrorLabel(error),
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.danger,
                    )
                }
            }
            Spacer(Modifier.height(NexusSpacing.xl))
            NexusSection(label = stringResource(R.string.settings_section_language)) {
                Text(
                    text = stringResource(R.string.settings_language_hint),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                )
                Spacer(Modifier.height(NexusSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                    AppLanguage.entries.forEach { language ->
                        LanguageAction(
                            language = language,
                            selected = language == appLanguage,
                            onClick = {
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(language.localeTag),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(NexusSpacing.xl))
            NexusSection(label = stringResource(R.string.settings_section_theme)) {
                Text(
                    text = stringResource(R.string.settings_theme_hint),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                )
                Spacer(Modifier.height(NexusSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                    NexusThemeSlot.entries.forEach { slot ->
                        ThemeSlotAction(
                            slot = slot,
                            selected = slot == preferences.themeSlot,
                            onClick = { viewModel.setThemeSlot(slot) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(NexusSpacing.xxl))
        }
    }

    val disconnectAccount = state.connections.mapNotNull { it.account }
        .firstOrNull { it.id == disconnectAccountId }
    if (disconnectAccount != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { disconnectAccountId = null },
            containerColor = NexusTheme.colors.surface,
            titleContentColor = NexusTheme.colors.textPrimary,
            textContentColor = NexusTheme.colors.textSecondary,
            title = {
                Text(
                    stringResource(
                        R.string.settings_disconnect_title,
                        JudgeId.fromId(disconnectAccount.judge)?.displayName
                            ?: disconnectAccount.judge.uppercase(),
                    ),
                    style = NexusTheme.typography.title,
                )
            },
            text = {
                Column {
                    Text(stringResource(R.string.settings_disconnect_body), style = NexusTheme.typography.label)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = NexusSpacing.xs)) {
                        androidx.compose.material3.Checkbox(
                            checked = purgeCache,
                            onCheckedChange = { purgeCache = it },
                        )
                        Text(
                            text = stringResource(R.string.settings_purge_judge_cache),
                            style = NexusTheme.typography.dataSmall,
                            color = NexusTheme.colors.textSecondary,
                        )
                    }
                }
            },
            confirmButton = {
                DialogText(stringResource(R.string.settings_disconnect), NexusTheme.colors.danger) {
                    viewModel.disconnect(disconnectAccount, purgeCache)
                    disconnectAccountId = null
                }
            },
            dismissButton = {
                DialogText(stringResource(R.string.action_cancel), NexusTheme.colors.textSecondary) {
                    disconnectAccountId = null
                }
            },
        )
    }
}

@Composable
private fun quotaErrorLabel(error: OpenAppQuotaError): String = when (error) {
    OpenAppQuotaError.CREDENTIAL_MISSING -> stringResource(R.string.settings_openapp_quota_missing)
    OpenAppQuotaError.UNAUTHORIZED -> stringResource(R.string.settings_openapp_quota_unauthorized)
    OpenAppQuotaError.FORBIDDEN -> stringResource(R.string.settings_openapp_quota_forbidden)
    OpenAppQuotaError.QUOTA_EXCEEDED -> stringResource(R.string.settings_openapp_quota_exceeded)
    OpenAppQuotaError.NOT_FOUND -> stringResource(R.string.settings_openapp_quota_api_error)
    OpenAppQuotaError.NETWORK -> stringResource(R.string.settings_network_unavailable)
    OpenAppQuotaError.API -> stringResource(R.string.settings_openapp_quota_api_error)
}

@Composable
private fun OpenAppCredentialEditor(
    saving: Boolean,
    verifying: Boolean,
    error: Boolean,
    onSave: (String, String) -> Unit,
) {
    // Credentials must not enter saved-instance-state or backup bundles.
    var user by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.xs)) {
        CredentialField(
            label = stringResource(R.string.settings_openapp_user),
            value = user,
            onValueChange = { user = it },
        )
        CredentialField(
            label = stringResource(R.string.settings_openapp_secret),
            value = secret,
            onValueChange = { secret = it },
            password = true,
        )
        SettingsAction(
            label = stringResource(
                when {
                    verifying -> R.string.settings_openapp_verifying
                    saving -> R.string.settings_openapp_saving
                    else -> R.string.settings_openapp_save
                },
            ),
            enabled = !saving,
            onClick = { onSave(user, secret) },
        )
        if (error) {
            Text(
                text = stringResource(R.string.settings_openapp_error),
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.danger,
            )
        }
    }
}

@Composable
private fun CredentialField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
) {
    val colors = NexusTheme.colors
    Column {
        Text(label, style = NexusTheme.typography.sectionLabel, color = colors.textTertiary)
        Spacer(Modifier.height(NexusSpacing.xxxs))
        Box(
            Modifier.fillMaxWidth().background(colors.background, NexusRadius.sm)
                .border(1.dp, colors.border, NexusRadius.sm)
                .padding(horizontal = NexusSpacing.xs, vertical = NexusSpacing.xxxs),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                ),
                textStyle = NexusTheme.typography.dataSmall.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun JudgeConnectionPanel(
    connection: JudgeConnectionUi,
    error: SettingsViewModel.ConnectError?,
    connecting: Boolean,
    onConnect: (String) -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val account = connection.account
    val sync = connection.syncState
    Column(
        Modifier.fillMaxWidth().background(NexusTheme.colors.surface, NexusRadius.md)
            .padding(NexusSpacing.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = connection.judge.displayName,
                style = NexusTheme.typography.data,
                color = NexusTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            when {
                account == null -> NexusStatus(stringResource(R.string.dash_not_connected), NexusTone.Neutral)
                sync?.state == SyncPhase.SYNCING.name -> NexusStatus(stringResource(R.string.settings_state_syncing), NexusTone.Accent)
                sync?.state == SyncPhase.PARTIAL.name -> NexusStatus(stringResource(R.string.settings_state_partial), NexusTone.Warning)
                sync?.state == SyncPhase.ERROR.name -> NexusStatus(stringResource(R.string.settings_state_failed), NexusTone.Danger)
                else -> NexusStatus(stringResource(R.string.settings_state_connected), NexusTone.Success)
            }
        }
        syncStageName(sync)?.let { stage ->
            Text(
                text = stringResource(R.string.settings_sync_stage, stage),
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.accent,
            )
        }
        Text(
            text = stringResource(R.string.settings_source_format, connection.reliability.name),
            style = NexusTheme.typography.dataSmall,
            color = NexusTheme.colors.textTertiary,
        )
        if (account == null) {
            Spacer(Modifier.height(NexusSpacing.sm))
            Text(
                text = stringResource(R.string.settings_public_handle_hint),
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.textTertiary,
            )
            Spacer(Modifier.height(NexusSpacing.xs))
            HandleInput(connection.judge, onConnect, connecting)
            error?.let {
                Spacer(Modifier.height(NexusSpacing.xxs))
                Text(errorLabel(it), style = NexusTheme.typography.dataSmall, color = NexusTheme.colors.danger)
            }
        } else {
            Spacer(Modifier.height(NexusSpacing.sm))
            Text(account.canonicalHandle, style = NexusTheme.typography.dataLarge, color = NexusTheme.colors.accent)
            Text(
                text = stringResource(R.string.settings_verification_format, account.verificationState),
                style = NexusTheme.typography.dataSmall,
                color = if (account.verificationState == "VERIFIED") {
                    NexusTheme.colors.success
                } else {
                    NexusTheme.colors.warning
                },
            )
            if (JudgeCapability.BACKGROUND_SYNC in connection.capabilities) {
                Text(
                    text = stringResource(R.string.settings_background_sync_enabled),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.accent,
                )
            }
            connection.profile?.rating?.let { rating ->
                Text(
                    text = "${stringResource(R.string.metric_rating)} $rating",
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                )
            } ?: if (connection.judge == JudgeId.ATCODER) {
                Text(
                    text = stringResource(R.string.settings_rating_unavailable),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                )
            } else {
                Unit
            }
            lastSyncLabel(sync)?.let {
                Text(
                    text = "${stringResource(R.string.sync_last_sync)} $it",
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                )
            }
            if (connection.judge == JudgeId.LUOGU &&
                sync?.lastErrorType == "AuthenticationRequired"
            ) {
                Text(
                    text = stringResource(R.string.settings_sync_auth_required),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.warning,
                )
            }
            if (sync?.state == SyncPhase.SYNCING.name && sync.currentStage == com.ojnexus.core.data.sync.SyncStage.SUBMISSIONS.name) {
                Text(
                    text = stringResource(R.string.sync_imported_count, sync.submissionsImported ?: 0),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.accent,
                )
            }
            Spacer(Modifier.height(NexusSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
                if (JudgeCapability.BACKGROUND_SYNC in connection.capabilities) {
                    SettingsAction(stringResource(R.string.settings_sync_now), onClick = onSync)
                }
                SettingsAction(stringResource(R.string.settings_disconnect), danger = true, onClick = onDisconnect)
            }
        }
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

@Composable
private fun lastSyncLabel(sync: com.ojnexus.core.database.entity.SyncStateEntity?): String? {
    val last = sync?.lastSuccessfulSyncAt ?: return null
    val minutes = ((System.currentTimeMillis() - last) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.last_sync_just_now)
        minutes < 60 -> stringResource(R.string.format_last_sync_min, minutes.toInt())
        else -> stringResource(R.string.format_last_sync_hour, (minutes / 60).toInt())
    }
}

@Composable
private fun HandleInput(judge: JudgeId, onConnect: (String) -> Unit, connecting: Boolean) {
    var handle by rememberSaveable(judge.id) { mutableStateOf("") }
    val colors = NexusTheme.colors
    Column {
        Text(stringResource(R.string.settings_handle_hint), style = NexusTheme.typography.sectionLabel, color = colors.textTertiary)
        Spacer(Modifier.height(NexusSpacing.xxxs))
        Box(
            Modifier.fillMaxWidth().background(colors.background, NexusRadius.sm)
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
        Spacer(Modifier.height(NexusSpacing.sm))
        Box(
            Modifier.background(colors.accentContainer, NexusRadius.sm)
                .border(1.dp, colors.accent, NexusRadius.sm)
                .clickable(enabled = !connecting, role = Role.Button) { onConnect(handle) }
                .padding(horizontal = NexusSpacing.md, vertical = NexusSpacing.xs),
        ) {
            Text(
                stringResource(if (connecting) R.string.settings_connecting else R.string.settings_connect),
                style = NexusTheme.typography.data,
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun SettingsAction(
    label: String,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = NexusTheme.colors
    val foreground = if (danger) colors.danger else colors.accent
    Box(
        Modifier.background(colors.surface, NexusRadius.sm).border(1.dp, foreground, NexusRadius.sm)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = NexusSpacing.sm, vertical = NexusSpacing.xs),
    ) {
        Text(label, style = NexusTheme.typography.dataSmall, color = foreground)
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Switch) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = NexusTheme.typography.data, color = colors.textPrimary)
            Text(description, style = NexusTheme.typography.dataSmall, color = colors.textTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textTertiary,
                uncheckedTrackColor = colors.surfaceElevated,
                uncheckedBorderColor = colors.border,
            ),
        )
    }
}

@Composable
private fun ThemeSlotAction(
    slot: NexusThemeSlot,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NexusTheme.colors
    Box(
        modifier.background(if (selected) colors.accentContainer else colors.surface, NexusRadius.sm)
            .border(NexusSize.dividerThickness, if (selected) colors.accent else colors.border, NexusRadius.sm)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = NexusSpacing.xxs, vertical = NexusSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                when (slot) {
                    NexusThemeSlot.NEXUS_BLUE -> R.string.settings_theme_blue
                    NexusThemeSlot.TERMINAL_GREEN -> R.string.settings_theme_green
                    NexusThemeSlot.AMBER_SIGNAL -> R.string.settings_theme_amber
                },
            ),
            style = NexusTheme.typography.dataSmall,
            color = if (selected) colors.accent else colors.textSecondary,
        )
    }
}

@Composable
private fun LanguageAction(
    language: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NexusTheme.colors
    Box(
        modifier.background(if (selected) colors.accentContainer else colors.surface, NexusRadius.sm)
            .border(NexusSize.dividerThickness, if (selected) colors.accent else colors.border, NexusRadius.sm)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = NexusSpacing.xxs, vertical = NexusSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                when (language) {
                    AppLanguage.SYSTEM -> R.string.settings_language_system
                    AppLanguage.ENGLISH -> R.string.settings_language_english
                    AppLanguage.SIMPLIFIED_CHINESE -> R.string.settings_language_chinese
                },
            ),
            style = NexusTheme.typography.dataSmall,
            color = if (selected) colors.accent else colors.textSecondary,
        )
    }
}

@Composable
private fun DialogText(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        label,
        style = NexusTheme.typography.data,
        color = color,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(NexusSpacing.xs),
    )
}
