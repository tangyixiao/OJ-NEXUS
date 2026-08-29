package com.ojnexus.judge.atcoder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.JudgeRegistry
import com.ojnexus.judge.atcoder.api.dto.AtCoderContestDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderMergedProblemDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderProblemModelDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderSubmissionDto
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class BindingAtCoderAdapter : AtCoderAdapter {
    var submissions = emptyList<AtCoderSubmissionDto>()
    var failure: AtCoderApiError? = null

    override suspend fun fetchSubmissions(handle: String, fromSecond: Long): List<AtCoderSubmissionDto> {
        failure?.let { throw it }
        return submissions
    }
    override suspend fun fetchContests() = emptyList<AtCoderContestDto>()
    override suspend fun fetchMergedProblems() = emptyList<AtCoderMergedProblemDto>()
    override suspend fun fetchProblemModels() = emptyMap<String, AtCoderProblemModelDto>()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AtCoderAccountBindingTest {
    private lateinit var database: OjNexusDatabase
    private lateinit var adapter: BindingAtCoderAdapter
    private lateinit var repository: JudgeAccountRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OjNexusDatabase::class.java,
        ).allowMainThreadQueries().build()
        adapter = BindingAtCoderAdapter()
        repository = JudgeAccountRepository(
            database,
            JudgeRegistry(
                adapters = listOf(adapter),
                accountConnectors = listOf(AtCoderAccountConnector(adapter)),
            ),
            Clock.fixed(Instant.ofEpochMilli(100), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `matching public submission verifies and preserves returned case`() = runBlocking {
        adapter.submissions = listOf(
            AtCoderSubmissionDto(
                result = "AC", problemId = "abc350_a", userId = "CaseUser",
                epochSecond = 1, contestId = "abc350", id = 1,
            ),
        )

        val account = repository.connect(JudgeId.ATCODER, "  CaseUser  ")

        assertEquals("CaseUser", account.handle)
        assertEquals("CaseUser", account.canonicalHandle)
        assertEquals("VERIFIED", account.verificationState)
        assertEquals("COMMUNITY", account.sourceReliability)
        assertEquals(account.id, database.syncStateDao().findByJudge("atcoder")!!.accountId)
    }

    @Test
    fun `valid handle with no submissions connects as unverified without lowercasing`() = runBlocking {
        val account = repository.connect(JudgeId.ATCODER, "Mixed_Case")

        assertEquals("Mixed_Case", account.handle)
        assertEquals("Mixed_Case", account.canonicalHandle)
        assertEquals("UNVERIFIED", account.verificationState)
    }

    @Test
    fun `community outage is not misclassified as user not found`() = runBlocking {
        adapter.failure = AtCoderApiError.ServerError(503)

        val account = repository.connect(JudgeId.ATCODER, "PossiblyNew")

        assertEquals("UNVERIFIED", account.verificationState)
    }

    @Test
    fun `invalid handle format is rejected before network access`() {
        assertThrows(JudgeAccountRepository.ConnectError.InvalidHandle::class.java) {
            runBlocking { repository.connect(JudgeId.ATCODER, "bad handle!") }
        }
    }
}
