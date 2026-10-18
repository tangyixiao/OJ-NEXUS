package com.ojnexus.feature.settings

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.backup.BackupCadence
import com.ojnexus.core.data.backup.ScheduledBackupScheduler
import com.ojnexus.core.data.backup.SnapshotConflictReport
import com.ojnexus.core.data.preferences.UserPreferences
import com.ojnexus.core.data.preferences.UserPreferencesRepository
import com.ojnexus.core.data.restore.RestoreOutcome
import com.ojnexus.core.designsystem.NexusThemeSlot
import com.ojnexus.core.data.repository.BackupRepository
import com.ojnexus.core.data.repository.DatabaseMaintenanceRepository
import com.ojnexus.core.data.repository.DatabaseMaintenanceStats
import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.repository.JudgeDataRepository
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.data.sync.SyncReport
import com.ojnexus.core.data.sync.SyncRetryRequest
import com.ojnexus.core.database.dao.SyncOperationDao
import com.ojnexus.core.database.dao.SyncOperationWithModules
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.JudgeProfileEntity
import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.DataSourceReliability
import com.ojnexus.judge.JudgeCapability
import com.ojnexus.judge.JudgeRegistry
import com.ojnexus.judge.sync.JudgeSyncWorker
import com.ojnexus.judge.luogu.open.OpenAppCredential
import com.ojnexus.judge.luogu.open.OpenAppCredentialStore
import com.ojnexus.judge.luogu.open.LuoguOpenApiError
import com.ojnexus.judge.luogu.open.LuoguOpenCredentialVerifier
import com.ojnexus.judge.luogu.open.LuoguOpenQuotaReader
import com.ojnexus.judge.luogu.open.LuoguOpenQuotaSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class JudgeConnectionUi(
    val judge: JudgeId,
    val account: JudgeAccountEntity?,
    val profile: JudgeProfileEntity?,
    val syncState: SyncStateEntity?,
    val capabilities: Set<JudgeCapability>,
    val reliability: DataSourceReliability,
    val syncOperations: List<SyncOperationWithModules> = emptyList(),
)

data class SettingsUiState(val connections: List<JudgeConnectionUi>)

enum class BackupOperation { EXPORT, IMPORT }

data class BackupResult(val operation: BackupOperation, val success: Boolean)

enum class RestoreStatus { APPLIED, ROLLED_BACK, REJECTED }

internal fun restoreStatusFor(outcome: RestoreOutcome?): RestoreStatus? = when (outcome) {
    is RestoreOutcome.Applied -> RestoreStatus.APPLIED
    is RestoreOutcome.RolledBack -> RestoreStatus.ROLLED_BACK
    is RestoreOutcome.Rejected -> RestoreStatus.REJECTED
    else -> null
}

data class OpenAppUiState(
    val configured: Boolean = false,
    val editing: Boolean = false,
    val saving: Boolean = false,
    val verifying: Boolean = false,
    val error: Boolean = false,
    val inputError: OpenAppCredentialInputError? = null,
    val checkingQuota: Boolean = false,
    val quota: LuoguOpenQuotaSnapshot? = null,
    val quotaError: OpenAppQuotaError? = null,
)

enum class OpenAppQuotaError {
    CREDENTIAL_MISSING,
    UNAUTHORIZED,
    FORBIDDEN,
    QUOTA_EXCEEDED,
    NOT_FOUND,
    NETWORK,
    API,
}

class SettingsViewModel(
    private val accountRepository: JudgeAccountRepository,
    private val dataRepository: JudgeDataRepository,
    private val registry: JudgeRegistry,
    private val backupRepository: BackupRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val maintenanceRepository: DatabaseMaintenanceRepository? = null,
    private val openAppCredentialStore: OpenAppCredentialStore? = null,
    private val openAppQuotaReader: LuoguOpenQuotaReader? = null,
    private val openAppCredentialVerifier: LuoguOpenCredentialVerifier? = null,
    private val manualSyncEnqueuer: (JudgeId, Long) -> Unit = { judge, accountId ->
        JudgeSyncWorker.enqueueManual(
            com.ojnexus.core.ui.GlobalContext.application,
            judge,
            accountId,
            true,
        )
    },
    restoreOutcome: RestoreOutcome? = null,
    private val syncOperationDao: SyncOperationDao? = null,
    private val syncRetryDispatcher: suspend (SyncRetryRequest) -> SyncReport? = { null },
) : ViewModel() {
    private val judges = registry.supportedJudges().sortedBy { it.ordinal }
    private val recentOperations = syncOperationDao?.let { dao ->
        combine(judges.map { judge -> dao.observeRecentByJudge(judge.id, limit = 10) }) { rows ->
            judges.zip(rows).toMap()
        }
    } ?: flowOf(emptyMap<JudgeId, List<SyncOperationWithModules>>())

    val state: StateFlow<SettingsUiState> = combine(
        dataRepository.observeConnections(),
        recentOperations,
    ) { snapshot, histories ->
        SettingsUiState(
            judges.map { judge ->
                val adapter = registry.adapter(judge)
                JudgeConnectionUi(
                    judge = judge,
                    account = snapshot.accounts[judge],
                    profile = snapshot.profiles[judge],
                    syncState = snapshot.syncStates[judge],
                    capabilities = adapter.capabilities,
                    reliability = adapter.reliability,
                    syncOperations = histories[judge].orEmpty(),
                )
            },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(emptyList()),
    )

    sealed interface ConnectError {
        data object HandleEmpty : ConnectError
        data object InvalidHandle : ConnectError
        data object UserNotFound : ConnectError
        data object RateLimited : ConnectError
        data object Network : ConnectError
        data object ApiFailed : ConnectError
    }

    private val connectErrors = MutableStateFlow<Map<JudgeId, ConnectError>>(emptyMap())
    val errors: StateFlow<Map<JudgeId, ConnectError>> = connectErrors.asStateFlow()
    private val connectingJudges = MutableStateFlow<Set<JudgeId>>(emptySet())
    val connecting: StateFlow<Set<JudgeId>> = connectingJudges.asStateFlow()
    private val syncAllInFlightFlag = AtomicBoolean(false)
    private val _syncAllInFlight = MutableStateFlow(false)
    val syncAllInFlight: StateFlow<Boolean> = _syncAllInFlight.asStateFlow()
    private val retryInFlightFlag = AtomicBoolean(false)
    private val _retryingOperationId = MutableStateFlow<Long?>(null)
    val retryingOperationId: StateFlow<Long?> = _retryingOperationId.asStateFlow()
    private val backupResult = MutableStateFlow<BackupResult?>(null)
    val backup: StateFlow<BackupResult?> = backupResult.asStateFlow()
    private val wipeJudges = MutableStateFlow<Set<JudgeId>>(emptySet())
    val wiping: StateFlow<Set<JudgeId>> = wipeJudges.asStateFlow()
    private val wipeErrors = MutableStateFlow<Map<JudgeId, Boolean>>(emptyMap())
    val wipeFailed: StateFlow<Map<JudgeId, Boolean>> = wipeErrors.asStateFlow()
    private val restoreStatus = MutableStateFlow(restoreStatusFor(restoreOutcome))
    val restore: StateFlow<RestoreStatus?> = restoreStatus.asStateFlow()
    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferences(),
    )
    private val openAppState = MutableStateFlow(OpenAppUiState())
    val openApp: StateFlow<OpenAppUiState> = openAppState.asStateFlow()

    init {
        viewModelScope.launch {
            val configured = runCatching { openAppCredentialStore?.read() != null }.getOrDefault(false)
            openAppState.update { it.copy(configured = configured) }
        }
    }

    fun saveOpenAppCredential(user: String, secret: String) {
        val store = openAppCredentialStore ?: return
        val normalizedUser = user.trim()
        val normalizedSecret = secret.trim()
        val inputError = validateOpenAppCredentialInput(normalizedUser, normalizedSecret)
        if (inputError != null) {
            openAppState.update {
                it.copy(error = false, inputError = inputError, quotaError = null)
            }
            return
        }
        openAppState.update {
            it.copy(
                saving = true,
                verifying = openAppQuotaReader != null,
                error = false,
                inputError = null,
                quotaError = null,
            )
        }
        viewModelScope.launch {
            try {
                when (val verification = verifyAndStoreOpenAppCredential(
                    store = store,
                    quotaReader = openAppQuotaReader,
                    credential = OpenAppCredential(user.trim(), secret.trim()),
                )) {
                    is OpenAppCredentialVerification.Verified -> {
                        openAppState.value = OpenAppUiState(
                            configured = true,
                            quota = verification.quota,
                        )
                    }
                    is OpenAppCredentialVerification.Rejected -> {
                        openAppState.value = OpenAppUiState(quotaError = verification.error)
                    }
                    is OpenAppCredentialVerification.Unverified -> {
                        openAppState.value = OpenAppUiState(
                            configured = true,
                            quotaError = verification.error,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                openAppState.update { it.copy(saving = false, verifying = false, error = true, inputError = null) }
            }
        }
    }

    fun beginOpenAppCredentialReplacement() {
        if (!openAppState.value.configured || openAppCredentialVerifier == null) return
        openAppState.update {
            it.copy(
                editing = true,
                error = false,
                inputError = null,
                quotaError = null,
            )
        }
    }

    fun cancelOpenAppCredentialReplacement() {
        if (!openAppState.value.configured) return
        openAppState.update {
            it.copy(
                editing = false,
                saving = false,
                verifying = false,
                error = false,
                inputError = null,
                quotaError = null,
            )
        }
    }

    fun replaceOpenAppCredential(user: String, secret: String) {
        val store = openAppCredentialStore ?: return
        val verifier = openAppCredentialVerifier ?: return
        if (!openAppState.value.configured) return
        val normalizedUser = user.trim()
        val normalizedSecret = secret.trim()
        val inputError = validateOpenAppCredentialInput(normalizedUser, normalizedSecret)
        if (inputError != null) {
            openAppState.update {
                it.copy(error = false, inputError = inputError, quotaError = null)
            }
            return
        }
        openAppState.update {
            it.copy(
                editing = true,
                saving = true,
                verifying = true,
                error = false,
                inputError = null,
                quotaError = null,
            )
        }
        viewModelScope.launch {
            try {
                when (val verification = verifyAndReplaceOpenAppCredential(
                    store = store,
                    verifier = verifier,
                    credential = OpenAppCredential(normalizedUser, normalizedSecret),
                )) {
                    is OpenAppCredentialVerification.Verified -> {
                        openAppState.value = OpenAppUiState(
                            configured = true,
                            quota = verification.quota,
                        )
                    }
                    is OpenAppCredentialVerification.Rejected -> {
                        openAppState.value = OpenAppUiState(
                            configured = true,
                            editing = true,
                            quotaError = verification.error,
                        )
                    }
                    is OpenAppCredentialVerification.Unverified -> {
                        openAppState.value = OpenAppUiState(
                            configured = true,
                            editing = true,
                            quotaError = verification.error,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                openAppState.update {
                    it.copy(
                        configured = true,
                        editing = true,
                        saving = false,
                        verifying = false,
                        error = true,
                        inputError = null,
                    )
                }
            }
        }
    }

    fun clearOpenAppCredential() {
        val store = openAppCredentialStore ?: return
        viewModelScope.launch {
            runCatching { store.clear() }
            openAppState.value = OpenAppUiState()
        }
    }

    fun checkOpenAppQuota() {
        val reader = openAppQuotaReader ?: return
        if (openAppState.value.checkingQuota) return
        openAppState.update { it.copy(checkingQuota = true, quotaError = null) }
        viewModelScope.launch {
            try {
                openAppState.update {
                    it.copy(
                        checkingQuota = false,
                        quota = reader.fetchQuota(),
                        quotaError = null,
                    )
                }
            } catch (error: LuoguOpenApiError) {
                openAppState.update {
                    it.copy(checkingQuota = false, quotaError = error.toOpenAppQuotaError())
                }
            } catch (_: Exception) {
                openAppState.update {
                    it.copy(checkingQuota = false, quotaError = OpenAppQuotaError.API)
                }
            }
        }
    }

    fun exportBackup(resolver: ContentResolver, destination: Uri) {
        viewModelScope.launch {
            backupResult.value = BackupResult(
                operation = BackupOperation.EXPORT,
                success = backupRepository.exportTo(resolver, destination),
            )
        }
    }

    fun importBackup(resolver: ContentResolver, source: Uri) {
        viewModelScope.launch {
            backupResult.value = BackupResult(
                operation = BackupOperation.IMPORT,
                success = backupRepository.importFrom(resolver, source),
            )
        }
    }

    private val pendingImport = MutableStateFlow<SnapshotConflictReport?>(null)
    val pendingImportFlow: StateFlow<SnapshotConflictReport?> = pendingImport.asStateFlow()

    /**
     * First step of an import: inspect the archive for a conflict preview. A snapshot
     * archive produces a report the UI must confirm; anything else (legacy raw `.db`,
     * invalid file) imports directly as before.
     */
    fun inspectImport(resolver: ContentResolver, source: Uri) {
        viewModelScope.launch {
            val report = runCatching { backupRepository.inspectSnapshot(resolver, source) }
                .getOrDefault(null)
            if (report != null) {
                pendingImport.value = report
            } else {
                importBackup(resolver, source)
            }
        }
    }

    /** Confirms the previewed import; the actual replace happens in [importBackup]. */
    fun confirmImport(resolver: ContentResolver, source: Uri) {
        pendingImport.value = null
        importBackup(resolver, source)
    }

    /** Cancels the previewed import — nothing is written, the live database stays untouched. */
    fun cancelImport() {
        pendingImport.value = null
    }

    fun dismissRestoreStatus() {
        restoreStatus.value = null
    }

    private val backupStatusDismissed = MutableStateFlow(false)
    val backupStatusDismissedFlow: StateFlow<Boolean> = backupStatusDismissed.asStateFlow()

    // --- Database housekeeping (Phase 90) ---

    private val maintenanceStats = MutableStateFlow<DatabaseMaintenanceStats?>(null)
    val maintenanceStatsFlow: StateFlow<DatabaseMaintenanceStats?> = maintenanceStats.asStateFlow()

    private val vacuumRunning = MutableStateFlow(false)
    val vacuumRunningFlow: StateFlow<Boolean> = vacuumRunning.asStateFlow()

    /** Elapsed seconds while VACUUM runs — SQLite offers no real progress callback. */
    private val vacuumElapsedSeconds = MutableStateFlow(0)
    val vacuumElapsedFlow: StateFlow<Int> = vacuumElapsedSeconds.asStateFlow()

    /** Set when a cancel raced a committing VACUUM; the vacuum finished atomically. */
    private val vacuumCancelRaced = MutableStateFlow(false)
    val vacuumCancelRacedFlow: StateFlow<Boolean> = vacuumCancelRaced.asStateFlow()

    private var vacuumJob: Job? = null
    private var vacuumCancelRequested = false

    init {
        refreshMaintenanceStats()
    }

    // --- Local crash log (Phase 114) — nothing leaves the device ---

    private val crashFiles = MutableStateFlow<List<String>>(emptyList())

    /** Newest-first crash report file names; empty when none captured. */
    val crashFilesFlow: StateFlow<List<String>> = crashFiles.asStateFlow()

    private val crashPreview = MutableStateFlow<String?>(null)

    /** Content of the newest crash file for the settings dialog; null hides the dialog. */
    val crashPreviewFlow: StateFlow<String?> = crashPreview.asStateFlow()

    fun refreshCrashFiles() {
        val filesDir = com.ojnexus.core.ui.GlobalContext.application.filesDir
        crashFiles.value = runCatching {
            com.ojnexus.core.crash.CrashLog.crashFiles(filesDir).map { it.name }
        }.getOrDefault(emptyList())
    }

    fun setCrashLogEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setCrashLogEnabled(enabled)
        }
    }

    fun openCrashPreview() {
        viewModelScope.launch(Dispatchers.IO) {
            val filesDir = com.ojnexus.core.ui.GlobalContext.application.filesDir
            val file = runCatching { com.ojnexus.core.crash.CrashLog.newest(filesDir) }
                .getOrNull() ?: return@launch
            val raw = runCatching { file.readText() }.getOrNull() ?: return@launch
            crashPreview.value = raw
        }
    }

    fun dismissCrashPreview() {
        crashPreview.value = null
    }

    fun clearCrashFiles() {
        val filesDir = com.ojnexus.core.ui.GlobalContext.application.filesDir
        runCatching { com.ojnexus.core.crash.CrashLog.clear(filesDir) }
        crashFiles.value = emptyList()
    }

    fun refreshMaintenanceStats() {
        if (maintenanceRepository == null) return
        viewModelScope.launch(Dispatchers.IO) {
            maintenanceStats.value = runCatching { maintenanceRepository.maintenanceStats() }
                .getOrDefault(null)
        }
    }

    fun startVacuum() {
        val repository = maintenanceRepository ?: return
        if (vacuumRunning.value || vacuumJob?.isActive == true) return
        vacuumCancelRequested = false
        vacuumCancelRaced.value = false
        vacuumRunning.value = true
        vacuumElapsedSeconds.value = 0
        vacuumJob = viewModelScope.launch(Dispatchers.IO) {
            val ticker = launch {
                while (isActive) {
                    delay(1_000)
                    vacuumElapsedSeconds.value += 1
                }
            }
            try {
                repository.vacuum()
            } catch (exception: CancellationException) {
                // The framework cannot interrupt a running VACUUM; it commits atomically.
                if (vacuumCancelRequested) {
                    vacuumCancelRaced.value = true
                } else {
                    throw exception
                }
            } finally {
                ticker.cancel()
                vacuumRunning.value = false
                refreshMaintenanceStats()
            }
        }
    }

    /** Requests cancellation; VACUUM either stops before starting or finishes atomically. */
    fun requestVacuumCancel() {
        vacuumCancelRequested = true
        vacuumJob?.cancel()
    }

    fun dismissVacuumCancelRaced() {
        vacuumCancelRaced.value = false
    }

    /** Persists a user-picked SAF folder and takes persistable read/write permissions. */
    fun chooseBackupFolder(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModelScope.launch {
            preferencesRepository.setBackupTreeUri(uri.toString())
            // Re-arm the scheduler with the current cadence against the new folder.
            ScheduledBackupScheduler.update(context.applicationContext, preferences.value.backupCadence)
        }
    }

    fun clearBackupFolder(context: Context) {
        viewModelScope.launch {
            preferencesRepository.setBackupTreeUri("")
            ScheduledBackupScheduler.update(context.applicationContext, BackupCadence.OFF)
            preferencesRepository.setBackupCadence(BackupCadence.OFF)
        }
    }

    fun setBackupCadence(context: Context, cadence: BackupCadence) {
        viewModelScope.launch {
            preferencesRepository.setBackupCadence(cadence)
            ScheduledBackupScheduler.update(context.applicationContext, cadence)
        }
    }

    fun setBackupKeepCount(count: Int) {
        viewModelScope.launch { preferencesRepository.setBackupKeepCount(count) }
    }

    fun dismissBackupStatus() {
        backupStatusDismissed.value = true
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setReduceMotion(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHapticsEnabled(enabled) }
    }

    fun setThemeSlot(slot: NexusThemeSlot) {
        viewModelScope.launch { preferencesRepository.setThemeSlot(slot) }
    }

    fun connect(judge: JudgeId, handle: String) {
        if (judge in connectingJudges.value) return
        if (handle.isBlank()) {
            connectErrors.update { it + (judge to ConnectError.HandleEmpty) }
            return
        }
        connectErrors.update { it - judge }
        connectingJudges.update { it + judge }
        viewModelScope.launch {
            try {
                val account = accountRepository.connect(judge, handle)
                if (!account.enabled || !shouldScheduleJudgeSync(registry.adapter(judge).capabilities)) return@launch
                dataRepository.markSyncQueued(judge, account.id)
                JudgeSyncWorker.enqueueManual(
                    com.ojnexus.core.ui.GlobalContext.application,
                    judge,
                    account.id,
                    true,
                )
                JudgeSyncWorker.enqueuePeriodic(
                    com.ojnexus.core.ui.GlobalContext.application,
                    judge,
                    account.id,
                )
            } catch (e: JudgeAccountRepository.ConnectError) {
                connectErrors.update { current -> current + (judge to e.toUiError()) }
            } finally {
                connectingJudges.update { it - judge }
            }
        }
    }

    fun disconnect(account: JudgeAccountEntity, removeCache: Boolean) {
        val judge = JudgeId.fromId(account.judge) ?: return
        viewModelScope.launch {
            accountRepository.disconnect(account.id, removeCache)
            if (!shouldScheduleJudgeSync(registry.adapter(judge).capabilities)) return@launch
            JudgeSyncWorker.cancelFor(
                com.ojnexus.core.ui.GlobalContext.application,
                judge,
                account.id,
            )
        }
    }

    /**
     * Typed-confirm guard lives in the dialog; this only runs the destructive wipe and
     * surfaces a per-judge failure flag. The account row itself survives the wipe.
     */
    fun wipeJudgeData(account: JudgeAccountEntity) {
        val judge = JudgeId.fromId(account.judge) ?: return
        if (judge in wipeJudges.value) return
        wipeJudges.update { it + judge }
        viewModelScope.launch {
            wipeErrors.update { it - judge }
            try {
                accountRepository.wipeJudgeData(account.judge, account.id)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                wipeErrors.update { it + (judge to true) }
            } finally {
                wipeJudges.update { it - judge }
            }
        }
    }

    fun syncNow(account: JudgeAccountEntity) {
        val judge = JudgeId.fromId(account.judge) ?: return
        if (!account.enabled || !shouldScheduleJudgeSync(registry.adapter(judge).capabilities)) return
        viewModelScope.launch {
            queueManualSync(judge, account.id)
        }
    }

    fun setEnabled(account: JudgeAccountEntity, enabled: Boolean) {
        val judge = JudgeId.fromId(account.judge) ?: return
        viewModelScope.launch {
            val updated = accountRepository.setEnabled(account.id, enabled) ?: return@launch
            if (!shouldScheduleJudgeSync(registry.adapter(judge).capabilities)) return@launch
            if (updated.enabled) {
                JudgeSyncWorker.enqueuePeriodic(
                    com.ojnexus.core.ui.GlobalContext.application,
                    judge,
                    updated.id,
                )
            } else {
                JudgeSyncWorker.cancelFor(
                    com.ojnexus.core.ui.GlobalContext.application,
                    judge,
                    updated.id,
                )
            }
        }
    }

    fun syncAll() {
        syncAll(state.value.connections)
    }

    fun retrySync(request: SyncRetryRequest) {
        if (!retryInFlightFlag.compareAndSet(false, true)) return
        _retryingOperationId.value = request.operationId
        viewModelScope.launch {
            try {
                syncRetryDispatcher(request)
            } finally {
                _retryingOperationId.value = null
                retryInFlightFlag.set(false)
            }
        }
    }

    internal fun syncAll(connections: List<JudgeConnectionUi>) {
        if (!syncAllInFlightFlag.compareAndSet(false, true)) return
        _syncAllInFlight.value = true
        val targets = eligibleConnectorSyncRows(connections).mapNotNull { row ->
            connections.firstOrNull { it.judge == row.judge }?.account?.let { account ->
                row.judge to account.id
            }
        }
        if (targets.isEmpty()) {
            syncAllInFlightFlag.set(false)
            _syncAllInFlight.value = false
            return
        }
        viewModelScope.launch {
            try {
                targets.forEach { (judge, accountId) -> queueManualSync(judge, accountId) }
            } finally {
                syncAllInFlightFlag.set(false)
                _syncAllInFlight.value = false
            }
        }
    }

    private suspend fun queueManualSync(judge: JudgeId, accountId: Long) {
        dataRepository.markSyncQueued(judge, accountId)
        manualSyncEnqueuer(judge, accountId)
    }

    fun syncPhaseLabel(syncState: SyncStateEntity?): SyncPhase? =
        syncState?.state?.let { phase -> SyncPhase.entries.firstOrNull { it.name == phase } }

    private fun JudgeAccountRepository.ConnectError.toUiError(): ConnectError = when (this) {
        is JudgeAccountRepository.ConnectError.HandleEmpty -> ConnectError.HandleEmpty
        is JudgeAccountRepository.ConnectError.InvalidHandle -> ConnectError.InvalidHandle
        is JudgeAccountRepository.ConnectError.UserNotFound -> ConnectError.UserNotFound
        is JudgeAccountRepository.ConnectError.Network -> ConnectError.Network
        is JudgeAccountRepository.ConnectError.ApiFailure ->
            if (comment?.contains("limit", ignoreCase = true) == true) {
                ConnectError.RateLimited
            } else {
                ConnectError.ApiFailed
            }
    }

}

internal fun shouldScheduleJudgeSync(capabilities: Set<JudgeCapability>): Boolean =
    JudgeCapability.BACKGROUND_SYNC in capabilities

internal fun syncPhaseLabel(syncState: SyncStateEntity?): String? =
    syncState?.state?.let { phase -> SyncPhase.entries.firstOrNull { it.name == phase }?.name }

internal fun syncStageName(syncState: SyncStateEntity?): String? =
    syncState
        ?.takeIf { it.state == SyncPhase.SYNCING.name }
        ?.currentStage

internal fun syncErrorLabelKey(errorType: String?): String = when {
    errorType?.contains("RateLimited", ignoreCase = true) == true -> "sync_error_rate_limited"
    errorType?.contains("UserNotFound", ignoreCase = true) == true -> "sync_error_user_not_found"
    errorType?.let { value ->
        listOf("Network", "Timeout", "ServerError").any(value::contains)
    } == true -> "sync_error_network"
    else -> "sync_error_api"
}
