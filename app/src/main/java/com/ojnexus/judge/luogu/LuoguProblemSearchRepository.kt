package com.ojnexus.judge.luogu

import com.ojnexus.core.data.repository.RemoteProblemSearchProvider
import com.ojnexus.core.database.entity.RemoteProblemEntity
import com.ojnexus.core.model.JudgeId
import java.time.Clock

/**
 * Fills a missing local Luogu catalog slice from the public keyword endpoint.
 * This keeps the app useful before a long background catalog sync completes. It never
 * handles passwords, cookies, sessions, or CSRF state.
 */
class LuoguProblemSearchRepository(
    private val client: LuoguClient,
    private val clock: Clock = Clock.systemUTC(),
) : RemoteProblemSearchProvider {
    override suspend fun fetch(
        judge: JudgeId,
        query: String,
        limit: Int,
        offset: Int,
    ): List<RemoteProblemEntity> {
        val normalizedQuery = query.trim()
        if (judge != JudgeId.LUOGU || normalizedQuery.isBlank() || limit <= 0 || offset < 0) return emptyList()
        val page = offset / limit + 1
        val response = client.fetchProblemPage(page = page, keyword = normalizedQuery)
        return response.data?.problems?.result
            ?.filter { it.pid.isNotBlank() }
            ?.map { LuoguMappers.toRemoteProblemEntity(it, JudgeId.LUOGU, clock.millis()) }
            .orEmpty()
    }
}
