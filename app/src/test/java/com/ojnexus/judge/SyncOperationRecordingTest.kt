package com.ojnexus.judge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.sync.StageOutcome
import com.ojnexus.core.data.sync.SyncReport
import com.ojnexus.core.data.sync.SyncRetryRequest
import com.ojnexus.core.data.sync.SyncRunContext
import com.ojnexus.core.data.sync.SyncStage
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.SyncOperationStatus
import com.ojnexus.core.model.JudgeId
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class RecordingAdapter(override val id: JudgeId) : JudgeAdapter {
    override val capabilities = emptySet<JudgeCapability>()
    override val reliability = DataSourceReliability.COMMUNITY
    override suspend fun status() = AdapterStatus.AVAILABLE
}

private class RecordingCoordinator : JudgeSyncCoordinator {
    override val judgeId = JudgeId.CODEFORCES

    override suspend fun syncAccount(accountId: Long, force: Boolean): SyncReport =
        SyncReport(listOf(StageOutcome(SyncStage.PROFILE, ok = true)))

    override suspend fun syncAccount(
        accountId: Long,
        force: Boolean,
        context: SyncRunContext,
    ): SyncReport {
        context.recordModule(StageOutcome(SyncStage.PROFILE, ok = true, itemsProcessed = 3))
        return SyncReport(listOf(StageOutcome(SyncStage.PROFILE, ok = true, itemsProcessed = 3)))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncOperationRecordingTest {
    private lateinit var database: OjNexusDatabase
    private lateinit var dispatcher: JudgeSyncDispatcher
    private val clock = Clock.fixed(Instant.ofEpochMilli(10_000), ZoneId.of("UTC"))
    private val account = JudgeAccountEntity(
        judge = JudgeId.CODEFORCES.id,
        handle = "tourist",
        canonicalHandle = "tourist",
        connectedAt = 1,
        updatedAt = 1,
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OjNexusDatabase::class.java,
        ).allowMainThreadQueries().build()
        val registry = JudgeRegistry(listOf(RecordingAdapter(JudgeId.CODEFORCES)))
        registry.attachSyncCoordinators(listOf(RecordingCoordinator()))
        val accounts = JudgeAccountRepository(database, registry, clock)
        val accountId = runBlocking { database.judgeAccountDao().insert(account) }
        dispatcher = JudgeSyncDispatcher(
            accountRepository = accounts,
            registry = registry,
            syncOperationDao = database.syncOperationDao(),
            currentDataGeneration = { "generation-current" },
            clock = clock,
        )
        selectedAccountId = accountId
    }

    @After
    fun tearDown() = database.close()

    private var selectedAccountId: Long = 0

    @Test
    fun `dispatcher records committed module and closes operation`() = runBlocking {
        dispatcher.sync(JudgeId.CODEFORCES, selectedAccountId, force = true, dataGeneration = "generation-current")

        val row = database.syncOperationDao().observeRecentByJudge("codeforces", 10).first().single()
        assertEquals(SyncOperationStatus.SUCCESS.name, row.operation.status)
        assertEquals("generation-current", row.operation.dataGeneration)
        assertEquals(3, row.modules.single().importedCount)
        assertEquals(3, row.modules.single().attemptedCount)
    }

    @Test
    fun `stale generation is recorded without invoking coordinator`() = runBlocking {
        dispatcher.recordStaleGeneration(JudgeId.CODEFORCES, selectedAccountId, "generation-old")

        val row = database.syncOperationDao().observeRecentByJudge("codeforces", 10).first().single()
        assertEquals(SyncOperationStatus.STALE_GENERATION.name, row.operation.status)
        assertEquals("STALE_DATA_GENERATION", row.operation.lastErrorType)
    }

    @Test
    fun `retry rejects operation from another generation`() = runBlocking {
        val request = SyncRetryRequest(
            judge = JudgeId.CODEFORCES,
            accountId = selectedAccountId,
            operationId = 999,
            stage = SyncStage.PROFILE,
            dataGeneration = "generation-old",
        )

        assertNull(dispatcher.retry(request))
        assertEquals(0, database.syncOperationDao().observeRecentByJudge("codeforces", 10).first().size)
    }
}
