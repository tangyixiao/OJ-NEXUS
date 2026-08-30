package com.ojnexus.judge.luogu

import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.AdapterStatus
import com.ojnexus.judge.DataSourceReliability
import com.ojnexus.judge.JudgeAdapter
import com.ojnexus.judge.JudgeCapability
import com.ojnexus.judge.luogu.api.dto.LuoguUserSummary

interface LuoguAdapter : JudgeAdapter {
    override val id: JudgeId get() = JudgeId.LUOGU
    override val reliability: DataSourceReliability get() = DataSourceReliability.EXPERIMENTAL
    override val capabilities: Set<JudgeCapability>
        get() = setOf(JudgeCapability.ACCOUNT_BINDING)

    override suspend fun status(): AdapterStatus = AdapterStatus.AVAILABLE

    suspend fun searchUser(handle: String): LuoguUserSummary?
}

class RetrofitLuoguAdapter(
    private val client: LuoguClient,
) : LuoguAdapter {
    override suspend fun searchUser(handle: String): LuoguUserSummary? =
        client.searchUsers(handle).users.asSequence()
            .filterNotNull()
            .firstOrNull { it.name == handle }
}
