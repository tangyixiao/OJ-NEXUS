package com.ojnexus.core.ui.submission

import com.ojnexus.core.model.Verdict

/**
 * Judge-agnostic presentation model for one submission, shared by the unified detail sheet.
 * Built by feature code from judge-specific sources (Codeforces attempts, AtCoder attempts,
 * Luogu submission jobs) — the sheet itself never sees judge-specific types.
 */
data class SubmissionDetailUiModel(
    /** Judge display id shown in the header tag, e.g. "codeforces". */
    val judgeId: String,
    /** Unified verdict tag; null when the source has no mapped verdict yet (pending/queued). */
    val verdict: Verdict?,
    /** Raw judge-side verdict text preserved for transparency; may be null. */
    val rawVerdict: String?,
    /** Display language, e.g. "C++ 20 (gcc 12.2)"; null when unknown. */
    val language: String?,
    /** Submission timestamp epoch millis; null when the source lacks it. */
    val timestampEpochMs: Long?,
    /** Execution time in milliseconds; null when unavailable. */
    val executionTimeMs: Long?,
    /** Peak memory in KiB; null when unavailable. */
    val memoryKiB: Long?,
    /** Score in 0..100 or null when the judge does not report one. */
    val score: Int?,
    /** Problem display title; null when unknown. */
    val problemTitle: String?,
    /** Problem display id (judge-side pid or contest+index); null when unknown. */
    val problemId: String?,
    /** Deep link to the judge-side submission page; null when no URL can be built. */
    val submissionUrl: String?,
    /** Deep link to the problem page; null when no URL can be built. */
    val problemUrl: String?,
    /** Optional free-form detail rows (label→value) such as compile messages or outputs. */
    val extraRows: List<SubmissionDetailRow> = emptyList(),
)

/** One label→value row rendered verbatim in the details block. */
data class SubmissionDetailRow(
    val label: String,
    val value: String,
)
