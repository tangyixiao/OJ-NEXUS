package com.ojnexus.judge.atcoder.api

import com.ojnexus.judge.atcoder.api.dto.AtCoderContestDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderMergedProblemDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderProblemModelDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderSubmissionDto
import retrofit2.http.GET
import retrofit2.http.Query

interface AtCoderProblemsApi {
    @GET("atcoder-api/v3/user/submissions")
    suspend fun submissions(
        @Query("user") user: String,
        @Query("from_second") fromSecond: Long,
    ): List<AtCoderSubmissionDto>

    @GET("atcoder/resources/contests.json")
    suspend fun contests(): List<AtCoderContestDto>

    @GET("atcoder/resources/merged-problems.json")
    suspend fun mergedProblems(): List<AtCoderMergedProblemDto>

    @GET("atcoder/resources/problem-models.json")
    suspend fun problemModels(): Map<String, AtCoderProblemModelDto>
}
