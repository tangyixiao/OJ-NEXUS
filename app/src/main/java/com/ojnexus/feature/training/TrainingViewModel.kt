package com.ojnexus.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.R
import com.ojnexus.core.data.DataError
import com.ojnexus.core.data.DataResult
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.data.repository.ReviewRepository
import com.ojnexus.core.data.repository.TrainingRepository
import com.ojnexus.core.model.Problem
import com.ojnexus.core.model.ReviewQueueItem
import com.ojnexus.core.model.TaskType
import com.ojnexus.core.model.TrainingType
import com.ojnexus.core.domain.TrainingCandidate
import com.ojnexus.core.domain.TrainingPlanner
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.localizedString
import com.ojnexus.core.time.LocalDaySource
import com.ojnexus.core.time.SystemLocalDaySource
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TrainingSessionStartState {
    data object Idle : TrainingSessionStartState
    data object Starting : TrainingSessionStartState
    data object Started : TrainingSessionStartState
    data class Failed(val message: String) : TrainingSessionStartState
}

fun trainingSessionStartState(
    result: DataResult<Long>,
    genericError: String,
    activeSessionError: String,
): TrainingSessionStartState = when (result) {
    is DataResult.Success -> TrainingSessionStartState.Started
    is DataResult.Failure -> TrainingSessionStartState.Failed(
        if (result.error is DataError.Storage && result.error.message.startsWith("A session is already active")) {
            activeSessionError
        } else {
            genericError
        },
    )
}

fun canDismissSessionDialog(startState: TrainingSessionStartState): Boolean =
    startState !is TrainingSessionStartState.Starting

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingViewModel(
    private val trainingRepository: TrainingRepository,
    private val reviewRepository: ReviewRepository,
    private val problemRepository: ProblemRepository,
    private val knowledgeRepository: com.ojnexus.core.data.repository.KnowledgeRepository,
    private val clock: Clock,
    private val errorMessage: () -> String = { localizedString(R.string.error_load_failed) },
    private val localDaySource: LocalDaySource = SystemLocalDaySource(clock),
) : ViewModel() {

    private val todayEpochDay = localDaySource.day.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        clock.instant().atZone(clock.zone).toLocalDate().toEpochDay(),
    )

    /** Manual re-sync trigger for flows whose sources change outside Room (none currently). */
    private val refresh = MutableStateFlow(0)
    private val sessionStart = MutableStateFlow<TrainingSessionStartState>(TrainingSessionStartState.Idle)

    val sessionStartState: StateFlow<TrainingSessionStartState> = sessionStart

    private data class TrainingSignals(
        val knowledge: List<com.ojnexus.core.data.repository.KnowledgeAreaState>,
        val candidates: List<com.ojnexus.core.database.dao.TrainingCandidateRow>,
    )

    val state: StateFlow<Loadable<TrainingUiState>> = todayEpochDay.flatMapLatest { today ->
        combine(
            reviewRepository.observeQueue(),
            trainingRepository.observeTasks(today),
            trainingRepository.observeActiveSession(),
            trainingRepository.observeHistory(limit = 10),
            combine(
                knowledgeRepository.observeMastery(),
                trainingRepository.observeCandidateRows(today),
                refresh,
            ) { knowledge, candidates, _ -> TrainingSignals(knowledge, candidates) },
        ) { queue, tasks, activeSession, history, signals ->
            Loadable.Ready(
                TrainingUiState(
                    todayEpochDay = today,
                    tasks = tasks,
                    reviews = bucketReviews(today, queue),
                    activeSession = activeSession,
                    history = history,
                    knowledge = signals.knowledge,
                    recommendations = signals.candidates
                        .map { row ->
                            val priority = TrainingPlanner.rank(
                                TrainingCandidate(
                                    solved = row.solved,
                                    attemptCount = row.attemptCount,
                                    failureCount = row.failureCount,
                                    reviewDue = row.reviewDue,
                                    difficulty = row.difficulty,
                                    targetDifficulty = null,
                                    coverageValue = row.coverageValue,
                                ),
                            )
                            TrainingRecommendation(
                                problemId = row.id,
                                judge = row.judge,
                                externalId = row.externalId,
                                title = row.title,
                                priority = priority.priority,
                                reasons = priority.reasons,
                            )
                        }
                        .sortedWith(compareByDescending<TrainingRecommendation> { it.priority }.thenBy { it.problemId }),
                ),
            )
        }
    }
        .catch<Loadable<TrainingUiState>> {
            emit(Loadable.Failed(it.message ?: com.ojnexus.core.ui.localizedString(com.ojnexus.R.string.error_load_failed)))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    /** Library entries for the session problem picker and task linking. */
    val problems: StateFlow<List<Problem>> = problemRepository.observeLibrary()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun bucketReviews(today: Long, queue: List<ReviewQueueItem>): ReviewBuckets =
        ReviewBuckets(
            overdue = queue.filter { it.dueDayIndex < today },
            dueToday = queue.filter { it.dueDayIndex == today },
            upcoming = queue.filter { it.dueDayIndex > today },
        )

    fun addTask(type: TaskType, problemId: Long?, title: String?) {
        viewModelScope.launch {
            trainingRepository.addTask(
                dateEpochDay = todayEpochDay.value,
                type = type,
                problemId = problemId,
                title = title?.takeIf { it.isNotBlank() },
                priority = 50,
            )
        }
    }

    fun toggleTask(taskId: Long, completed: Boolean) {
        viewModelScope.launch { trainingRepository.setTaskCompleted(taskId, !completed) }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { trainingRepository.deleteTask(taskId) }
    }

    fun clearCompleted() {
        viewModelScope.launch { trainingRepository.clearCompletedTasks(todayEpochDay.value) }
    }

    fun startSession(
        type: TrainingType,
        targetDurationMin: Int?,
        targetTag: String?,
        problemIds: List<Long>,
    ) {
        if (sessionStart.value is TrainingSessionStartState.Starting) return
        sessionStart.value = TrainingSessionStartState.Starting
        viewModelScope.launch {
            try {
                when (val result = trainingRepository.createAndStartSession(type, targetDurationMin, targetTag, problemIds)) {
                    is DataResult.Success -> {
                        refresh.update { it + 1 }
                        sessionStart.value = TrainingSessionStartState.Started
                    }
                    is DataResult.Failure -> {
                        sessionStart.value = trainingSessionStartState(
                            result = result,
                            genericError = errorMessage(),
                            activeSessionError = localizedString(R.string.session_active_exists),
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                sessionStart.value = TrainingSessionStartState.Failed(errorMessage())
            }
        }
    }

    fun clearSessionStartState() {
        sessionStart.value = TrainingSessionStartState.Idle
    }

}
