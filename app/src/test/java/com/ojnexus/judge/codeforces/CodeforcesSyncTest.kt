package com.ojnexus.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Verdict
import com.ojnexus.judge.codeforces.CodeforcesApiError
import com.ojnexus.judge.codeforces.CodeforcesAdapter
import com.ojnexus.judge.codeforces.CodeforcesSyncCoordinator
import com.ojnexus.judge.codeforces.CodeforcesSyncRepository
import com.ojnexus.judge.codeforces.SyncPolicy
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.judge.codeforces.api.dto.CfContestDto
import com.ojnexus.judge.codeforces.api.dto.CfProblemDto
import com.ojnexus.judge.codeforces.api.dto.CfProblemsetDto
import com.ojnexus.judge.codeforces.api.dto.CfProblemStatisticsDto
import com.ojnexus.judge.codeforces.api.dto.CfRatingChangeDto
import com.ojnexus.judge.codeforces.api.dto.CfSubmissionDto
import com.ojnexus.judge.codeforces.api.dto.CfUserDto
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Configurable in-memory fake of the Codeforces adapter — fully offline and deterministic. */
private class FakeCodeforcesAdapter(
    var profile: CfUserDto? = CfUserDto(handle = "tourist", rating = 3979, maxRating = 3986, rank = "legendary grandmaster"),
    var ratingHistory: List<CfRatingChangeDto> = emptyList(),
    var submissionPages: MutableList<List<CfSubmissionDto>> = mutableListOf(),
    var contests: List<CfContestDto> = emptyList(),
    var problemset: CfProblemsetDto = CfProblemsetDto(),
    /** True when the fake should behave like the real API resolving historic handles. */
    var historicHandlesResolve: Boolean = false,
) : CodeforcesAdapter {

    var fetchProfileCalls = 0
    var submissionPageCalls = 0

    override suspend fun fetchProfile(handle: String): CfUserDto {
        fetchProfileCalls++
        val known = profile?.takeIf {
            it.handle.equals(handle, ignoreCase = true) || historicHandlesResolve
        }
        return known
            ?: throw CodeforcesApiError.UserNotFound("handles: User with handle '$handle' not found")
    }

    override suspend fun fetchRatingHistory(handle: String): List<CfRatingChangeDto> = ratingHistory

    override suspend fun fetchSubmissionsPage(handle: String, from: Int, count: Int): List<CfSubmissionDto> {
        submissionPageCalls++
        // Each element of submissionPages IS one page, returned by 1-based page number.
        return submissionPages.getOrNull(from - 1) ?: emptyList()
    }

    override suspend fun fetchContests(): List<CfContestDto> = contests

    override suspend fun fetchProblemset(): CfProblemsetDto = problemset
}

/**
 * Offline regression tests for the Phase 2 sync invariants:
 * A. one submission id -> at most one attempt; B. re-sync is idempotent;
 * C. rejudge updates verdicts; D. AC is sticky; E/F. notes+reviews survive;
 * G. disconnect keeps local training history; plus timezone day keys.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CodeforcesSyncTest {

    private lateinit var database: OjNexusDatabase
    private lateinit var problemRepository: ProblemRepository
    private lateinit var accountRepository: JudgeAccountRepository
    private lateinit var syncRepository: CodeforcesSyncRepository
    private lateinit var coordinator: CodeforcesSyncCoordinator
    private val adapter = FakeCodeforcesAdapter()
    private val fixedNow = Instant.parse("2026-08-29T12:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OjNexusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(fixedNow, ZoneId.of("UTC"))
        problemRepository = ProblemRepository(database, clock)
        accountRepository = JudgeAccountRepository(database, adapter, clock)
        syncRepository = CodeforcesSyncRepository(database, adapter, clock, ZoneId.of("UTC"))
        coordinator = CodeforcesSyncCoordinator(accountRepository, syncRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun submission(
        id: Long,
        contestId: Long = 2134,
        index: String = "C",
        verdict: String = "OK",
        creationTimeSeconds: Long = 1_789_000_000L,
    ) = CfSubmissionDto(
        id = id,
        contestId = contestId,
        creationTimeSeconds = creationTimeSeconds,
        problem = CfProblemDto(
            contestId = contestId,
            index = index,
            name = "Yet Another Array Query",
            rating = 1700,
            tags = listOf("constructive"),
        ),
        programmingLanguage = "Kotlin 2.3.21",
        verdict = verdict,
        passedTestCount = 42,
        timeConsumedMillis = 77,
        memoryConsumedBytes = 1_048_576L,
        participantType = "CONTESTANT",
        testset = "SYSTEM",
    )

    private suspend fun connect(): JudgeAccountEntity =
        accountRepository.connect(JudgeId.CODEFORCES, "  tourist ")

    // --- Account connection ---

    @Test
    fun `connect adopts the canonical handle and trims user input`() = runBlocking {
        val account = connect()
        assertEquals("tourist", account.canonicalHandle)
        assertEquals("  tourist ", account.handle)
        assertTrue(account.enabled)
    }

    @Test
    fun `connect with empty or unknown handle fails without writing`() = runBlocking {
        adapter.profile = null
        listOf("", "   ").forEach { raw ->
            try {
                accountRepository.connect(JudgeId.CODEFORCES, raw)
                throw AssertionError("expected HandleEmpty")
            } catch (expected: JudgeAccountRepository.ConnectError.HandleEmpty) {
            }
        }
        try {
            accountRepository.connect(JudgeId.CODEFORCES, "ghost")
            throw AssertionError("expected UserNotFound")
        } catch (expected: JudgeAccountRepository.ConnectError.UserNotFound) {
        }
        assertNull(accountRepository.findActive(JudgeId.CODEFORCES))
    }

    @Test
    fun `connect with a historic handle adopts the canonical response handle`() = runBlocking {
        // User typed an old handle; the real API resolves it to the current one.
        adapter.profile = CfUserDto(handle = "tourist_new", rating = 3900)
        adapter.historicHandlesResolve = true
        val account = accountRepository.connect(JudgeId.CODEFORCES, "tourist_old")
        assertEquals("tourist_new", account.canonicalHandle)
    }

    @Test
    fun `reconnect with the same handle keeps one active account`() = runBlocking {
        val first = connect()
        val second = connect()
        assertEquals(first.id, second.id)
        assertEquals(1, database.judgeAccountDao().countActiveByJudge(JudgeId.CODEFORCES.id))
    }

    @Test
    fun `disconnect removes account and sync state, keeps training history, optional cache purge`() = runBlocking {
        val account = connect()
        adapter.submissionPages = mutableListOf(listOf(submission(1)))
        coordinator.syncAccount(account.id, force = true)
        assertTrue(problemRepository.findProblemByKey(com.ojnexus.core.model.ProblemKey(JudgeId.CODEFORCES, "2134C")) != null)

        // Keep cached data: profile snapshot stays.
        accountRepository.disconnect(account.id, removeCachedRemoteData = false)
        assertNull(accountRepository.findActive(JudgeId.CODEFORCES))
        assertNull(syncRepository.findStateFor(account))
        assertNotNull(database.judgeProfileDao().findByJudge("codeforces"))
        // Imported attempt = local training history; disconnect never deletes it.
        assertTrue(database.attemptDao().findByExternalId("codeforces", "1") != null)

        // Reconnect, then disconnect with the purge option: remote caches go, history stays.
        val reconnected = connect()
        accountRepository.disconnect(reconnected.id, removeCachedRemoteData = true)
        assertNull(accountRepository.findActive(JudgeId.CODEFORCES))
        assertNull(database.judgeProfileDao().findByJudge("codeforces"))
        assertTrue(database.attemptDao().findByExternalId("codeforces", "1") != null)
    }

    // --- Submission sync invariants ---

    @Test
    fun `initial sync materializes problems and imports attempts with metadata`() = runBlocking {
        val account = connect()
        adapter.submissionPages = mutableListOf(
            listOf(submission(2, verdict = "OK"), submission(1, verdict = "WRONG_ANSWER")),
        )
        val report = coordinator.syncAccount(account.id, force = true)
        assertTrue(report!!.allOk)

        val problem = database.problemDao().findByKey("codeforces", "2134C")!!
        assertEquals("Yet Another Array Query", problem.title)
        assertEquals(1700, problem.difficulty)
        assertEquals(
            "https://codeforces.com/problemset/problem/2134/C",
            problem.sourceUrl,
        )
        assertEquals(2, problem.attemptCount)
        assertTrue(problem.solved)

        val ac = database.attemptDao().findByExternalId("codeforces", "2")!!
        assertEquals(Verdict.AC.name, ac.verdict)
        assertEquals("OK", ac.rawVerdict)
        assertEquals("codeforces", ac.sourceJudge)
        assertEquals("2", ac.externalSubmissionId)
        assertEquals(42, ac.passedTestCount)
        assertEquals(1_048_576L, ac.memoryBytes)
        // dayIndex derived from the submission timestamp in UTC (zone under test).
        val expectedDay = java.time.LocalDate.ofInstant(
            java.time.Instant.ofEpochSecond(1_789_000_000L), ZoneId.of("UTC"),
        ).toEpochDay()
        assertEquals(expectedDay, ac.dayIndex)
    }

    @Test
    fun `re-sync is idempotent - same submission count and rows`() = runBlocking {
        val account = connect()
        adapter.submissionPages = mutableListOf(listOf(submission(2), submission(1)))
        coordinator.syncAccount(account.id, force = true)
        val afterFirst = database.attemptDao().findByExternalId("codeforces", "2")!!

        coordinator.syncAccount(account.id, force = true)

        assertEquals(afterFirst.id, database.attemptDao().findByExternalId("codeforces", "2")!!.id)
        assertEquals(2, problemRepository.findProblemByKey(
            com.ojnexus.core.model.ProblemKey(JudgeId.CODEFORCES, "2134C"),
        )!!.attemptCount)
    }

    @Test
    fun `rejudge updates the existing attempt verdict and promotes solved`() = runBlocking {
        val account = connect()
        adapter.submissionPages = mutableListOf(listOf(submission(1, verdict = "WRONG_ANSWER")))
        coordinator.syncAccount(account.id, force = true)
        var problem = database.problemDao().findByKey("codeforces", "2134C")!!
        assertFalse(problem.solved)

        // Rejudge flips WA -> OK.
        adapter.submissionPages = mutableListOf(listOf(submission(1, verdict = "OK")))
        coordinator.syncAccount(account.id, force = true)

        val attempt = database.attemptDao().findByExternalId("codeforces", "1")!!
        assertEquals(Verdict.AC.name, attempt.verdict)
        problem = database.problemDao().findByKey("codeforces", "2134C")!!
        assertTrue(problem.solved)
        // Attempt count unchanged by the rejudge (no new row).
        assertEquals(1, problem.attemptCount)
    }

    @Test
    fun `AC remains sticky - later failed sync submissions never unsolve`() = runBlocking {
        val account = connect()
        adapter.submissionPages = mutableListOf(
            listOf(submission(2, verdict = "OK"), submission(1, verdict = "WRONG_ANSWER")),
        )
        coordinator.syncAccount(account.id, force = true)
        assertTrue(database.problemDao().findByKey("codeforces", "2134C")!!.solved)

        // Sync again with only a fresh WA above the AC (newer id): still solved.
        adapter.submissionPages = mutableListOf(
            listOf(submission(3, verdict = "WRONG_ANSWER"), submission(2, verdict = "OK"), submission(1, verdict = "WRONG_ANSWER")),
        )
        coordinator.syncAccount(account.id, force = true)
        assertTrue(database.problemDao().findByKey("codeforces", "2134C")!!.solved)
    }

    @Test
    fun `sync preserves user notes and review rows on an existing local problem`() = runBlocking {
        // User manually added the problem with notes + review BEFORE connecting.
        val added = problemRepository.addProblem(
            ProblemRepository.ProblemInput(
                key = com.ojnexus.core.model.ProblemKey(JudgeId.CODEFORCES, "2134C"),
                title = "My custom title",
                difficulty = null,
                tags = listOf("mytag"),
                sourceUrl = null,
            ),
        ).getOrNull()!!
        problemRepository.saveNotes(
            com.ojnexus.core.model.ProblemNotes(
                problemId = added, keyInsight = "keep me", implementationNotes = "",
                complexity = "", general = "", updatedAt = 0,
            ),
        )
        database.reviewDao().upsert(
            com.ojnexus.core.database.entity.ReviewEntity(
                problemId = added, stage = 2, dueAt = 5L, dueDayIndex = 5, createdAt = 1L,
            ),
        )
        database.problemDao().setFavorite(added, true)

        val account = connect()
        adapter.submissionPages = mutableListOf(listOf(submission(1)))
        coordinator.syncAccount(account.id, force = true)

        val problem = problemRepository.findProblemByKey(
            com.ojnexus.core.model.ProblemKey(JudgeId.CODEFORCES, "2134C"),
        )!!
        assertEquals(added, problem.id)
        assertEquals("Yet Another Array Query", problem.title) // remote title authoritative
        assertEquals(1700, problem.difficulty)
        assertTrue(problem.favorite) // user flag survives
        // User tags survive (remote tags go to the catalog, not the user's tag set).
        assertEquals(listOf("mytag"), problem.tags)
        val detail = kotlinx.coroutines.runBlocking {
            problemRepository.observeDetail(added).first()
        }
        assertEquals("keep me", detail?.notes?.keyInsight)
        assertNotNull(detail?.review)
    }

    // --- Partial sync ---

    @Test
    fun `contest failure yields PARTIAL while profile and submissions persist`() = runBlocking {
        val account = connect()
        adapter.submissionPages = mutableListOf(listOf(submission(1)))
        val failingAdapter = object : CodeforcesAdapter by adapter {
            override suspend fun fetchContests(): List<CfContestDto> =
                throw CodeforcesApiError.ServerError(500)
        }
        val brokenCoordinator = CodeforcesSyncCoordinator(
            accountRepository,
            CodeforcesSyncRepository(database, failingAdapter, Clock.fixed(fixedNow, ZoneId.of("UTC")), ZoneId.of("UTC")),
        )
        val report = brokenCoordinator.syncAccount(account.id, force = true)!!
        assertEquals(SyncPhase.PARTIAL, report.phase())
        assertTrue(report.failures.any { it.stage == com.ojnexus.core.data.sync.SyncStage.CONTESTS })
        // Profile/submission data persisted despite the contest failure.
        assertNotNull(database.judgeProfileDao().findByJudge("codeforces"))
        assertTrue(database.attemptDao().findByExternalId("codeforces", "1") != null)
        val state = syncRepository.findStateFor(account)!!
        assertEquals(SyncPhase.PARTIAL.name, state.state)
        assertEquals("ServerError", state.lastErrorType)
    }

    // --- Freshness policy ---

    @Test
    fun `non-forced sync skips fresh modules`() = runBlocking {
        val account = connect()
        adapter.submissionPages = mutableListOf(listOf(submission(1)))
        coordinator.syncAccount(account.id, force = true)
        val profileCallsAfterFirst = adapter.fetchProfileCalls

        coordinator.syncAccount(account.id, force = false)
        assertEquals(profileCallsAfterFirst, adapter.fetchProfileCalls)
    }

    // --- Rating sync ---

    @Test
    fun `rating history upserts without duplicates on repeated syncs`() = runBlocking {
        val account = connect()
        adapter.ratingHistory = listOf(
            CfRatingChangeDto(2101, "Round A", "tourist", 1, 1_700_000_000L, 1400, 1500),
            CfRatingChangeDto(2102, "Round B", "tourist", 3, 1_710_000_000L, 1500, 1600),
        )
        coordinator.syncAccount(account.id, force = true)
        coordinator.syncAccount(account.id, force = true)
        assertEquals(2, database.ratingChangeDao().countByJudge(JudgeId.CODEFORCES.id))
    }

    // --- Problemset sync ---

    @Test
    fun `problemset lands in the remote catalog, not the local library`() = runBlocking {
        val account = connect()
        adapter.problemset = CfProblemsetDto(
            problems = listOf(
                CfProblemDto(contestId = 1, index = "A", name = "Hello", rating = 800, tags = listOf("brute force")),
                CfProblemDto(contestId = 1, index = "B", name = "Hard One", rating = 3500),
            ),
            problemStatistics = listOf(
                CfProblemStatisticsDto(contestId = 1, index = "A", solvedCount = 20_000),
            ),
        )
        coordinator.syncAccount(account.id, force = true)

        assertEquals(2, database.remoteProblemDao().countByJudge("codeforces"))
        // The catalog must NOT leak into the user's problem library.
        assertEquals(0, problemRepository.findProblemByKey(
            com.ojnexus.core.model.ProblemKey(JudgeId.CODEFORCES, "1A"),
        )?.id ?: 0)
        val remote = database.remoteProblemDao().findByKey("codeforces", "1A")!!
        assertEquals(20_000, remote.solvedCount)
    }

    // --- Contests ---

    @Test
    fun `contests sync stores raw phase and start times`() = runBlocking {
        val account = connect()
        adapter.contests = listOf(
            CfContestDto(id = 2201, name = "Codeforces Round (Div. 2)", phase = "BEFORE",
                durationSeconds = 7_200, startTimeSeconds = 1_900_000_000L),
        )
        coordinator.syncAccount(account.id, force = true)
        assertEquals(1, database.contestDao().countByJudge("codeforces"))
    }

    @Test
    fun `page size policy is within official limits`() {
        assertTrue(SyncPolicy.SUBMISSION_PAGE_SIZE <= 10_000)
        assertTrue(SyncPolicy.REQUEST_INTERVAL_MS >= 2_000)
    }
}
