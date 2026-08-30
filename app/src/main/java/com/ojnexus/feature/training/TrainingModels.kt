package com.ojnexus.feature.training

import com.ojnexus.core.model.ReviewQueueItem
import com.ojnexus.core.model.TrainingSession
import com.ojnexus.core.model.TrainingTask
import com.ojnexus.core.data.repository.KnowledgeAreaState

/** Review queue split into the three user-facing buckets. */
data class ReviewBuckets(
    val overdue: List<ReviewQueueItem> = emptyList(),
    val dueToday: List<ReviewQueueItem> = emptyList(),
    val upcoming: List<ReviewQueueItem> = emptyList(),
) {
    val isEmpty: Boolean get() = overdue.isEmpty() && dueToday.isEmpty() && upcoming.isEmpty()
    val dueNowCount: Int get() = overdue.size + dueToday.size
}

data class TrainingUiState(
    val todayEpochDay: Long,
    val tasks: List<TrainingTask>,
    val reviews: ReviewBuckets,
    val activeSession: TrainingSession?,
    val history: List<TrainingSession>,
    val knowledge: List<KnowledgeAreaState> = emptyList(),
)
