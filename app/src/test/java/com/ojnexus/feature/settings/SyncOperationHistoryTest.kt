package com.ojnexus.feature.settings

import com.ojnexus.core.data.sync.SyncRetryRequest
import com.ojnexus.core.data.sync.SyncStage
import com.ojnexus.core.database.dao.SyncOperationWithModules
import com.ojnexus.core.database.entity.SyncOperationEntity
import com.ojnexus.core.database.entity.SyncOperationModuleEntity
import com.ojnexus.core.database.entity.SyncOperationStatus
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.JudgeCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncOperationHistoryTest {
    @Test
    fun `history projection is bounded and keeps newest deterministic rows`() {
        val rows = listOf(3L, 1L, 2L).map { id ->
            operationRow(
                id = id,
                startedAt = if (id == 1L) 100L else 200L,
                status = SyncOperationStatus.SUCCESS.name,
            )
        }

        val projected = projectSyncOperationHistory(rows, limit = 2)

        assertEquals(listOf(3L, 2L), projected.map { it.operationId })
        assertEquals(2, projected.size)
    }

    @Test
    fun `failed module yields full sync fallback without safe stage capability`() {
        val entry = projectSyncOperationHistory(
            listOf(operationRow(7L, 100L, SyncOperationStatus.PARTIAL.name)),
        ).single()
        val module = entry.modules.single()

        val action = syncRetryAction(
            entry = entry,
            module = module,
            judge = JudgeId.CODEFORCES,
            capabilities = emptySet(),
        )

        assertTrue(action != null)
        assertEquals(SyncRetryKind.FULL_SYNC, action?.kind)
        assertEquals(SyncStage.PROFILE, action?.request?.stage)
    }

    @Test
    fun `safe stage capability changes action while successful modules are not retryable`() {
        val entry = projectSyncOperationHistory(
            listOf(operationRow(8L, 100L, SyncOperationStatus.PARTIAL.name, judge = JudgeId.ATCODER.id)),
        ).single()
        val module = entry.modules.single()

        val action = syncRetryAction(
            entry = entry,
            module = module,
            judge = JudgeId.ATCODER,
            capabilities = setOf(JudgeCapability.SAFE_STAGE_RETRY),
        )
        assertEquals(SyncRetryKind.FAILED_STAGE, action?.kind)
        assertEquals(
            SyncRetryRequest(JudgeId.ATCODER, 4L, 8L, SyncStage.PROFILE, "generation"),
            action?.request,
        )

        val successful = entry.copy(modules = listOf(module.copy(status = "SUCCESS")))
        assertNull(syncRetryAction(successful, successful.modules.single(), JudgeId.ATCODER, emptySet()))
    }

    @Test
    fun `stale generation is not retryable from the history card`() {
        val entry = projectSyncOperationHistory(
            listOf(operationRow(9L, 100L, SyncOperationStatus.STALE_GENERATION.name)),
        ).single()

        assertNull(syncRetryAction(entry, entry.modules.single(), JudgeId.CODEFORCES, emptySet()))
    }

    private fun operationRow(
        id: Long,
        startedAt: Long,
        status: String,
        judge: String = JudgeId.CODEFORCES.id,
    ) = SyncOperationWithModules(
        operation = SyncOperationEntity(
            id = id,
            judge = judge,
            accountId = 4L,
            dataGeneration = "generation",
            startedAt = startedAt,
            status = status,
        ),
        modules = listOf(
            SyncOperationModuleEntity(
                id = id,
                operationId = id,
                stage = SyncStage.PROFILE.name,
                status = "ERROR",
                failureType = "Network",
            ),
        ),
    )
}
