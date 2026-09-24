package com.ojnexus.judge.sync

import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.model.JudgeId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class JudgeSyncBootstrapTest {

    @Test
    fun `reconcile schedules enabled background accounts and skips disabled or missing ones`() = runBlocking {
        val accounts = mapOf(
            JudgeId.CODEFORCES to account(JudgeId.CODEFORCES, id = 10, enabled = true),
            JudgeId.ATCODER to account(JudgeId.ATCODER, id = 20, enabled = false),
            JudgeId.LUOGU to account(JudgeId.LUOGU, id = 30, enabled = true),
        )
        val scheduled = mutableListOf<String>()

        JudgeSyncBootstrap(
            activeAccount = { accounts[it] },
            backgroundJudges = setOf(JudgeId.LUOGU, JudgeId.ATCODER, JudgeId.CODEFORCES, JudgeId.LOCAL),
            enqueuePeriodic = { judge, accountId -> scheduled += "${judge.id}:$accountId" },
        ).reconcile()

        assertEquals(listOf("codeforces:10", "luogu:30"), scheduled)
    }

    @Test
    fun `one scheduler failure does not prevent other judges from being restored`() = runBlocking {
        val accounts = mapOf(
            JudgeId.CODEFORCES to account(JudgeId.CODEFORCES, id = 10),
            JudgeId.ATCODER to account(JudgeId.ATCODER, id = 20),
            JudgeId.LUOGU to account(JudgeId.LUOGU, id = 30),
        )
        val scheduled = mutableListOf<JudgeId>()

        JudgeSyncBootstrap(
            activeAccount = { accounts[it] },
            backgroundJudges = setOf(JudgeId.CODEFORCES, JudgeId.ATCODER, JudgeId.LUOGU),
            enqueuePeriodic = { judge, _ ->
                if (judge == JudgeId.ATCODER) throw IllegalStateException("scheduler unavailable")
                scheduled += judge
            },
        ).reconcile()

        assertEquals(listOf(JudgeId.CODEFORCES, JudgeId.LUOGU), scheduled)
    }

    private fun account(judge: JudgeId, id: Long, enabled: Boolean = true) = JudgeAccountEntity(
        id = id,
        judge = judge.id,
        handle = "user-$id",
        canonicalHandle = "user-$id",
        connectedAt = 1,
        updatedAt = 1,
        enabled = enabled,
    )
}
