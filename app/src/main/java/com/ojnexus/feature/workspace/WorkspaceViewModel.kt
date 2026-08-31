package com.ojnexus.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.judge.luogu.open.LuoguOpenApiError
import com.ojnexus.judge.luogu.open.LuoguOpenEvaluation
import com.ojnexus.judge.luogu.open.LuoguOpenGateway
import com.ojnexus.judge.luogu.open.LuoguOpenResult
import com.ojnexus.judge.luogu.open.pollLuoguOpenResult
import com.ojnexus.judge.luogu.open.LuoguLanguages
import com.ojnexus.judge.luogu.open.LuoguProblemJudgeRequest
import com.ojnexus.judge.luogu.open.LuoguRunRequest
import com.ojnexus.judge.luogu.open.LuoguSubmissionHistory
import com.ojnexus.judge.luogu.open.OpenAppCredentialStore
import com.ojnexus.judge.luogu.open.SubmissionJobKind
import com.ojnexus.judge.luogu.open.SubmissionJobStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class WorkspaceMode { RUN, SUBMIT }

enum class WorkspaceResultState { IDLE, PENDING, READY }

data class WorkspaceState(
    val pid: String,
    val title: String?,
    val code: String = "",
    val input: String = "",
    val language: String = LuoguLanguages.DEFAULT_ID,
    val o2: Boolean = false,
    val mode: WorkspaceMode = WorkspaceMode.RUN,
    val customRunAvailable: Boolean = true,
    val credentialConfigured: Boolean = false,
    val busy: Boolean = false,
    val requestId: String? = null,
    val resultState: WorkspaceResultState = WorkspaceResultState.IDLE,
    val evaluation: com.ojnexus.judge.luogu.open.LuoguOpenEvaluation? = null,
    val error: WorkspaceError? = null,
)

enum class WorkspaceError {
    CREDENTIAL_MISSING,
    INVALID_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    QUOTA_EXCEEDED,
    NOT_FOUND,
    UNSUPPORTED_OPERATION,
    NETWORK,
    SERVER,
    PREVIOUS_REQUEST_FAILED,
}

class WorkspaceViewModel(
    pid: String,
    title: String?,
    private val gateway: LuoguOpenGateway,
    private val credentialStore: OpenAppCredentialStore,
    testScope: CoroutineScope? = null,
    private val history: LuoguSubmissionHistory? = null,
    private val delayForResult: suspend (Long) -> Unit = { delay(it) },
) : ViewModel() {
    private val workScope = testScope ?: viewModelScope
    private val mutableState = MutableStateFlow(
        WorkspaceState(
            pid = pid,
            title = title,
            mode = if (gateway.supportsCustomInputRun) WorkspaceMode.RUN else WorkspaceMode.SUBMIT,
            customRunAvailable = gateway.supportsCustomInputRun,
        ),
    )
    private val submissionStarted = AtomicBoolean(false)
    val state: StateFlow<WorkspaceState> = mutableState.asStateFlow()

    init {
        workScope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutableState.update { it.copy(credentialConfigured = credentialStore.read() != null) }
        }
        workScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val job = history?.latestForProblem(pid) ?: return@launch
            mutableState.update {
                if (submissionStarted.get()) {
                    it
                } else {
                    val ready = job.status == SubmissionJobStatus.READY.name
                    it.copy(
                        requestId = job.requestId,
                        language = job.language,
                        mode = if (job.kind == SubmissionJobKind.PROBLEM.name || !gateway.supportsCustomInputRun) {
                            WorkspaceMode.SUBMIT
                        } else {
                            WorkspaceMode.RUN
                        },
                        resultState = when (job.status) {
                            SubmissionJobStatus.READY.name -> WorkspaceResultState.READY
                            SubmissionJobStatus.FAILED.name -> WorkspaceResultState.IDLE
                            else -> WorkspaceResultState.PENDING
                        },
                        evaluation = if (ready) {
                            LuoguOpenEvaluation(
                                requestId = job.requestId,
                                trackId = job.trackId,
                                type = "judge",
                                compileSuccess = job.compileSuccess,
                                compileMessage = job.compileMessage,
                                status = job.judgeStatus,
                                score = job.score,
                                timeMs = job.executionTimeMs?.toLong(),
                                memoryKiB = job.memoryKiB?.toLong(),
                                output = job.output,
                                exitCode = job.exitCode,
                            )
                        } else {
                            null
                        },
                        error = if (job.status == SubmissionJobStatus.FAILED.name) {
                            WorkspaceError.PREVIOUS_REQUEST_FAILED
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    fun setCode(value: String) = mutableState.update { it.copy(code = value, error = null) }

    fun setInput(value: String) = mutableState.update { it.copy(input = value, error = null) }

    fun setLanguage(value: String) = mutableState.update { it.copy(language = value, error = null) }

    fun setMode(value: WorkspaceMode) = mutableState.update {
        it.copy(mode = value, resultState = WorkspaceResultState.IDLE, evaluation = null, error = null)
    }

    fun setO2(value: Boolean) = mutableState.update { it.copy(o2 = value) }

    fun submit() {
        if (mutableState.value.busy) return
        submissionStarted.set(true)
        val snapshot = mutableState.value
        mutableState.update { it.copy(busy = true, error = null, resultState = WorkspaceResultState.IDLE) }
        workScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val response = if (snapshot.mode == WorkspaceMode.SUBMIT) {
                    gateway.submitProblem(
                        LuoguProblemJudgeRequest(
                            pid = snapshot.pid,
                            lang = snapshot.language,
                            o2 = snapshot.o2,
                            code = snapshot.code,
                        ),
                    )
                } else {
                    gateway.run(
                        LuoguRunRequest(
                            input = snapshot.input,
                            lang = snapshot.language,
                            o2 = snapshot.o2,
                            code = snapshot.code,
                        ),
                    )
                }
                mutableState.update {
                    it.copy(
                        requestId = response.requestId,
                        resultState = WorkspaceResultState.PENDING,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.toWorkspaceError()) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun checkResult() {
        val requestId = mutableState.value.requestId ?: return
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        workScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                when (val result = pollResult(requestId)) {
                    LuoguOpenResult.Pending -> mutableState.update {
                        it.copy(resultState = WorkspaceResultState.PENDING)
                    }
                    is LuoguOpenResult.InProgress -> mutableState.update {
                        it.copy(
                            resultState = WorkspaceResultState.PENDING,
                            evaluation = result.evaluation,
                        )
                    }
                    is LuoguOpenResult.Ready -> mutableState.update {
                        it.copy(
                            resultState = WorkspaceResultState.READY,
                            evaluation = result.evaluation,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.toWorkspaceError()) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun pollResult(requestId: String): LuoguOpenResult {
        return pollLuoguOpenResult(
            requestId = requestId,
            fetch = gateway::fetchResult,
            delayForResult = delayForResult,
            awaitResultSignal = gateway::awaitResultSignal,
        )
    }

    private fun Exception.toWorkspaceError(): WorkspaceError = when (this) {
        LuoguOpenApiError.CredentialMissing -> WorkspaceError.CREDENTIAL_MISSING
        is LuoguOpenApiError.InvalidRequest -> WorkspaceError.INVALID_REQUEST
        LuoguOpenApiError.Unauthorized -> WorkspaceError.UNAUTHORIZED
        LuoguOpenApiError.Forbidden -> WorkspaceError.FORBIDDEN
        LuoguOpenApiError.QuotaExceeded -> WorkspaceError.QUOTA_EXCEEDED
        LuoguOpenApiError.NotFound -> WorkspaceError.NOT_FOUND
        LuoguOpenApiError.UnsupportedOperation -> WorkspaceError.UNSUPPORTED_OPERATION
        is LuoguOpenApiError.Network -> WorkspaceError.NETWORK
        is LuoguOpenApiError.Http, LuoguOpenApiError.MalformedResponse -> WorkspaceError.SERVER
        else -> WorkspaceError.SERVER
    }

}
