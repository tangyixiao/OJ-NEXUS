package com.ojnexus.feature.problems

import com.ojnexus.core.model.JudgeId

enum class ProblemStatusUi {
    SOLVED,
    ATTEMPTED,
    UNSOLVED,
}

data class ProblemRowUi(
    val judge: JudgeId,
    val code: String,
    val title: String,
    /** Unified difficulty rating; null when the judge has no rating for this problem. */
    val rating: Int?,
    val status: ProblemStatusUi,
    /** 0–100; null when not enough attempt data exists. */
    val masteryPercent: Int?,
)

data class ProblemsUiState(
    val totalCount: Int,
    val rows: List<ProblemRowUi>,
)
