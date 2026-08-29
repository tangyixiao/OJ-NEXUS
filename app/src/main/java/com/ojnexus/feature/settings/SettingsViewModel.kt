package com.ojnexus.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.JudgeProfileEntity
import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.codeforces.CodeforcesSyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val account: JudgeAccountEntity?,
    val profile: JudgeProfileEntity?,
    val syncState: SyncStateEntity?,
)

class SettingsViewModel(
    private val accountRepository: JudgeAccountRepository,
    private val syncRepository: CodeforcesSyncRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        accountRepository.observeActive(JudgeId.CODEFORCES),
        syncRepository.observeProfile(JudgeId.CODEFORCES),
        syncRepository.observeSyncStateFlow(JudgeId.CODEFORCES),
    ) { account, profile, syncState ->
        SettingsUiState(account = account, profile = profile, syncState = syncState)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(null, null, null),
    )

    sealed interface ConnectError {
        data object HandleEmpty : ConnectError
        data object InvalidHandle : ConnectError
        data object UserNotFound : ConnectError
        data object RateLimited : ConnectError
        data object Network : ConnectError
        data object ApiFailed : ConnectError
    }

    private val connectError = MutableStateFlow<ConnectError?>(null)
    val error: StateFlow<ConnectError?> = connectError.asStateFlow()

    private val connecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = connecting.asStateFlow()

    fun connect(handle: String) {
        if (connecting.value) return
        if (handle.isBlank()) {
            connectError.value = ConnectError.HandleEmpty
            return
        }
        connectError.value = null
        connecting.value = true
        viewModelScope.launch {
            try {
                val account = accountRepository.connect(JudgeId.CODEFORCES, handle)
                // Initial sync runs as unique background work; the user can leave the page.
                com.ojnexus.judge.sync.JudgeSyncWorker.enqueueManual(
                    context = com.ojnexus.core.ui.GlobalContext.application,
                    judge = JudgeId.CODEFORCES,
                    accountId = account.id,
                    force = true,
                )
                com.ojnexus.judge.sync.JudgeSyncWorker.enqueuePeriodic(
                    context = com.ojnexus.core.ui.GlobalContext.application,
                    judge = JudgeId.CODEFORCES,
                    accountId = account.id,
                )
            } catch (e: JudgeAccountRepository.ConnectError) {
                connectError.value = when (e) {
                    is JudgeAccountRepository.ConnectError.HandleEmpty -> ConnectError.HandleEmpty
                    is JudgeAccountRepository.ConnectError.InvalidHandle -> ConnectError.InvalidHandle
                    is JudgeAccountRepository.ConnectError.UserNotFound -> ConnectError.UserNotFound
                    is JudgeAccountRepository.ConnectError.Network -> ConnectError.Network
                    is JudgeAccountRepository.ConnectError.ApiFailure ->
                        if (e.comment?.contains("limit", ignoreCase = true) == true) {
                            ConnectError.RateLimited
                        } else {
                            ConnectError.ApiFailed
                        }
                }
            } finally {
                connecting.value = false
            }
        }
    }

    fun disconnect(accountId: Long, removeCache: Boolean) {
        viewModelScope.launch {
            accountRepository.disconnect(accountId, removeCache)
            com.ojnexus.judge.sync.JudgeSyncWorker.cancelFor(
                com.ojnexus.core.ui.GlobalContext.application,
                JudgeId.CODEFORCES,
                accountId,
            )
        }
    }

    fun syncNow(accountId: Long) {
        com.ojnexus.judge.sync.JudgeSyncWorker.enqueueManual(
            context = com.ojnexus.core.ui.GlobalContext.application,
            judge = JudgeId.CODEFORCES,
            accountId = accountId,
            force = true,
        )
    }

    fun clearError() {
        connectError.value = null
    }

    fun syncPhaseLabel(syncState: SyncStateEntity?): SyncPhase? =
        syncState?.state?.let { phase -> SyncPhase.entries.firstOrNull { it.name == phase } }
}
