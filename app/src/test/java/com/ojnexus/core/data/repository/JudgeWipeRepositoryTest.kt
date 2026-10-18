package com.ojnexus.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.dao.SyncOperationWithModules
import com.ojnexus.core.database.entity.AttemptEntity
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.JudgeProfileEntity
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.RemoteProblemDetailEntity
import com.ojnexus.core.database.entity.RemoteProblemEntity
import com.ojnexus.core.database.entity.SyncOperationEntity
import com.ojnexus.core.database.entity.SyncStateEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JudgeWipeRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: OjNexusDatabase
    private lateinit var repository: JudgeAccountRepository
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.databaseBuilder(
            context,
            OjNexusDatabase::class.java,
            "wipe-test-${System.nanoTime()}.db",
        ).allowMainThreadQueries().build()
        repository = JudgeAccountRepository(
            database,
            com.ojnexus.judge.JudgeRegistry(emptyList(), emptyList()),
            clock,
        ) { "gen-1" }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun history(judge: String): List<SyncOperationWithModules> =
        database.syncOperationDao().observeRecentByJudge(judge, limit = 10).first()

    private suspend fun seedJudgePayload(judge: String): JudgeAccountEntity {
        val accountId = database.judgeAccountDao().insert(
            JudgeAccountEntity(
                judge = judge,
                handle = "user-$judge",
                canonicalHandle = "user-$judge",
                connectedAt = 1L,
                updatedAt = 1L,
            ),
        )
        database.syncStateDao().upsert(SyncStateEntity(judge = judge, accountId = accountId))
        database.judgeProfileDao().upsert(
            JudgeProfileEntity(judge = judge, handle = "user-$judge", updatedAt = 1L),
        )
        val problemId = database.problemDao().insert(
            ProblemEntity(
                judge = judge,
                externalId = "1A",
                title = "Seeded $judge",
                difficulty = 800,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        database.attemptDao().insert(
            AttemptEntity(
                problemId = problemId,
                timestamp = 1L,
                dayIndex = 0L,
                verdict = "OK",
            ),
        )
        database.remoteProblemDao().upsertAll(
            listOf(
                RemoteProblemEntity(
                    judge = judge,
                    externalId = "1A",
                    name = "Catalog $judge",
                    updatedAt = 1L,
                ),
            ),
        )
        database.remoteProblemDetailDao().upsert(
            RemoteProblemDetailEntity(
                judge = judge,
                externalId = "1A",
                title = "Detail $judge",
                difficulty = 800,
                tagsJson = "[]",
                totalSubmit = null,
                totalAccepted = null,
                background = "",
                description = "",
                inputFormat = "",
                outputFormat = "",
                hint = "",
                samplesJson = "[]",
                timeLimitMs = null,
                memoryLimitMb = null,
                updatedAt = 1L,
            ),
        )
        database.syncOperationDao().openOperation(
            SyncOperationEntity(
                judge = judge,
                accountId = accountId,
                dataGeneration = "gen-old",
                startedAt = 1L,
                status = "SUCCESS",
            ),
        )
        return requireNotNull(database.judgeAccountDao().findById(accountId))
    }

    @Test
    fun `wipe removes one judge payload and keeps the other judge intact`() = runBlocking {
        val codeforces = seedJudgePayload("codeforces")
        val luogu = seedJudgePayload("luogu")
        val codeforcesProblem = requireNotNull(database.problemDao().findByKey("codeforces", "1A"))

        repository.wipeJudgeData("codeforces", codeforces.id)

        // Wiped judge: payload gone. The luogu problem row remains (isolation).
        assertEquals(1, database.problemDao().count())
        assertNull(database.problemDao().findByKey("codeforces", "1A"))
        assertNull(database.judgeProfileDao().findByJudge("codeforces"))
        assertNull(database.remoteProblemDetailDao().findByKey("codeforces", "1A"))
        assertNull(database.syncStateDao().findByJudge("codeforces"))
        // Attempts cascade with the problem row.
        assertTrue(database.attemptDao().findByProblem(codeforcesProblem.id).isEmpty())

        // Cross-judge isolation: luogu rows survive untouched.
        assertNotNull(database.problemDao().findByKey("luogu", "1A"))
        assertNotNull(database.judgeProfileDao().findByJudge("luogu"))
        assertNotNull(database.remoteProblemDetailDao().findByKey("luogu", "1A"))
        assertNotNull(database.syncStateDao().findByJudge("luogu"))

        // Both account rows survive.
        assertNotNull(database.judgeAccountDao().findById(codeforces.id))
        assertNotNull(database.judgeAccountDao().findById(luogu.id))
    }

    @Test
    fun `wipe writes a WIPE ledger entry for the wiped judge only`() = runBlocking {
        val codeforces = seedJudgePayload("codeforces")
        seedJudgePayload("luogu")

        repository.wipeJudgeData("codeforces", codeforces.id)

        val wipedHistory = history("codeforces")
        assertEquals(1, wipedHistory.size)
        val wipe = wipedHistory.single().operation
        assertEquals(JudgeAccountRepository.WIPE_LEDGER_STATUS, wipe.status)
        assertEquals("gen-1", wipe.dataGeneration)
        assertEquals(clock.millis(), wipe.finishedAt)

        val untouched = history("luogu")
        assertEquals(1, untouched.size)
        assertEquals("SUCCESS", untouched.single().operation.status)
    }

    @Test
    fun `wipe rejects an account id that is not the active account for the judge`() = runBlocking {
        val codeforces = seedJudgePayload("codeforces")
        val luogu = seedJudgePayload("luogu")

        var failure: Throwable? = null
        try {
            repository.wipeJudgeData("codeforces", luogu.id)
        } catch (e: IllegalArgumentException) {
            failure = e
        }
        assertTrue(failure is IllegalArgumentException)
        // Nothing was deleted: both problem rows are unchanged.
        assertEquals(2, database.problemDao().count())
        assertNotNull(database.problemDao().findByKey("codeforces", "1A"))
    }
}
