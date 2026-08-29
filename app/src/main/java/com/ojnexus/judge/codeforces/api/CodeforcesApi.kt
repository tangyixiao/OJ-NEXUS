package com.ojnexus.judge.codeforces.api

import com.ojnexus.judge.codeforces.api.dto.CfContestDto
import com.ojnexus.judge.codeforces.api.dto.CfProblemsetDto
import com.ojnexus.judge.codeforces.api.dto.CfRatingChangeDto
import com.ojnexus.judge.codeforces.api.dto.CfSubmissionDto
import com.ojnexus.judge.codeforces.api.dto.CfUserDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Official public Codeforces API. Anonymous only — no apiKey/apiSig/secret parameters are
 * ever sent (see docs/CODEFORCES.md). Every call goes through [com.ojnexus.judge.codeforces.CodeforcesRequestGate]
 * via the client; nothing else in the app may invoke these methods directly.
 */
interface CodeforcesApi {

    @GET("user.info")
    suspend fun userInfo(
        @Query("handles") handles: String,
    ): CodeforcesEnvelope<List<CfUserDto>>

    @GET("user.rating")
    suspend fun userRating(
        @Query("handle") handle: String,
    ): CodeforcesEnvelope<List<CfRatingChangeDto>>

    /** Submissions, newest first. `from` is 1-based; `count` capped per SyncPolicy. */
    @GET("user.status")
    suspend fun userStatus(
        @Query("handle") handle: String,
        @Query("from") from: Int,
        @Query("count") count: Int,
    ): CodeforcesEnvelope<List<CfSubmissionDto>>

    /** `gym = false`: regular contests only. */
    @GET("contest.list")
    suspend fun contestList(
        @Query("gym") gym: Boolean,
    ): CodeforcesEnvelope<List<CfContestDto>>

    @GET("problemset.problems")
    suspend fun problemsetProblems(): CodeforcesEnvelope<CfProblemsetDto>
}
