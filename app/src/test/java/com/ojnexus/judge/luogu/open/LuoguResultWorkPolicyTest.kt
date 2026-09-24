package com.ojnexus.judge.luogu.open

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.SubmissionJobEntity
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LuoguResultWorkPolicyTest {
    private lateinit var database: OjNexusDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OjNexusDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `ready result completes the worker`() {
        assertEquals(
            LuoguResultWorkDecision.Success,
            LuoguResultWorkPolicy.decide(readyResult(), runAttemptCount = 0),
        )
    }

    @Test
    fun `pending result retries until the bounded budget is exhausted`() {
        assertEquals(
            LuoguResultWorkDecision.Retry,
            LuoguResultWorkPolicy.decide(LuoguOpenResult.Pending, runAttemptCount = 0),
        )
        assertEquals(
            LuoguResultWorkDecision.Success,
            LuoguResultWorkPolicy.decide(
                LuoguOpenResult.Pending,
                runAttemptCount = LuoguResultWorkPolicy.MAX_RESULT_ATTEMPTS,
            ),
        )
    }

    @Test
    fun `network error retries`() {
        assertEquals(
            LuoguResultWorkDecision.Retry,
            LuoguResultWorkPolicy.decide(
                LuoguOpenApiError.Network(IOException("offline")),
                runAttemptCount = 0,
            ),
        )
    }

    @Test
    fun `unauthorized error is permanent`() {
        assertEquals(
            LuoguResultWorkDecision.Failure,
            LuoguResultWorkPolicy.decide(LuoguOpenApiError.Unauthorized, runAttemptCount = 0),
        )
    }

    @Test
    fun `pending Luogu jobs are returned oldest first within the limit`() = runBlocking {
        val dao = database.submissionJobDao()
        dao.insert(job("old", updatedAt = 10L, status = SubmissionJobStatus.PENDING.name))
        dao.insert(job("ready", updatedAt = 15L, status = SubmissionJobStatus.READY.name))
        dao.insert(job("middle", updatedAt = 20L, status = SubmissionJobStatus.PENDING.name))
        dao.insert(job("new", updatedAt = 30L, status = SubmissionJobStatus.PENDING.name))

        assertEquals(
            listOf("old", "middle"),
            dao.findPendingForBackground(limit = 2).map { it.requestId },
        )
    }

    private fun readyResult() = LuoguOpenResult.Ready(
        LuoguOpenEvaluation(
            requestId = "ready",
            trackId = null,
            type = "judge",
            compileSuccess = true,
            compileMessage = null,
            status = 12,
            score = 100,
            timeMs = 1,
            memoryKiB = 1,
            output = null,
            exitCode = 0,
        ),
    )

    private fun job(requestId: String, updatedAt: Long, status: String) = SubmissionJobEntity(
        judge = "luogu",
        requestId = requestId,
        kind = SubmissionJobKind.PROBLEM.name,
        pid = "P1001",
        language = "cxx/14/gcc",
        status = status,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )
}
