package com.ojnexus.feature.submissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.R
import com.ojnexus.core.database.entity.SubmissionJobEntity
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.localizedString
import com.ojnexus.judge.luogu.open.LuoguOpenApiError
import com.ojnexus.judge.luogu.open.LuoguResultWorkScheduler
import com.ojnexus.judge.luogu.open.LuoguSubmissionCenter
import com.ojnexus.judge.luogu.open.pollLuoguOpenResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val RECENT_JOB_LIMIT = 20

sealed interface SubmissionCenterActionError {
    val requestId: String

    data class Generic(
        override val requestId: String,
        val message: String,
    ) : SubmissionCenterActionError
}

data class SubmissionCenterUiState(
    val jobs: List<SubmissionJobEntity>,
    val busyRequestIds: Set<String>,
    val queuedRequestIds: Set<String> = emptySet(),
    val recoveryAvailable: Boolean = false,
    val actionError: SubmissionCenterActionError?,
)

class SubmissionCenterViewModel(
    private val submissionCenter: LuoguSubmissionCenter,
    private val delayForResult: suspend (Long) -> Unit = { delay(it) },
    private val scheduler: LuoguResultWorkScheduler? = null,
) : ViewModel() {
    private val busyRequestIds = MutableStateFlow<Set<String>>(emptySet())
    private val queuedRequestIds = MutableStateFlow<Set<String>>(emptySet())
    private val actionError = MutableStateFlow<SubmissionCenterActionError?>(null)

    val state: StateFlow<Loadable<SubmissionCenterUiState>> = combine(
        submissionCenter.observeRecentJobs(RECENT_JOB_LIMIT),
        busyRequestIds,
        queuedRequestIds,
        actionError,
    ) { jobs, busyIds, queuedIds, error ->
        Loadable.Ready(
            SubmissionCenterUiState(
                jobs = jobs,
                busyRequestIds = busyIds,
                queuedRequestIds = queuedIds,
                recoveryAvailable = scheduler != null,
                actionError = error,
            ),
        )
    }
        .catch<Loadable<SubmissionCenterUiState>> {
            emit(Loadable.Failed(it.message ?: localizedString(R.string.error_load_failed)))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    fun queueRecovery(requestId: String) {
        val trimmed = requestId.trim().takeIf { it.isNotEmpty() } ?: return
        if (scheduler == null) return
        viewModelScope.launch {
            try {
                scheduler.enqueueNow(trimmed)
                queuedRequestIds.update { it + trimmed }
                actionError.update { current ->
                    if (current?.requestId == trimmed) null else current
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                actionError.value = error.toActionError(trimmed)
            }
        }
    }

    fun checkPending() {
        val ready = state.value as? Loadable.Ready ?: return
        pendingSubmissionRequestIds(ready.value.jobs).forEach(::checkResult)
    }

    fun queueFailed() {
        val ready = state.value as? Loadable.Ready ?: return
        failedSubmissionRequestIds(ready.value.jobs).forEach(::queueRecovery)
    }

    fun checkResult(requestId: String) {
        var shouldLaunch = false
        busyRequestIds.update { current ->
            if (requestId in current) {
                current
            } else {
                shouldLaunch = true
                current + requestId
            }
        }
        if (!shouldLaunch) return
        viewModelScope.launch {
            try {
                pollLuoguOpenResult(
                    requestId = requestId,
                    fetch = submissionCenter::refreshResult,
                    delayForResult = delayForResult,
                    awaitResultSignal = submissionCenter::awaitResultSignal,
                )
                actionError.update { current ->
                    if (current?.requestId == requestId) {
                        null
                    } else {
                        current
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                actionError.value = error.toActionError(requestId)
            } finally {
                busyRequestIds.update { it - requestId }
            }
        }
    }

    private fun Exception.toActionError(requestId: String): SubmissionCenterActionError = when (this) {
        LuoguOpenApiError.CredentialMissing ->
            SubmissionCenterActionError.Generic(requestId, message ?: "OpenApp credential is not configured")
        is LuoguOpenApiError.InvalidRequest ->
            SubmissionCenterActionError.Generic(requestId, message ?: "Open Platform request is invalid")
        LuoguOpenApiError.Unauthorized ->
            SubmissionCenterActionError.Generic(requestId, message ?: "Open Platform authorization failed")
        LuoguOpenApiError.Forbidden ->
            SubmissionCenterActionError.Generic(requestId, message ?: "Open Platform access is forbidden")
        LuoguOpenApiError.QuotaExceeded ->
            SubmissionCenterActionError.Generic(requestId, message ?: "Open Platform quota is insufficient")
        LuoguOpenApiError.NotFound ->
            SubmissionCenterActionError.Generic(requestId, message ?: "Open Platform resource was not found")
        is LuoguOpenApiError.Network ->
            SubmissionCenterActionError.Generic(requestId, message ?: "Open Platform network error")
        is LuoguOpenApiError.Http, LuoguOpenApiError.MalformedResponse ->
            SubmissionCenterActionError.Generic(requestId, message ?: "Open Platform server error")
        else -> SubmissionCenterActionError.Generic(requestId, message ?: localizedString(R.string.error_load_failed))
    }
}
