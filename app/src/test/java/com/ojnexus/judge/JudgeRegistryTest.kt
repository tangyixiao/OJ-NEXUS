package com.ojnexus.judge

import com.ojnexus.core.model.JudgeId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeJudgeAdapter(
    override val id: JudgeId,
    override val capabilities: Set<JudgeCapability>,
    override val reliability: DataSourceReliability,
    private val adapterStatus: AdapterStatus = AdapterStatus.AVAILABLE,
) : JudgeAdapter {
    override suspend fun status(): AdapterStatus = adapterStatus
}

class JudgeRegistryTest {

    @Test
    fun `registry resolves adapters by judge without feature branching`() {
        val codeforces = FakeJudgeAdapter(
            JudgeId.CODEFORCES,
            setOf(JudgeCapability.PROFILE, JudgeCapability.SUBMISSIONS),
            DataSourceReliability.OFFICIAL,
        )
        val atCoder = FakeJudgeAdapter(
            JudgeId.ATCODER,
            setOf(JudgeCapability.SUBMISSIONS, JudgeCapability.PROBLEM_DIFFICULTY),
            DataSourceReliability.COMMUNITY,
        )

        val registry = JudgeRegistry(listOf(codeforces, atCoder))

        assertEquals(codeforces, registry.adapter(JudgeId.CODEFORCES))
        assertEquals(atCoder, registry.adapter(JudgeId.ATCODER))
        assertNull(registry.adapterOrNull(JudgeId.LUOGU))
        assertEquals(setOf(JudgeId.CODEFORCES, JudgeId.ATCODER), registry.supportedJudges())
    }

    @Test
    fun `duplicate judge registrations fail fast`() {
        val first = FakeJudgeAdapter(JudgeId.ATCODER, emptySet(), DataSourceReliability.COMMUNITY)
        val second = FakeJudgeAdapter(JudgeId.ATCODER, emptySet(), DataSourceReliability.EXPERIMENTAL)

        assertThrows(IllegalArgumentException::class.java) {
            JudgeRegistry(listOf(first, second))
        }
    }

    @Test
    fun `capabilities and runtime status are independent facts`() = runBlocking {
        val adapter = FakeJudgeAdapter(
            JudgeId.ATCODER,
            setOf(JudgeCapability.CONTESTS),
            DataSourceReliability.COMMUNITY,
            AdapterStatus.DEGRADED,
        )

        assertTrue(JudgeCapability.CONTESTS in adapter.capabilities)
        assertEquals(AdapterStatus.DEGRADED, adapter.status())
        assertEquals(DataSourceReliability.COMMUNITY, adapter.reliability)
    }

    @Test
    fun `registry resolves Luogu account binding without a sync coordinator`() {
        val luogu = FakeJudgeAdapter(
            JudgeId.LUOGU,
            setOf(JudgeCapability.ACCOUNT_BINDING),
            DataSourceReliability.EXPERIMENTAL,
        )
        val connector = object : JudgeAccountConnector {
            override val judgeId = JudgeId.LUOGU
            override suspend fun bind(rawHandle: String) = error("not used in registry test")
        }

        val registry = JudgeRegistry(listOf(luogu), listOf(connector))

        assertEquals(luogu, registry.adapter(JudgeId.LUOGU))
        assertEquals(connector, registry.accountConnector(JudgeId.LUOGU))
        assertThrows(IllegalStateException::class.java) {
            registry.syncCoordinator(JudgeId.LUOGU)
        }
    }
}
