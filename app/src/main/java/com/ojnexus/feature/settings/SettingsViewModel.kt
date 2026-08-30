package com.ojnexus.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.preferences.UserPreferences
import com.ojnexus.core.data.preferences.UserPreferencesRepository
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

class SettingsViewModel(
    private val accountRepository: JudgeAccountRepository,
    dataRepository: JudgeDataRepository,
    registry: JudgeRegistry,
    private val backupRepository: BackupRepository,
    private val preferencesRepository: UserPreferencesRepository,
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
    private val backupResult = MutableStateFlow<Boolean?>(null)
    val backup: StateFlow<Boolean?> = backupResult.asStateFlow()
    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferences(),
    )

    fun exportBackup(resolver: ContentResolver, destination: Uri) {
        viewModelScope.launch {
            backupResult.value = backupRepository.exportTo(resolver, destination)
        }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setReduceMotion(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHapticsEnabled(enabled) }
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
            JudgeSyncWorker.cancelFor(
                com.ojnexus.core.ui.GlobalContext.application,
                judge,
                account.id,
            )
        }
    }

    fun syncNow(account: JudgeAccountEntity) {
        val judge = JudgeId.fromId(account.judge) ?: return
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
}
