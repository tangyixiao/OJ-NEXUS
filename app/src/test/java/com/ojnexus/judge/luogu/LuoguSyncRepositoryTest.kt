package com.ojnexus.judge.luogu

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.sync.SyncStage
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.luogu.api.dto.LuoguContestDto
import com.ojnexus.judge.luogu.api.dto.LuoguContestListData
import com.ojnexus.judge.luogu.api.dto.LuoguContestListResponse
import com.ojnexus.judge.luogu.api.dto.LuoguContestPageDto
import com.ojnexus.judge.luogu.api.dto.LuoguEloEntryDto
import com.ojnexus.judge.luogu.api.dto.LuoguProblemDto
import com.ojnexus.judge.luogu.api.dto.LuoguProblemListData
import com.ojnexus.judge.luogu.api.dto.LuoguProblemListResponse
import com.ojnexus.judge.luogu.api.dto.LuoguProblemPageDto
import com.ojnexus.judge.luogu.api.dto.LuoguPublicUserDto
import com.ojnexus.judge.luogu.api.dto.LuoguRatingSummaryDto
import com.ojnexus.judge.luogu.api.dto.LuoguRecordPageResponse
import com.ojnexus.judge.luogu.api.dto.LuoguUserPageData
import com.ojnexus.judge.luogu.api.dto.LuoguUserPageResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LuoguSyncRepositoryTest {
    private lateinit var database: OjNexusDatabase
    private lateinit var accounts: JudgeAccountRepository
    private lateinit var repository: LuoguSyncRepository
    private val adapter = FakeLuoguSyncAdapter()
    private val now = Instant.parse("2026-08-30T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("UTC"))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OjNexusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accounts = JudgeAccountRepository(
            database,
            com.ojnexus.judge.JudgeRegistry(
                adapters = listOf(adapter),
                accountConnectors = listOf(LuoguAccountConnector(adapter)),
            ),
            clock,
        )
        repository = LuoguSyncRepository(database, adapter, clock, ZoneId.of("UTC"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `public stages persist profile rating catalog and contests idempotently`() = runBlocking {
        val account = connect()

        assertTrue(repository.syncProfile(account, force = true).ok)
        assertTrue(repository.syncRating(account, force = true).ok)
        assertTrue(repository.syncContests(account, force = true).ok)
        assertTrue(repository.syncProblems(account, force = true).ok)
        assertTrue(repository.syncContests(account, force = true).ok)
        assertTrue(repository.syncProblems(account, force = true).ok)

        val profile = database.judgeProfileDao().findByJudge(JudgeId.LUOGU.id)
        assertEquals("alice", profile?.handle)
        assertEquals(1200, profile?.rating)
        assertEquals("bio", profile?.introduction)
        assertEquals(1, database.ratingChangeDao().countByJudge(JudgeId.LUOGU.id))
        assertEquals(2, database.remoteProblemDao().countByJudge(JudgeId.LUOGU.id))
        assertEquals(1, database.contestDao().countByJudge(JudgeId.LUOGU.id))
    }

    @Test
    fun `catalog sync stops at reported count and preserves prior page after later failure`() = runBlocking {
        val account = connect()
        adapter.problemFailurePage = 2

        val outcome = repository.syncProblems(account, force = true)

        assertFalse(outcome.ok)
        assertEquals(1, outcome.itemsProcessed)
        assertNotNull(database.remoteProblemDao().findByKey(JudgeId.LUOGU.id, "P1000"))
        assertEquals(null, database.syncStateDao().findByJudge(JudgeId.LUOGU.id)?.problemsetSyncedAt)

        adapter.problemFailurePage = null
        adapter.problemPageCalls = 0
        repository.syncProblems(account, force = true)
        assertEquals(2, adapter.problemPageCalls)
        assertEquals(2, database.remoteProblemDao().countByJudge(JudgeId.LUOGU.id))
    }

    @Test
    fun `official problemset dump imports in place of paged catalog`() = runBlocking {
        val account = connect()
        adapter.useProblemsetDump = true
        adapter.problemsetDump = gzip(
            """
            {"pid":"P1000","type":"P","difficulty":1,"tags":["math"],"title":"A+B"}
            {"pid":"P1001","type":"P","difficulty":2,"tags":["dp"],"title":"A-B"}
            """.trimIndent(),
        )

        val outcome = repository.syncProblems(account, force = true)

        assertTrue(outcome.ok)
        assertEquals(2, outcome.itemsProcessed)
        assertEquals(0, adapter.problemPageCalls)
        assertEquals(2, database.remoteProblemDao().countByJudge(JudgeId.LUOGU.id))
        assertEquals("A+B", database.remoteProblemDao().findByKey(JudgeId.LUOGU.id, "P1000")?.name)
    }

    @Test
    fun `anonymous submissions report authentication required without importing attempts`() = runBlocking {
        val account = connect()

        val outcome = repository.syncSubmissions(account, force = true)

        assertFalse(outcome.ok)
        assertEquals(SyncStage.SUBMISSIONS, outcome.stage)
        assertEquals("AuthenticationRequired", outcome.errorType)
        assertEquals(0, outcome.itemsProcessed)
    }

    @Test
    fun `catalog page budget fails instead of silently truncating`() = runBlocking {
        val account = connect()
        val limited = LuoguSyncRepository(
            database = database,
            adapter = adapter,
            clock = clock,
            zone = ZoneId.of("UTC"),
            maxCatalogPages = 1,
        )

        val outcome = limited.syncProblems(account, force = true)

        assertFalse(outcome.ok)
        assertEquals("ParseError", outcome.errorType)
        assertEquals(1, outcome.itemsProcessed)
    }

    private suspend fun connect(): JudgeAccountEntity =
        accounts.connect(JudgeId.LUOGU, "alice")

    private fun gzip(value: String): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).bufferedWriter().use { writer -> writer.write(value) }
    }.toByteArray()
}

private class FakeLuoguSyncAdapter : LuoguAdapter {
    var useProblemsetDump = false
    var problemsetDump = ByteArray(0)
    var problemFailurePage: Int? = null
    var problemPageCalls = 0

    override val supportsProblemsetDump: Boolean
        get() = useProblemsetDump

    override suspend fun openProblemsetDump() = ByteArrayInputStream(problemsetDump)

    override suspend fun searchUser(handle: String) =
        com.ojnexus.judge.luogu.api.dto.LuoguUserSummary(uid = 7, name = handle)

    override suspend fun fetchUserPage(uid: Long) = LuoguUserPageResponse(
        data = LuoguUserPageData(
            user = LuoguPublicUserDto(
                uid = uid,
                name = "alice",
                introduction = "bio",
                slogan = "hello",
                eloValue = 1200,
                followerCount = 8,
            ),
            gu = LuoguRatingSummaryDto(rating = 1200),
        ),
    )

    override suspend fun fetchPracticePage(uid: Long) = LuoguUserPageResponse(
        data = LuoguUserPageData(
            user = LuoguPublicUserDto(uid = uid, name = "alice", eloValue = 1200),
            elo = listOf(
                LuoguEloEntryDto(
                    rating = 1200,
                    time = 1_700_000_000,
                    contest = com.ojnexus.judge.luogu.api.dto.LuoguRatingContestDto(
                        id = 99,
                        name = "Round",
                    ),
                ),
            ),
        ),
    )

    override suspend fun fetchProblemPage(page: Int): LuoguProblemListResponse {
        problemPageCalls++
        if (problemFailurePage == page) throw LuoguApiError.ServerError(500)
        val result = if (page == 1) {
            listOf(LuoguProblemDto(pid = "P1000", name = "A+B", difficulty = 1))
        } else if (page == 2) {
            listOf(LuoguProblemDto(pid = "P1001", name = "A-B", difficulty = 2))
        } else {
            emptyList()
        }
        return LuoguProblemListResponse(
            data = LuoguProblemListData(
                problems = LuoguProblemPageDto(perPage = 1, count = 2, result = result),
            ),
        )
    }

    override suspend fun fetchContestPage(page: Int) = LuoguContestListResponse(
        data = LuoguContestListData(
            contests = LuoguContestPageDto(
                perPage = 20,
                count = 1,
                result = if (page == 1) listOf(
                    LuoguContestDto(
                        id = 12,
                        startTime = 1_700_000_000,
                        endTime = 1_700_003_600,
                        name = "Contest",
                    ),
                ) else emptyList(),
            ),
        ),
    )

    override suspend fun fetchRecordPage(uid: Long, page: Int): LuoguRecordPageResponse =
        throw LuoguApiError.AuthenticationRequired()

}
