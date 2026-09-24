package com.ojnexus.feature.settings

import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.DataSourceReliability
import com.ojnexus.judge.JudgeCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorCenterTest {
    @Test
    fun `empty registry has no connected or eligible rows`() {
        val summary = deriveConnectorCenter(emptyList())

        assertEquals(0, summary.connectedCount)
        assertEquals(0, summary.supportedCount)
        assertEquals(0, summary.eligibleSyncCount)
        assertTrue(summary.rows.isEmpty())
    }

    @Test
    fun `disconnected judge is visible but cannot sync`() {
        val summary = deriveConnectorCenter(listOf(connection(JudgeId.CODEFORCES)))
        val row = summary.rows.single()

        assertEquals(0, summary.connectedCount)
        assertEquals(1, summary.supportedCount)
        assertFalse(row.connected)
        assertFalse(row.canSync)
        assertEquals(null, row.phase)
    }

    @Test
    fun `connected row reports queued state and receipt coverage`() {
        val summary = deriveConnectorCenter(
            listOf(
                connection(
                    JudgeId.ATCODER,
                    account = account(JudgeId.ATCODER),
                    capabilities = setOf(
                        JudgeCapability.ACCOUNT_BINDING,
                        JudgeCapability.BACKGROUND_SYNC,
                        JudgeCapability.PROFILE,
                        JudgeCapability.SUBMISSIONS,
                    ),
                    sync = SyncStateEntity(
                        judge = JudgeId.ATCODER.id,
                        state = SyncPhase.QUEUED.name,
                        lastSuccessfulSyncAt = 42L,
                        profileSyncedAt = 40L,
                    ),
                ),
            ),
        )
        val row = summary.rows.single()

        assertEquals(1, summary.connectedCount)
        assertEquals(1, summary.eligibleSyncCount)
        assertEquals(SyncPhase.QUEUED, row.phase)
        assertEquals(1, row.completedReceiptCount)
        assertEquals(2, row.totalReceiptCount)
        assertEquals(42L, row.lastSuccessfulSyncAt)
        assertTrue(row.canSync)
    }

    @Test
    fun `fully synced row is complete and still manually refreshable`() {
        val summary = deriveConnectorCenter(
            listOf(
                connection(
                    JudgeId.CODEFORCES,
                    account = account(JudgeId.CODEFORCES),
                    capabilities = setOf(
                        JudgeCapability.ACCOUNT_BINDING,
                        JudgeCapability.BACKGROUND_SYNC,
                        JudgeCapability.PROFILE,
                        JudgeCapability.RATING_HISTORY,
                        JudgeCapability.SUBMISSIONS,
                        JudgeCapability.CONTESTS,
                        JudgeCapability.PROBLEM_CATALOG,
                    ),
                    sync = SyncStateEntity(
                        judge = JudgeId.CODEFORCES.id,
                        state = SyncPhase.SUCCESS.name,
                        lastSuccessfulSyncAt = 99L,
                        profileSyncedAt = 1L,
                        ratingSyncedAt = 2L,
                        submissionsSyncedAt = 3L,
                        contestsSyncedAt = 4L,
                        problemsetSyncedAt = 5L,
                    ),
                ),
            ),
        )
        val row = summary.rows.single()

        assertEquals(5, row.completedReceiptCount)
        assertEquals(5, row.totalReceiptCount)
        assertTrue(row.isComplete)
        assertTrue(row.canSync)
    }

    @Test
    fun `sync all targets include only connected background-sync judges`() {
        val connections = listOf(
            connection(
                JudgeId.CODEFORCES,
                account = account(JudgeId.CODEFORCES),
                capabilities = setOf(JudgeCapability.ACCOUNT_BINDING, JudgeCapability.BACKGROUND_SYNC),
            ),
            connection(
                JudgeId.ATCODER,
                account = account(JudgeId.ATCODER),
                capabilities = setOf(JudgeCapability.ACCOUNT_BINDING),
            ),
            connection(JudgeId.LUOGU),
        )

        assertEquals(
            listOf(JudgeId.CODEFORCES),
            eligibleConnectorSyncRows(connections).map { it.judge },
        )
    }

    private fun connection(
        judge: JudgeId,
        account: JudgeAccountEntity? = null,
        capabilities: Set<JudgeCapability> = setOf(JudgeCapability.ACCOUNT_BINDING),
        sync: SyncStateEntity? = null,
    ) = JudgeConnectionUi(
        judge = judge,
        account = account,
        profile = null,
        syncState = sync,
        capabilities = capabilities,
        reliability = DataSourceReliability.OFFICIAL,
    )

    private fun account(judge: JudgeId) = JudgeAccountEntity(
        id = 7L,
        judge = judge.id,
        handle = "raw_handle",
        canonicalHandle = "handle",
        connectedAt = 1L,
        updatedAt = 1L,
    )
}
