package com.ojnexus.feature.submissions

import com.ojnexus.core.database.entity.SubmissionJobEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Verdict
import com.ojnexus.core.ui.submission.SubmissionDetailRow
import com.ojnexus.core.ui.submission.SubmissionDetailUiModel
import com.ojnexus.judge.luogu.LuoguUrls
import com.ojnexus.judge.luogu.open.SubmissionJobKind
import com.ojnexus.judge.luogu.open.SubmissionJobStatus
import com.ojnexus.judge.luogu.open.LuoguJudgeStatus

/**
 * Converts a Luogu submission job (Open Platform lifecycle record) into the unified
 * detail-sheet model. Pure function — unit-testable without Compose.
 */
internal fun SubmissionJobEntity.toSubmissionDetailModel(): SubmissionDetailUiModel {
    val judgeStatus = judgeStatus
    val verdict: Verdict? = judgeStatus?.let { LuoguJudgeStatus.verdict(it) }
    val problemUrl = pid?.takeIf { it.isNotBlank() }?.let { LuoguUrls.problem(it) }
    val kind = kind
    val extra = buildList {
        score?.let { add(SubmissionDetailRow(SCORE_LABEL, it.toString())) }
        judgeStatus?.let { add(SubmissionDetailRow(JUDGE_STATUS_LABEL, it.toString())) }
        compileSuccess?.let {
            add(SubmissionDetailRow(COMPILE_LABEL, if (it) COMPILE_OK else COMPILE_FAILED))
        }
        compileMessage?.takeIf { msg -> msg.isNotBlank() }?.let {
            add(SubmissionDetailRow(COMPILE_MESSAGE_LABEL, it))
        }
        output?.takeIf { msg -> msg.isNotBlank() }?.let {
            add(SubmissionDetailRow(OUTPUT_LABEL, it))
        }
        exitCode?.let { add(SubmissionDetailRow(EXIT_CODE_LABEL, it.toString())) }
        lastErrorType?.takeIf { msg -> msg.isNotBlank() }?.let {
            add(SubmissionDetailRow(ERROR_LABEL, it))
        }
        if (kind == SubmissionJobKind.RUN.name) {
            add(SubmissionDetailRow(KIND_LABEL, KIND_RUN))
        }
    }
    return SubmissionDetailUiModel(
        judgeId = judge.ifBlank { JudgeId.LUOGU.id },
        verdict = verdict,
        rawVerdict = judgeStatus?.let { "STATUS $it" },
        language = language,
        timestampEpochMs = createdAt,
        executionTimeMs = executionTimeMs?.toLong(),
        memoryKiB = memoryKiB?.toLong(),
        score = score,
        problemTitle = title?.trim()?.takeIf { it.isNotEmpty() },
        problemId = pid?.takeIf { it.isNotBlank() },
        submissionUrl = null,
        problemUrl = problemUrl,
        extraRows = extra,
    )
}

private const val SCORE_LABEL = "SCORE"
private const val JUDGE_STATUS_LABEL = "JUDGE STATUS"
private const val COMPILE_LABEL = "COMPILE"
private const val COMPILE_OK = "OK"
private const val COMPILE_FAILED = "FAILED"
private const val COMPILE_MESSAGE_LABEL = "COMPILE MESSAGE"
private const val OUTPUT_LABEL = "OUTPUT"
private const val EXIT_CODE_LABEL = "EXIT CODE"
private const val ERROR_LABEL = "ERROR"
private const val KIND_LABEL = "KIND"
private const val KIND_RUN = "CUSTOM RUN"

internal fun submissionDetailSheetVisible(status: String): Boolean =
    status == SubmissionJobStatus.READY.name || status == SubmissionJobStatus.FAILED.name
