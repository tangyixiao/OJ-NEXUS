package com.ojnexus.feature.submissions

import com.ojnexus.core.database.entity.SubmissionJobEntity
import com.ojnexus.judge.luogu.open.SubmissionJobStatus

enum class SubmissionStatusFilter {
    ALL,
    PENDING,
    READY,
    FAILED,
}

data class SubmissionCenterSummary(
    val total: Int,
    val pending: Int,
    val ready: Int,
    val failed: Int,
    val other: Int,
)

fun summarizeSubmissionCenter(
    jobs: List<SubmissionJobEntity>,
): SubmissionCenterSummary {
    val pending = jobs.count { it.status == SubmissionJobStatus.PENDING.name }
    val ready = jobs.count { it.status == SubmissionJobStatus.READY.name }
    val failed = jobs.count { it.status == SubmissionJobStatus.FAILED.name }
    return SubmissionCenterSummary(
        total = jobs.size,
        pending = pending,
        ready = ready,
        failed = failed,
        other = jobs.size - pending - ready - failed,
    )
}

fun filterSubmissionJobs(
    jobs: List<SubmissionJobEntity>,
    filter: SubmissionStatusFilter,
): List<SubmissionJobEntity> = when (filter) {
    SubmissionStatusFilter.ALL -> jobs.toList()
    SubmissionStatusFilter.PENDING -> jobs.filter { it.status == SubmissionJobStatus.PENDING.name }
    SubmissionStatusFilter.READY -> jobs.filter { it.status == SubmissionJobStatus.READY.name }
    SubmissionStatusFilter.FAILED -> jobs.filter { it.status == SubmissionJobStatus.FAILED.name }
}
