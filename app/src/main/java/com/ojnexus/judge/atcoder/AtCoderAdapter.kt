package com.ojnexus.judge.atcoder

import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.AdapterStatus
import com.ojnexus.judge.DataSourceReliability
import com.ojnexus.judge.JudgeAdapter
import com.ojnexus.judge.JudgeCapability
import com.ojnexus.judge.atcoder.api.AtCoderHistoryApi
import com.ojnexus.judge.atcoder.api.AtCoderProblemsApi
import com.ojnexus.judge.atcoder.api.dto.AtCoderContestDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderMergedProblemDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderProblemModelDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderRatingChangeDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderSubmissionDto

interface AtCoderAdapter : JudgeAdapter {
    override val id: JudgeId get() = JudgeId.ATCODER
    override val reliability: DataSourceReliability get() = DataSourceReliability.COMMUNITY
    override val capabilities: Set<JudgeCapability>
        get() = setOf(
            JudgeCapability.ACCOUNT_BINDING,
            JudgeCapability.SUBMISSIONS,
            JudgeCapability.PROBLEM_CATALOG,
            JudgeCapability.PROBLEM_DIFFICULTY,
            JudgeCapability.CONTESTS,
            JudgeCapability.RATING,
            JudgeCapability.RATING_HISTORY,
            JudgeCapability.BACKGROUND_SYNC,
            JudgeCapability.INCREMENTAL_SYNC,
        )

    override suspend fun status(): AdapterStatus = AdapterStatus.AVAILABLE

    suspend fun fetchSubmissions(handle: String, fromSecond: Long): List<AtCoderSubmissionDto>
    suspend fun fetchContests(): List<AtCoderContestDto>
    suspend fun fetchMergedProblems(): List<AtCoderMergedProblemDto>
    suspend fun fetchProblemModels(): Map<String, AtCoderProblemModelDto>
    suspend fun fetchRatingHistory(handle: String): List<AtCoderRatingChangeDto>
}

class RetrofitAtCoderAdapter(
    private val client: AtCoderProblemsClient<AtCoderProblemsApi>,
    private val historyClient: AtCoderProblemsClient<AtCoderHistoryApi>,
) : AtCoderAdapter {
    override suspend fun fetchSubmissions(handle: String, fromSecond: Long) =
        client.call { submissions(handle, fromSecond) }

    override suspend fun fetchContests() = client.call { contests() }

    override suspend fun fetchMergedProblems() = client.call { mergedProblems() }

    override suspend fun fetchProblemModels() = client.call { problemModels() }

    override suspend fun fetchRatingHistory(handle: String) =
        historyClient.call { ratingHistory(handle, RATING_CONTEST_TYPE) }

    private companion object {
        /** Algorithm ladder only; heuristic (AHC) ratings are a separate rating chain. */
        const val RATING_CONTEST_TYPE = "algo"
    }
}
