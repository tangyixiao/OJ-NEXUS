package com.ojnexus.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.DataError
import com.ojnexus.core.data.DataResult
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.data.repository.ReviewRepository
import com.ojnexus.core.data.repository.TrainingRepository
import com.ojnexus.core.domain.SessionClock
import com.ojnexus.core.model.Problem
import com.ojnexus.core.model.SessionProblem
import com.ojnexus.core.model.TrainingSession
import com.ojnexus.core.model.TrainingType
import com.ojnexus.core.model.Verdict
import com.ojnexus.core.ui.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Summary of a finished session, computed from attempts inside the session window. */
data class SessionSummary(
    val durationMs: Long,
    val problems: List<SessionProblem>,
) {
    val problemCount: Int get() = problems.size
    val solvedCount: Int get() = problems.count { it.solved }
    val attemptCount: Int get() = problems.sumOf { it.attempts }
    val acCount: Int get() = problems.count { it.solved }
    val waCount: Int get() = (attemptCount - acCount).coerceAtLeast(0)

    /** Mean difficulty of the problems solved in this session; null when none solved or unknown. */
    val averageDifficulty: Int?
        get() = problems
            .filter { it.solved }
            .mapNotNull { it.difficulty }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toInt()
}

/** Session action failures the UI can express through string resources. */
sealed interface SessionActionError {
    data object ActiveExists : SessionActionError
    data class Generic(val message: String) : SessionActionError
}

data class SessionSurfaceState(
    /** null = no live session on the active route → show the creation form. */
    val session: TrainingSession?,
    /** Live session problem count; null when not live. */
    val liveProblemCount: Int?,
    /** Fully computed once the session is finished. */
    val summary: SessionSummary?,
    /** Reactive progress rows for the active or historical session. */
    val problems: List<SessionProblem> = emptyList(),
    val actionError: SessionActionError? = null,
)

class SessionViewModel(
    private val sessionId: Long?,
    private val trainingRepository: TrainingRepository,
    private val problemRepository: ProblemRepository,
    private val reviewRepository: ReviewRepository,
) : ViewModel() {

    /**
     * 1Hz wall-clock ticker. Collected ONLY by [elapsedMs] — the rest of the screen does not
     * recompose per second.
     */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    private val sessionFlow = when (sessionId) {
        null -> trainingRepository.observeActiveSession()
        else -> trainingRepository.observeSession(sessionId)
    }

    private val _actionError = MutableStateFlow<SessionActionError?>(null)
    private val _lastLoggedProblemId = MutableStateFlow<Long?>(null)
    private val _actionInFlight = MutableStateFlow(false)

    /** Latest local session action error, or null after a successful action. */
    val actionError: StateFlow<SessionActionError?> = _actionError.asStateFlow()

    /** Problem id for the most recent successful local verdict action. */
    val lastLoggedProblemId: StateFlow<Long?> = _lastLoggedProblemId.asStateFlow()

    /** True while the local verdict transaction is running. */
    val actionInFlight: StateFlow<Boolean> = _actionInFlight.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionProblems: StateFlow<List<SessionProblem>> = sessionFlow
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                trainingRepository.observeSessionProblems(session.id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val problems: StateFlow<List<Problem>> = problemRepository.observeLibrary()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val liveProblemCount = sessionFlow.flatMapLatest { session ->
        if (session != null && session.finishedAt == null) {
            trainingRepository.observeSessionProblemCount(session.id)
        } else {
            kotlinx.coroutines.flow.flowOf<Int?>(null)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val finishedSummary = sessionFlow.flatMapLatest { session ->
        if (session != null && session.finishedAt != null) {
            flow<SessionSummary?> {
                emit(
                    SessionSummary(
                        durationMs = SessionClock.elapsedMs(
                            startedAt = session.startedAt,
                            totalPausedMs = session.totalPausedMs,
                            pausedAt = session.pausedAt,
                            finishedAt = session.finishedAt,
                            now = System.currentTimeMillis(),
                        ),
                        problems = trainingRepository.sessionProblems(session.id),
                    ),
                )
            }
        } else {
            kotlinx.coroutines.flow.flowOf<SessionSummary?>(null)
        }
    }

    val state: StateFlow<Loadable<SessionSurfaceState>> = combine(
        sessionFlow,
        liveProblemCount,
        finishedSummary,
        actionError,
        sessionProblems,
    ) { session, liveCount, summary, error, problems ->
        Loadable.Ready(
            SessionSurfaceState(
                session = session,
                liveProblemCount = liveCount,
                summary = summary,
                problems = problems,
                actionError = error,
            ),
        )
    }
        .catch<Loadable<SessionSurfaceState>> {
            emit(Loadable.Failed(it.message ?: com.ojnexus.core.ui.localizedString(com.ojnexus.R.string.error_load_failed)))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    val elapsedMs: StateFlow<Long> = combine(sessionFlow, ticker) { session, now ->
        session?.let {
            SessionClock.elapsedMs(
                startedAt = it.startedAt,
                totalPausedMs = it.totalPausedMs,
                pausedAt = it.pausedAt,
                finishedAt = it.finishedAt,
                now = now,
            )
        } ?: 0L
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun createSession(type: TrainingType, targetDurationMin: Int?, targetTag: String?, problemIds: List<Long>) {
        viewModelScope.launch {
            when (val result = trainingRepository.createAndStartSession(type, targetDurationMin, targetTag, problemIds)) {
                is DataResult.Success -> _actionError.value = null
                is DataResult.Failure -> _actionError.value = result.error.toActionError()
            }
        }
    }

    fun logAttempt(problemId: Long, verdict: Verdict) {
        if (!_actionInFlight.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                when (val result = problemRepository.addAttempt(problemId, verdict)) {
                    is DataResult.Success -> {
                        _actionError.value = null
                        _lastLoggedProblemId.value = problemId
                    }
                    is DataResult.Failure -> _actionError.value = result.error.toActionError()
                }
            } finally {
                _actionInFlight.value = false
            }
        }
    }

    fun pause(sessionId: Long) = launchAction { trainingRepository.pauseSession(sessionId) }

    fun resume(sessionId: Long) = launchAction { trainingRepository.resumeSession(sessionId) }

    fun cancel(sessionId: Long) = launchAction { trainingRepository.cancelSession(sessionId) }

    fun finish(sessionId: Long) = launchAction { trainingRepository.finishSession(sessionId) }

    fun scheduleReviews(problemIds: List<Long>) {
        viewModelScope.launch {
            when (val result = reviewRepository.scheduleReviews(problemIds)) {
                is DataResult.Success -> _actionError.value = null
                is DataResult.Failure -> _actionError.value = result.error.toActionError()
            }
        }
    }

    private fun launchAction(block: suspend () -> DataResult<Unit>) {
        viewModelScope.launch {
            when (val result = block()) {
                is DataResult.Success -> _actionError.value = null
                is DataResult.Failure -> _actionError.value = result.error.toActionError()
            }
        }
    }

    private fun DataError.toActionError(): SessionActionError = when (this) {
        is DataError.NotFound -> SessionActionError.Generic(message)
        is DataError.DuplicateProblem -> SessionActionError.Generic(message)
        is DataError.Storage ->
            if (message.startsWith("A session is already active")) {
                SessionActionError.ActiveExists
            } else {
                SessionActionError.Generic(message)
            }
    }
}
