package com.ojnexus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import com.ojnexus.app.NexusStartupState
import com.ojnexus.core.data.backup.ScheduledBackupWorker
import com.ojnexus.core.data.restore.RecoveryDisposition
import com.ojnexus.core.data.restore.RestoreFailure
import com.ojnexus.core.data.restore.RestoreReadiness
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.luogu.open.LuoguOpenResultWorker
import com.ojnexus.judge.sync.JudgeSyncWorker
import com.ojnexus.core.data.backup.BackupCadence
import com.ojnexus.core.data.backup.ScheduledBackupScheduler
import com.ojnexus.judge.luogu.open.WorkManagerLuoguResultScheduler
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.Executor
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor
import com.google.common.util.concurrent.Futures

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = StartupProbeApplication::class)
class RecoveryWorkerGateTest {
    @Test fun blockedWorkersReturnRecoveryBlockedBeforeBusinessIo() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = ApplicationProvider.getApplicationContext<StartupProbeApplication>()
        val database = context.getDatabasePath(OjNexusDatabase.DATABASE_NAME)
        assertTrue(app.startupState is NexusStartupState.Blocked)
        assertWorkManagerUnresolved(context)
        app.resetBusinessIoCalls()
        val sync = JudgeSyncWorker(context, workerParameters(workDataOf("account_id" to 9L, "judge_id" to JudgeId.CODEFORCES.id)))
        val luogu = LuoguOpenResultWorker(context, workerParameters(workDataOf("request_id" to "persisted-request")))
        val backup = ScheduledBackupWorker(context, workerParameters(Data.EMPTY))
        val syncResult = sync.doWork()
        val luoguResult = luogu.doWork()
        val backupResult = backup.doWork()
        assertTrue(syncResult is ListenableWorker.Result.Failure)
        assertTrue(luoguResult is ListenableWorker.Result.Failure)
        assertTrue(backupResult is ListenableWorker.Result.Failure)
        assertEquals("RECOVERY_BLOCKED", syncResult.outputData.getString("result"))
        assertEquals("RECOVERY_BLOCKED", luoguResult.outputData.getString("result"))
        assertEquals("RECOVERY_BLOCKED", backupResult.outputData.getString("result"))
        JudgeSyncWorker.enqueueManual(context, JudgeId.CODEFORCES, 9L)
        JudgeSyncWorker.enqueuePeriodic(context, JudgeId.CODEFORCES, 9L)
        WorkManagerLuoguResultScheduler(context).enqueue("persisted-request")
        WorkManagerLuoguResultScheduler(context).enqueue("persisted-request", "explicit-generation")
        WorkManagerLuoguResultScheduler(context).enqueueNow("persisted-request")
        WorkManagerLuoguResultScheduler(context).enqueueNow("persisted-request", "explicit-generation")
        ScheduledBackupScheduler.update(context, BackupCadence.WEEKLY)
        assertFalse(database.exists())
        assertEquals("no prefs/files/cache/database/resolver access while blocked", 0, app.businessIoCalls)
        assertWorkManagerUnresolved(context)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = StartingProbeApplication::class)
class RecoveryStartingWorkerGateTest {
    @Test fun startingWorkersRetryBeforeBusinessIo() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = ApplicationProvider.getApplicationContext<StartingProbeApplication>()
        val database = context.getDatabasePath(OjNexusDatabase.DATABASE_NAME)
        assertSame(NexusStartupState.Starting, app.startupState)
        assertWorkManagerUnresolved(context)
        app.resetBusinessIoCalls()
        val sync = JudgeSyncWorker(context, workerParameters(workDataOf("account_id" to 9L, "judge_id" to JudgeId.CODEFORCES.id)))
        val luogu = LuoguOpenResultWorker(context, workerParameters(workDataOf("request_id" to "persisted-request")))
        val backup = ScheduledBackupWorker(context, workerParameters(Data.EMPTY))
        assertTrue(sync.doWork() is ListenableWorker.Result.Retry)
        assertTrue(luogu.doWork() is ListenableWorker.Result.Retry)
        assertTrue(backup.doWork() is ListenableWorker.Result.Retry)
        JudgeSyncWorker.enqueueManual(context, JudgeId.CODEFORCES, 9L)
        JudgeSyncWorker.enqueuePeriodic(context, JudgeId.CODEFORCES, 9L)
        val luoguScheduler = WorkManagerLuoguResultScheduler(context)
        luoguScheduler.enqueue("persisted-request")
        luoguScheduler.enqueue("persisted-request", "explicit-generation")
        luoguScheduler.enqueueNow("persisted-request")
        luoguScheduler.enqueueNow("persisted-request", "explicit-generation")
        ScheduledBackupScheduler.update(context, BackupCadence.OFF)
        ScheduledBackupScheduler.update(context, BackupCadence.WEEKLY)
        assertFalse(database.exists())
        assertEquals("no prefs/files/cache/database/resolver access while starting", 0, app.businessIoCalls)
        assertWorkManagerUnresolved(context)
    }
}

private fun assertWorkManagerUnresolved(context: Context) {
    try {
        WorkManager.getInstance(context)
        fail("refused scheduling must not resolve WorkManager")
    } catch (_: IllegalStateException) {
        // manifest=NONE keeps WorkManager uninitialized unless the gated call resolves it.
    }
}

class StartingProbeApplication : StartupProbeApplication() {
    override fun onCreate() = Unit
}

internal fun workerParameters(data: Data): WorkerParameters {
    val executor = Executor { it.run() }
    return WorkerParameters(
        UUID.randomUUID(), data, emptyList(), WorkerParameters.RuntimeExtras(), 0, 0,
        executor, EmptyCoroutineContext, WorkManagerTaskExecutor(executor), object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? = null
        },
        ProgressUpdater { _, _, _ -> Futures.immediateVoidFuture() },
        ForegroundUpdater { _, _, _ -> Futures.immediateVoidFuture() },
    )
}
