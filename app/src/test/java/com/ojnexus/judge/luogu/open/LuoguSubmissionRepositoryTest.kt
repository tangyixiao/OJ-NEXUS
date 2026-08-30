package com.ojnexus.judge.luogu.open

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.entity.SubmissionJobEntity
import com.ojnexus.core.model.JudgeId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LuoguSubmissionRepositoryTest {
    private lateinit var database: com.ojnexus.core.database.OjNexusDatabase
    private lateinit var repository: LuoguSubmissionRepository
    private val gateway = FakeSubmissionGateway()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            com.ojnexus.core.database.OjNexusDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = LuoguSubmissionRepository(database, gateway, clock)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `submission is persisted without storing source code or input`() = runBlocking {
        repository.submitProblem(
            LuoguProblemJudgeRequest(
                pid = "P1001",
                lang = "cxx/14/gcc",
                o2 = false,
                code = "SECRET SOURCE SHOULD NOT BE STORED",
            ),
        )

        val job = database.submissionJobDao().findByRequestId("req-1")
        assertNotNull(job)
        assertEquals(SubmissionJobKind.PROBLEM.name, job?.kind)
        assertEquals(SubmissionJobStatus.PENDING.name, job?.status)
        assertEquals(1_000L, job?.createdAt)
    }

    @Test
    fun `ready result updates the local job status and score`() = runBlocking {
        repository.submitProblem(
            LuoguProblemJudgeRequest("P1001", "cxx/14/gcc", false, "int main() {}"),
        )
        gateway.result = LuoguOpenResult.Ready(
            LuoguOpenEvaluation(
                requestId = "req-1",
                trackId = null,
                type = "judge",
                compileSuccess = true,
                compileMessage = null,
                status = 12,
                score = 100,
                timeMs = 2,
                memoryKiB = 3,
                output = null,
                exitCode = null,
            ),
        )

        repository.fetchResult("req-1")

        val job = database.submissionJobDao().findByRequestId("req-1")
        assertEquals(SubmissionJobStatus.READY.name, job?.status)
        assertEquals(12, job?.judgeStatus)
        assertEquals(100, job?.score)
        assertEquals(1_000L, job?.updatedAt)
    }

    @Test
    fun `terminal problem result materializes one idempotent local attempt`() = runBlocking {
        repository.submitProblem(
            LuoguProblemJudgeRequest("P1001", "cxx/14/gcc", false, "int main() {}"),
        )
        gateway.result = LuoguOpenResult.Ready(
            LuoguOpenEvaluation(
                requestId = "req-1",
                trackId = null,
                type = "judge",
                compileSuccess = true,
                compileMessage = null,
                status = 12,
                score = 100,
                timeMs = 2,
                memoryKiB = 3,
                output = null,
                exitCode = null,
            ),
        )

        repository.fetchResult("req-1")
        repository.fetchResult("req-1")

        val problem = database.problemDao().findByKey(JudgeId.LUOGU.id, "P1001")
        assertNotNull(problem)
        val attempts = database.attemptDao().findByProblem(requireNotNull(problem).id)
        assertEquals(1, attempts.size)
        assertEquals("req-1", attempts.single().externalSubmissionId)
        assertEquals("AC", attempts.single().verdict)
        assertEquals(100, attempts.single().score?.toInt())
    }

    @Test
    fun `waiting judge result does not create a finished attempt`() = runBlocking {
        repository.submitProblem(
            LuoguProblemJudgeRequest("P1001", "cxx/14/gcc", false, "int main() {}"),
        )
        gateway.result = LuoguOpenResult.Ready(
            LuoguOpenEvaluation(
                requestId = "req-1",
                trackId = null,
                type = "judge",
                compileSuccess = null,
                compileMessage = null,
                status = 1,
                score = null,
                timeMs = null,
                memoryKiB = null,
                output = null,
                exitCode = null,
            ),
        )

        repository.fetchResult("req-1")

        assertEquals(null, database.problemDao().findByKey(JudgeId.LUOGU.id, "P1001"))
    }

    @Test
    fun `submission history survives a Room database reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "submission-history-${System.nanoTime()}"
        val first = Room.databaseBuilder(
            context,
            com.ojnexus.core.database.OjNexusDatabase::class.java,
            name,
        ).allowMainThreadQueries().build()
        first.submissionJobDao().insert(
            SubmissionJobEntity(
                judge = JudgeId.LUOGU.id,
                requestId = "req-reopen",
                kind = SubmissionJobKind.PROBLEM.name,
                pid = "P1001",
                language = "cxx/14/gcc",
                status = SubmissionJobStatus.PENDING.name,
                createdAt = 1,
                updatedAt = 2,
            ),
        )
        first.close()

        val reopened = Room.databaseBuilder(
            context,
            com.ojnexus.core.database.OjNexusDatabase::class.java,
            name,
        ).allowMainThreadQueries().build()
        try {
            val reopenedRepository = LuoguSubmissionRepository(reopened, gateway, clock)
            assertEquals("req-reopen", reopenedRepository.latestForProblem("P1001")?.requestId)
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }
}

private class FakeSubmissionGateway : LuoguOpenGateway {
    var result: LuoguOpenResult = LuoguOpenResult.Pending

    override suspend fun submitProblem(request: LuoguProblemJudgeRequest) = LuoguOpenSubmission("req-1")
    override suspend fun run(request: LuoguRunRequest) = LuoguOpenSubmission("run-1")
    override suspend fun fetchResult(requestId: String): LuoguOpenResult = result
}
