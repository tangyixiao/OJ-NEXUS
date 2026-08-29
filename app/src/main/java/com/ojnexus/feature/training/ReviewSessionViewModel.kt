package com.ojnexus.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.data.repository.ReviewRepository
import com.ojnexus.core.model.ProblemDetail
import com.ojnexus.core.model.ReviewResult
import com.ojnexus.core.ui.Loadable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReviewSessionUiState(
    val detail: ProblemDetail,
    /** Scheduler's decision after the last outcome; null until a result is recorded. */
    val lastOutcome: ReviewOutcome?,
)

data class ReviewOutcome(
    val result: ReviewResult,
    val nextStage: Int,
    val nextIntervalDays: Long,
)

class ReviewSessionViewModel(
    private val problemId: Long,
    private val problemRepository: ProblemRepository,
    private val reviewRepository: ReviewRepository,
) : ViewModel() {

    private val lastOutcome = MutableStateFlow<ReviewOutcome?>(null)

    val state: StateFlow<Loadable<ReviewSessionUiState>> = combine(
        problemRepository.observeDetail(problemId),
        lastOutcome.asStateFlow(),
    ) { detail, outcome ->
        if (detail == null) {
            Loadable.Failed(NOT_FOUND)
        } else {
            Loadable.Ready(ReviewSessionUiState(detail = detail, lastOutcome = outcome))
        }
    }
        .catch { emit(Loadable.Failed(it.message ?: "Load failed")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    fun record(result: ReviewResult) {
        viewModelScope.launch {
            reviewRepository.completeReview(problemId, result).getOrNull()?.let { scheduled ->
                lastOutcome.value = ReviewOutcome(
                    result = result,
                    nextStage = scheduled.stage,
                    nextIntervalDays = scheduled.intervalDays,
                )
            }
        }
    }

    private companion object {
        const val NOT_FOUND = "problem not found"
    }
}
