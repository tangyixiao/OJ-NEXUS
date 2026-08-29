package com.ojnexus.judge

import com.ojnexus.core.data.sync.StageOutcome
import com.ojnexus.core.data.sync.SyncReport
import com.ojnexus.core.data.sync.SyncStage
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.sync.JudgeWorkNames
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

private class RoutingAdapter(override val id: JudgeId) : JudgeAdapter {
    override val capabilities = emptySet<JudgeCapability>()
    override val reliability = DataSourceReliability.COMMUNITY
    override suspend fun status() = AdapterStatus.AVAILABLE
}

private class RoutingCoordinator(override val judgeId: JudgeId) : JudgeSyncCoordinator {
    val calls = mutableListOf<Pair<Long, Boolean>>()
    override suspend fun syncAccount(accountId: Long, force: Boolean): SyncReport {
        calls += accountId to force
        return SyncReport(listOf(StageOutcome(SyncStage.SUBMISSIONS, true)))
    }
}

class JudgeSyncRoutingTest {
    @Test
    fun `registry resolves one coordinator per judge`() = runBlocking {
        val registry = JudgeRegistry(listOf(RoutingAdapter(JudgeId.CODEFORCES), RoutingAdapter(JudgeId.ATCODER)))
        val codeforces = RoutingCoordinator(JudgeId.CODEFORCES)
        val atCoder = RoutingCoordinator(JudgeId.ATCODER)
        registry.attachSyncCoordinators(listOf(codeforces, atCoder))

        registry.syncCoordinator(JudgeId.ATCODER).syncAccount(9, true)

        assertEquals(listOf(9L to true), atCoder.calls)
        assertEquals(emptyList<Pair<Long, Boolean>>(), codeforces.calls)
    }

    @Test
    fun `duplicate coordinator registration fails`() {
        val registry = JudgeRegistry(listOf(RoutingAdapter(JudgeId.ATCODER)))
        assertThrows(IllegalArgumentException::class.java) {
            registry.attachSyncCoordinators(
                listOf(RoutingCoordinator(JudgeId.ATCODER), RoutingCoordinator(JudgeId.ATCODER)),
            )
        }
    }

    @Test
    fun `work names isolate judge and account`() {
        assertEquals("judge-sync-manual-codeforces-7", JudgeWorkNames.manual(JudgeId.CODEFORCES, 7))
        assertEquals("judge-sync-periodic-atcoder-7", JudgeWorkNames.periodic(JudgeId.ATCODER, 7))
    }
}
