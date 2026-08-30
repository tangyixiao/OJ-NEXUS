package com.ojnexus.judge.luogu

import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.AdapterStatus
import com.ojnexus.judge.DataSourceReliability
import com.ojnexus.judge.JudgeAdapter
import com.ojnexus.judge.JudgeCapability
import com.ojnexus.judge.luogu.api.dto.LuoguUserSummary
import com.ojnexus.judge.luogu.api.dto.LuoguContestListResponse
import com.ojnexus.judge.luogu.api.dto.LuoguProblemListResponse
import com.ojnexus.judge.luogu.api.dto.LuoguRecordPageResponse
import com.ojnexus.judge.luogu.api.dto.LuoguUserPageResponse

interface LuoguAdapter : JudgeAdapter {
    override val id: JudgeId get() = JudgeId.LUOGU
    override val reliability: DataSourceReliability get() = DataSourceReliability.EXPERIMENTAL
    override val capabilities: Set<JudgeCapability>
        get() = setOf(
            JudgeCapability.ACCOUNT_BINDING,
            JudgeCapability.PROFILE,
            JudgeCapability.RATING,
            JudgeCapability.RATING_HISTORY,
            JudgeCapability.PROBLEM_CATALOG,
            JudgeCapability.PROBLEM_DIFFICULTY,
            JudgeCapability.CONTESTS,
            JudgeCapability.BACKGROUND_SYNC,
            JudgeCapability.INCREMENTAL_SYNC,
        )

    override suspend fun status(): AdapterStatus = AdapterStatus.AVAILABLE

    suspend fun searchUser(handle: String): LuoguUserSummary?

    suspend fun fetchUserPage(uid: Long): LuoguUserPageResponse =
        error("Luogu profile sync is not implemented by this adapter")

    suspend fun fetchPracticePage(uid: Long): LuoguUserPageResponse =
        error("Luogu rating sync is not implemented by this adapter")

    suspend fun fetchProblemPage(page: Int): LuoguProblemListResponse =
        error("Luogu problem sync is not implemented by this adapter")

    suspend fun fetchContestPage(page: Int): LuoguContestListResponse =
        error("Luogu contest sync is not implemented by this adapter")

    suspend fun fetchRecordPage(uid: Long, page: Int): LuoguRecordPageResponse =
        error("Luogu submission sync is not implemented by this adapter")
}

class RetrofitLuoguAdapter(
    private val client: LuoguClient,
) : LuoguAdapter {
    override suspend fun searchUser(handle: String): LuoguUserSummary? =
        client.searchUsers(handle).users.asSequence()
            .filterNotNull()
            .firstOrNull { it.name == handle }

    override suspend fun fetchUserPage(uid: Long) = client.fetchUserPage(uid)

    override suspend fun fetchPracticePage(uid: Long) = client.fetchPracticePage(uid)

    override suspend fun fetchProblemPage(page: Int) = client.fetchProblemPage(page)

    override suspend fun fetchContestPage(page: Int) = client.fetchContestPage(page)

    override suspend fun fetchRecordPage(uid: Long, page: Int) = client.fetchRecordPage(uid, page)
}
