package com.ojnexus.feature.problems

import com.ojnexus.core.model.Attempt
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.ui.submission.SubmissionDetailUiModel

/**
 * Converts a locally recorded attempt (possibly remote-synced) into the unified detail-sheet
 * model. `submissionUrl` is precomputed by the caller through the judge-specific URL builder —
 * this mapper stays judge-agnostic and never constructs URLs itself.
 */
internal fun toSubmissionDetailModel(
    attempt: Attempt,
    submissionUrl: String?,
): SubmissionDetailUiModel = SubmissionDetailUiModel(
    judgeId = attempt.sourceJudge ?: JudgeId.LOCAL.id,
    verdict = attempt.verdict,
    rawVerdict = attempt.rawVerdict,
    language = attempt.language,
    timestampEpochMs = attempt.timestamp,
    executionTimeMs = null,
    memoryKiB = null,
    score = null,
    problemTitle = null,
    problemId = null,
    submissionUrl = submissionUrl,
    problemUrl = null,
    extraRows = emptyList(),
)
