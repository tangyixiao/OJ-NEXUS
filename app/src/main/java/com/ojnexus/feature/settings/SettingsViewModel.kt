package com.ojnexus.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.preferences.UserPreferences
import com.ojnexus.core.data.preferences.UserPreferencesRepository
import com.ojnexus.core.designsystem.NexusThemeSlot
import com.ojnexus.core.data.repository.BackupRepository
import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.repository.JudgeDataRepository
import com.ojnexus.core.data.sync.SyncPhase
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
import com.ojnexus.judge.luogu.open.LuoguOpenQuotaReader
import com.ojnexus.judge.luogu.open.LuoguOpenQuotaSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JudgeConnectionUi(
    val judge: JudgeId,
    val account: JudgeAccountEntity?,
    val profile: JudgeProfileEntity?,
    val syncState: SyncStateEntity?,
    val capabilities: Set<JudgeCapability>,
    val reliability: DataSourceReliability,
)

data class SettingsUiState(val connections: List<JudgeConnectionUi>)

enum class BackupOperation { EXPORT, IMPORT }

data class BackupResult(val operation: BackupOperation, val success: Boolean)

data class OpenAppUiState(
    val configured: Boolean = false,
    val saving: Boolean = false,
    val error: Boolean = false,
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
    dataRepository: JudgeDataRepository,
    private val registry: JudgeRegistry,
    private val backupRepository: BackupRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val openAppCredentialStore: OpenAppCredentialStore? = null,
    private val openAppQuotaReader: LuoguOpenQuotaReader? = null,
) : ViewModel() {
    private val judges = registry.supportedJudges().sortedBy { it.ordinal }

    val state: StateFlow<SettingsUiState> = dataRepository.observeConnections().map { snapshot ->
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
    private val backupResult = MutableStateFlow<BackupResult?>(null)
    val backup: StateFlow<BackupResult?> = backupResult.asStateFlow()
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
        if (user.isBlank() || secret.isBlank()) {
            openAppState.update { it.copy(error = true) }
            return
        }
        openAppState.update { it.copy(saving = true, error = false) }
        viewModelScope.launch {
            try {
                store.write(OpenAppCredential(user.trim(), secret.trim()))
                openAppState.value = OpenAppUiState(configured = true)
            } catch (_: Exception) {
                openAppState.update { it.copy(saving = false, error = true) }
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
                    it.copy(checkingQuota = false, quotaError = error.toQuotaError())
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
                if (!shouldScheduleJudgeSync(registry.adapter(judge).capabilities)) return@launch
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

    fun syncNow(account: JudgeAccountEntity) {
        val judge = JudgeId.fromId(account.judge) ?: return
        if (!shouldScheduleJudgeSync(registry.adapter(judge).capabilities)) return
        JudgeSyncWorker.enqueueManual(
            com.ojnexus.core.ui.GlobalContext.application,
            judge,
            account.id,
            true,
        )
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

    private fun LuoguOpenApiError.toQuotaError(): OpenAppQuotaError = when (this) {
        LuoguOpenApiError.CredentialMissing -> OpenAppQuotaError.CREDENTIAL_MISSING
        LuoguOpenApiError.Unauthorized -> OpenAppQuotaError.UNAUTHORIZED
        LuoguOpenApiError.Forbidden -> OpenAppQuotaError.FORBIDDEN
        LuoguOpenApiError.QuotaExceeded -> OpenAppQuotaError.QUOTA_EXCEEDED
        LuoguOpenApiError.NotFound -> OpenAppQuotaError.NOT_FOUND
        is LuoguOpenApiError.Network -> OpenAppQuotaError.NETWORK
        else -> OpenAppQuotaError.API
    }
}

internal fun shouldScheduleJudgeSync(capabilities: Set<JudgeCapability>): Boolean =
    JudgeCapability.BACKGROUND_SYNC in capabilities
