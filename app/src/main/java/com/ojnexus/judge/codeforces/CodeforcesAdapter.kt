package com.ojnexus.judge.codeforces

import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.AdapterStatus
import com.ojnexus.judge.DataSourceReliability
import com.ojnexus.judge.JudgeCapability
import com.ojnexus.judge.codeforces.api.dto.CfContestDto
import com.ojnexus.judge.codeforces.api.dto.CfProblemsetDto
import com.ojnexus.judge.codeforces.api.dto.CfRatingChangeDto
import com.ojnexus.judge.codeforces.api.dto.CfSubmissionDto
import com.ojnexus.judge.codeforces.api.dto.CfUserDto

/**
 * The Codeforces judge adapter boundary (docs/OJ_ADAPTERS.md). Everything above this
 * interface (repositories, sync coordinator, UI) works with entities and errors; only
 * [RetrofitCodeforcesAdapter] talks to the transport. Tests substitute fakes here.
 */
interface CodeforcesAdapter : com.ojnexus.judge.JudgeAdapter {

    override val id: JudgeId get() = JudgeId.CODEFORCES
    override val reliability: DataSourceReliability get() = DataSourceReliability.OFFICIAL
    override val capabilities: Set<JudgeCapability>
        get() = setOf(
            JudgeCapability.ACCOUNT_BINDING,
            JudgeCapability.PROFILE,
            JudgeCapability.RATING,
            JudgeCapability.RATING_HISTORY,
            JudgeCapability.SUBMISSIONS,
            JudgeCapability.PROBLEM_CATALOG,
            JudgeCapability.CONTESTS,
            JudgeCapability.BACKGROUND_SYNC,
            JudgeCapability.INCREMENTAL_SYNC,
        )

    override suspend fun status(): AdapterStatus = AdapterStatus.AVAILABLE

    /** Profile lookup; throws [CodeforcesApiError.UserNotFound] for unknown handles. */
    suspend fun fetchProfile(handle: String): CfUserDto

    /** Full rated-contest history (small; always fetched whole). */
    suspend fun fetchRatingHistory(handle: String): List<CfRatingChangeDto>

    /** One page of submissions, newest first; `from` is 1-based. */
    suspend fun fetchSubmissionsPage(handle: String, from: Int, count: Int): List<CfSubmissionDto>

    /** Regular contests (no gym). */
    suspend fun fetchContests(): List<CfContestDto>

    /** Whole problemset with per-problem statistics. */
    suspend fun fetchProblemset(): CfProblemsetDto
}

/** Production adapter: every call goes through the rate-limit gate via [CodeforcesClient]. */
class RetrofitCodeforcesAdapter(
    private val client: CodeforcesClient,
) : CodeforcesAdapter {

    override suspend fun fetchProfile(handle: String): CfUserDto {
        val users = client.call { userInfo(handles = handle) }
        return users.firstOrNull()
            ?: throw CodeforcesApiError.UserNotFound("handle not found: $handle")
    }

    override suspend fun fetchRatingHistory(handle: String): List<CfRatingChangeDto> =
        client.call { userRating(handle = handle) }

    override suspend fun fetchSubmissionsPage(
        handle: String,
        from: Int,
        count: Int,
    ): List<CfSubmissionDto> =
        client.call { userStatus(handle = handle, from = from, count = count) }

    override suspend fun fetchContests(): List<CfContestDto> =
        client.call { contestList(gym = false) }

    override suspend fun fetchProblemset(): CfProblemsetDto =
        client.call { problemsetProblems() }
}
