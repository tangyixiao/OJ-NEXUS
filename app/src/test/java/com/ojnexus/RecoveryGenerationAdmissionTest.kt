package com.ojnexus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.workDataOf
import com.ojnexus.app.NexusStartupState
import com.ojnexus.core.data.backup.ScheduledBackupWorker
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.SyncOperationStatus
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.luogu.open.LuoguOpenResultWorker
import com.ojnexus.judge.sync.JudgeSyncWorker
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 127B: with a Ready startup state, only an exactly matching generation stamp may
 * proceed. Stale and unstamped work must refuse before coordinator runs, result polls,
 * exports or backup-status writes; sync refusals stay auditable in the operations ledger.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = ReadyAdmissionApplication::class)
class RecoveryGenerationAdmissionTest {
    private fun readyContainer(): NexusStartupState.Ready =
        ApplicationProvider.getApplicationContext<ReadyAdmissionApplication>()
            .startupState as NexusStartupState.Ready

    private suspend fun seedAccount(): Long =
        readyContainer().container.database.judgeAccountDao().insert(
            JudgeAccountEntity(
                judge = JudgeId.CODEFORCES.id,
                handle = "tourist",
                canonicalHandle = "tourist",
                connectedAt = 1,
                updatedAt = 1,
            ),
        )

    private suspend fun operationRows() = readyContainer().container.database
        .syncOperationDao().observeRecentByJudge(JudgeId.CODEFORCES.id, 10).first()

    @Test
    fun `stale sync work refuses with an auditable ledger row`() = runBlocking {
        seedAccount()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = JudgeSyncWorker(
            context,
            workerParameters(workDataOf("account_id" to 1L, "judge_id" to JudgeId.CODEFORCES.id, "data_generation" to "generation-old")),
        ).doWork()
        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        assertEquals("STALE_DATA_GENERATION", result.outputData.getString(JudgeSyncWorker.RESULT_KEY))
        val row = operationRows().single()
        assertEquals(SyncOperationStatus.STALE_GENERATION.name, row.operation.status)
        assertEquals("generation-old", row.operation.dataGeneration)
        assertEquals("STALE_DATA_GENERATION", row.operation.lastErrorType)
    }

    @Test
    fun `unstamped sync work refuses without any coordinator operation`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = JudgeSyncWorker(
            context,
            workerParameters(workDataOf("account_id" to 9L, "judge_id" to JudgeId.CODEFORCES.id)),
        ).doWork()
        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        assertEquals("UNSTAMPED_DATA_GENERATION", result.outputData.getString(JudgeSyncWorker.RESULT_KEY))
        assertTrue("unstamped refusal must not open a sync operation", operationRows().isEmpty())
    }

    @Test
    fun `unstamped refusal records an empty generation ledger row`() = runBlocking {
        val accountId = seedAccount()
        val current = readyContainer().container.currentDataGeneration()
        val dispatcher = readyContainer().container.syncDispatcher
        assertFalse("a matching stamp is not a refusal", dispatcher.recordStaleGeneration(JudgeId.CODEFORCES, accountId, current))
        assertTrue(dispatcher.recordStaleGeneration(JudgeId.CODEFORCES, accountId, null))
        val row = operationRows().single()
        assertEquals(SyncOperationStatus.STALE_GENERATION.name, row.operation.status)
        assertEquals("", row.operation.dataGeneration)
    }

    @Test
    fun `stale and unstamped luogu polls refuse without room writes`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stale = LuoguOpenResultWorker(
            context,
            workerParameters(workDataOf("request_id" to "persisted-request", "data_generation" to "generation-old")),
        ).doWork()
        assertEquals("STALE_DATA_GENERATION", stale.outputData.getString("result"))
        val unstamped = LuoguOpenResultWorker(
            context,
            workerParameters(workDataOf("request_id" to "persisted-request")),
        ).doWork()
        assertEquals("UNSTAMPED_DATA_GENERATION", unstamped.outputData.getString("result"))
        assertTrue(
            "refused result polls must not touch submission jobs",
            readyContainer().container.database.submissionJobDao().observeRecent(5).first().isEmpty(),
        )
    }

    @Test
    fun `periodic backup request carries the current generation stamp`() {
        val input = com.ojnexus.core.data.backup.scheduledBackupInput("generation-current")
        assertEquals("generation-current", input.getString(ScheduledBackupWorker.DATA_GENERATION))
    }

    @Test
    fun `stale and unstamped backup work refuses before preferences`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stale = ScheduledBackupWorker(
            context,
            workerParameters(workDataOf("data_generation" to "generation-old")),
        ).doWork()
        assertEquals("STALE_DATA_GENERATION", stale.outputData.getString("result"))
        val unstamped = ScheduledBackupWorker(context, workerParameters(Data.EMPTY)).doWork()
        assertEquals("UNSTAMPED_DATA_GENERATION", unstamped.outputData.getString("result"))
        assertFalse(
            "refused backups must not write backup-status preferences",
            File(context.filesDir, "datastore/oj-nexus-preferences.preferences_pb").exists(),
        )
    }
}

class ReadyAdmissionApplication : OjNexusApplication() {
    override fun bootstrapReadyContainer(container: AppContainer) = Unit
    override fun initializeGlobalContext() = Unit
    override fun installCrashLogIfOptedIn(container: AppContainer) = Unit
}
