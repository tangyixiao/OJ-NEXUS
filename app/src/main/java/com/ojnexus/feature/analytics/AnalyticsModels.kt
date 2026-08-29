package com.ojnexus.feature.analytics

import com.ojnexus.core.model.Verdict

/** One column per week, 7 intensities (0–4) per column, Monday first. */
data class HeatmapUi(
    val weeks: List<List<Int>>,
)

data class VerdictCountUi(
    val verdict: Verdict,
    val count: Int,
)

data class TagCountUi(
    /** Judge-side tag name (data, not UI copy). */
    val tag: String,
    val count: Int,
)

data class AnalyticsUiState(
    val heatmap: HeatmapUi,
    /** Rating history sample points, oldest first. */
    val ratingTrend: List<Int>,
    val verdictCounts: List<VerdictCountUi>,
    val weakTags: List<TagCountUi>,
)
