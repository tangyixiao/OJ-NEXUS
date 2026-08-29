package com.ojnexus.feature.training

import com.ojnexus.core.model.KnowledgeArea
import com.ojnexus.core.model.TrainingType

enum class TrainingReasonUi {
    WEAK_MASTERY,
    REVIEW_GAP,
    NO_ATTEMPTS,
}

data class TargetItemUi(
    val code: String,
    val priority: Int,
    val reasons: List<TrainingReasonUi>,
    /** Days since last review; null when the problem was never reviewed. */
    val reviewGapDays: Int?,
    /** Preformatted difficulty window, e.g. "1700–1900". */
    val targetRange: String,
)

data class AreaMasteryUi(
    val area: KnowledgeArea,
    /** 0–100. */
    val masteryPercent: Int,
)

data class TrainingUiState(
    val sessionActive: Boolean,
    val selectedType: TrainingType,
    val durationMinutes: Int,
    val targetProblemCount: Int,
    val targets: List<TargetItemUi>,
    val weakAreas: List<AreaMasteryUi>,
)
