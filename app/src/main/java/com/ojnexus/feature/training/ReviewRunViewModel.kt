package com.ojnexus.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.DataResult
import com.ojnexus.core.data.repository.ReviewRepository
import com.ojnexus.core.domain.ScheduledReview
import com.ojnexus.core.model.ReviewQueueItem
import com.ojnexus.core.model.ReviewResult
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.localizedString
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewRunUiState(
    val captured: List<ReviewQueueItem>,
    val completedCount: Int,
    val active: ReviewQueueItem?,
    val completedItem: ReviewQueueItem?,
    val lastOutcome: ReviewOutcome?,
    val error: String?,
    val isRecording: Boolean,
) {
    val total: Int get() = captured.size
    val left: Int get() = (total - completedCount).coerceAtLeast(0)
    val isComplete: Boolean get() = total > 0 && completedCount >= total
}

private data class ReviewRunInternalState(
    val captured: List<ReviewQueueItem>? = null,
    val completedIds: Set<Long> = emptySet(),
    val currentId: Long? = null,
    val completedItem: ReviewQueueItem? = null,
    val lastOutcome: ReviewOutcome? = null,
    val error: String? = null,
    val isRecording: Boolean = false,
)

class ReviewRunViewModel(
    private val reviewRepository: ReviewRepository,
    private val clock: Clock,
    private val localizedErrorMessage: () -> String = {
        localizedString(com.ojnexus.R.string.error_load_failed)
    },
    private val completeReview: suspend (Long, ReviewResult) -> DataResult<ScheduledReview> =
        reviewRepository::completeReview,
) : ViewModel() {

    private val queue = MutableStateFlow<List<ReviewQueueItem>>(emptyList())
    private val internal = MutableStateFlow(ReviewRunInternalState())
    private val todayEpochDay = clock.instant().atZone(clock.zone).toLocalDate().toEpochDay()

    init {
        viewModelScope.launch {
            reviewRepository.observeQueue()
                .catch { error ->
                    internal.update { it.copy(error = localizedErrorMessage()) }
                }
                .collect { items ->
                    queue.value = items
                    if (internal.value.captured == null) {
                        val captured = captureReviewRunQueue(items, todayEpochDay)
                        internal.update {
                            it.copy(
                                captured = captured,
                                currentId = captured.firstOrNull()?.problemId,
                            )
                        }
                    }
                }
        }
    }

    val state: StateFlow<Loadable<ReviewRunUiState>> = combine(queue, internal) { items, run ->
        val captured = run.captured.orEmpty()
        val activeSnapshot = captured.firstOrNull {
            it.problemId == run.currentId && it.problemId !in run.completedIds
        }
        val active = items.firstOrNull { it.problemId == run.currentId }
            ?.takeUnless { it.problemId in run.completedIds }
            ?: activeSnapshot
        Loadable.Ready(
            ReviewRunUiState(
                captured = captured,
                completedCount = run.completedIds.count { id -> id in captured.map { it.problemId } },
                active = active,
                completedItem = run.completedItem,
                lastOutcome = run.lastOutcome,
                error = run.error,
                isRecording = run.isRecording,
            ),
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Loadable.Loading)

    fun record(result: ReviewResult) {
        val run = internal.value
        val currentId = run.currentId ?: return
        val item = run.captured?.firstOrNull { it.problemId == currentId } ?: return
        if (run.isRecording || currentId in run.completedIds) return

        internal.update { it.copy(isRecording = true, error = null) }
        viewModelScope.launch {
            try {
                when (val outcome = completeReview(currentId, result)) {
                    is DataResult.Success -> internal.update {
                        it.copy(
                            completedIds = it.completedIds + currentId,
                            completedItem = item,
                            lastOutcome = ReviewOutcome(
                                result = result,
                                nextStage = outcome.value.stage,
                                nextIntervalDays = outcome.value.intervalDays,
                            ),
                            error = null,
                            isRecording = false,
                        )
                    }

                    is DataResult.Failure -> internal.update {
                        it.copy(error = localizedErrorMessage(), isRecording = false)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                internal.update { it.copy(error = localizedErrorMessage(), isRecording = false) }
            }
        }
    }

    fun next() {
        val run = internal.value
        val captured = run.captured.orEmpty()
        val next = captured.firstOrNull { it.problemId !in run.completedIds }
        internal.update {
            it.copy(
                currentId = next?.problemId,
                completedItem = null,
                lastOutcome = null,
                error = null,
            )
        }
    }
}
