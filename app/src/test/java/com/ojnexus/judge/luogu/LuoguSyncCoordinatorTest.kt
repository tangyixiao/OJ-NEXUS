package com.ojnexus.judge.luogu

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.data.sync.SyncStage
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.JudgeCapability
import com.ojnexus.judge.JudgeRegistry
import com.ojnexus.judge.luogu.api.dto.LuoguContestListResponse
import com.ojnexus.judge.luogu.api.dto.LuoguProblemListResponse
import com.ojnexus.judge.luogu.api.dto.LuoguRecordPageResponse
import com.ojnexus.judge.luogu.api.dto.LuoguUserPageResponse
import com.ojnexus.judge.luogu.api.dto.LuoguUserPageData
import com.ojnexus.judge.luogu.api.dto.LuoguPublicUserDto
import com.ojnexus.judge.luogu.api.dto.LuoguRatingSummaryDto
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LuoguSyncCoordinatorTest {
    private lateinit var database: OjNexusDatabase
    private lateinit var accounts: JudgeAccountRepository
    private lateinit var coordinator: LuoguSyncCoordinator
    private val adapter = CoordinatorAdapter()
    private val clock = Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneId.of("UTC"))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OjNexusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val registry = JudgeRegistry(
            adapters = listOf(adapter),
            accountConnectors = listOf(LuoguAccountConnector(adapter)),
        )
        accounts = JudgeAccountRepository(database, registry, clock)
        coordinator = LuoguSyncCoordinator(
            accounts,
            LuoguSyncRepository(database, adapter, clock, ZoneId.of("UTC")),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `public stages run in order and finish successfully without submissions`() = runBlocking {
        val account = accounts.connect(JudgeId.LUOGU, "alice")

        val report = coordinator.syncAccount(account.id, force = true)

        val stageOrder = listOf("profile", "rating", "contests", "problems")
            .map { stage -> adapter.calls.indexOf(stage) }
        assertTrue(stageOrder.zipWithNext().all { (previous, next) -> previous < next })
        assertTrue("submissions" !in adapter.calls)
        assertEquals(SyncPhase.SUCCESS, report?.phase())
        assertTrue(report?.allOk == true)
        assertEquals(SyncStage.PROBLEMS, report?.outcomes?.last()?.stage)
        assertEquals(SyncPhase.SUCCESS.name, database.syncStateDao().findByJudge("luogu")?.state)
    }

    @Test
    fun `adapter does not advertise unauthenticated submissions`() {
        assertTrue(JudgeCapability.SUBMISSIONS !in adapter.capabilities)
    }
}

private class CoordinatorAdapter : LuoguAdapter {
    val calls = mutableListOf<String>()

    override suspend fun searchUser(handle: String) =
        com.ojnexus.judge.luogu.api.dto.LuoguUserSummary(uid = 7, name = handle)

    override suspend fun fetchUserPage(uid: Long): LuoguUserPageResponse {
        calls += "profile"
        return LuoguUserPageResponse(
            data = LuoguUserPageData(
                user = LuoguPublicUserDto(uid = uid, name = "alice", eloValue = 1200),
                gu = LuoguRatingSummaryDto(rating = 1200),
            ),
        )
    }

    override suspend fun fetchPracticePage(uid: Long): LuoguUserPageResponse {
        calls += "rating"
        return LuoguUserPageResponse(
            data = LuoguUserPageData(
                user = LuoguPublicUserDto(uid = uid, name = "alice", eloValue = 1200),
                gu = LuoguRatingSummaryDto(rating = 1200),
            ),
        )
    }

    override suspend fun fetchContestPage(page: Int): LuoguContestListResponse {
        if (page == 1) calls += "contests"
        return LuoguContestListResponse(
            data = com.ojnexus.judge.luogu.api.dto.LuoguContestListData(
                contests = com.ojnexus.judge.luogu.api.dto.LuoguContestPageDto(
                    count = 0,
                    result = emptyList(),
                ),
            ),
        )
    }

    override suspend fun fetchProblemPage(page: Int): LuoguProblemListResponse {
        if (page == 1) calls += "problems"
        return LuoguProblemListResponse(
            data = com.ojnexus.judge.luogu.api.dto.LuoguProblemListData(
                problems = com.ojnexus.judge.luogu.api.dto.LuoguProblemPageDto(
                    count = 0,
                    result = emptyList(),
                ),
            ),
        )
    }

    override suspend fun fetchRecordPage(uid: Long, page: Int): LuoguRecordPageResponse {
        calls += "submissions"
        throw LuoguApiError.AuthenticationRequired()
    }
}
