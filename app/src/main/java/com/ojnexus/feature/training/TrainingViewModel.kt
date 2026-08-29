package com.ojnexus.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.data.repository.ReviewRepository
import com.ojnexus.core.data.repository.TrainingRepository
import com.ojnexus.core.model.Problem
import com.ojnexus.core.model.ReviewQueueItem
import com.ojnexus.core.model.TaskType
import com.ojnexus.core.model.TrainingType
import com.ojnexus.core.ui.Loadable
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrainingViewModel(
    private val trainingRepository: TrainingRepository,
    private val reviewRepository: ReviewRepository,
    private val problemRepository: ProblemRepository,
    private val clock: Clock,
) : ViewModel() {

    /**
     * Fixed at ViewModel creation: the TODAY list belongs to the calendar day the user opened
     * the screen. A day rollover is picked up on the next app launch (documented limitation).
     */
    private val todayEpochDay: Long = LocalDate.ofInstant(clock.instant(), clock.zone).toEpochDay()

    /** Manual re-sync trigger for flows whose sources change outside Room (none currently). */
    private val refresh = MutableStateFlow(0)

    val state: StateFlow<Loadable<TrainingUiState>> = combine(
        reviewRepository.observeQueue(),
        trainingRepository.observeTasks(todayEpochDay),
        trainingRepository.observeActiveSession(),
        trainingRepository.observeHistory(limit = 10),
        refresh,
    ) { queue, tasks, activeSession, history, _ ->
        Loadable.Ready(
            TrainingUiState(
                todayEpochDay = todayEpochDay,
                tasks = tasks,
                reviews = bucketReviews(todayEpochDay, queue),
                activeSession = activeSession,
                history = history,
            ),
        )
    }
        .catch<Loadable<TrainingUiState>> { emit(Loadable.Failed(it.message ?: "Load failed")) }
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
                dateEpochDay = todayEpochDay,
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
        viewModelScope.launch { trainingRepository.clearCompletedTasks(todayEpochDay) }
    }

    fun startSession(
        type: TrainingType,
        targetDurationMin: Int?,
        targetTag: String?,
        problemIds: List<Long>,
    ) {
        viewModelScope.launch {
            trainingRepository.createAndStartSession(type, targetDurationMin, targetTag, problemIds)
            refresh.update { it + 1 }
        }
    }
}
