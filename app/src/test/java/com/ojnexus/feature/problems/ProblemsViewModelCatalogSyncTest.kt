package com.ojnexus.feature.problems

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.data.repository.JudgeDataRepository
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.data.sync.StageOutcome
import com.ojnexus.core.data.sync.SyncStage
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.luogu.LuoguPublicCatalogSync
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProblemsViewModelCatalogSyncTest {
    private lateinit var database: OjNexusDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OjNexusDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `Luogu catalog action exposes imported count after success`() = runBlocking {
        val viewModel = viewModel(FixedCatalogSync(StageOutcome(SyncStage.PROBLEMS, true, itemsProcessed = 42)))

        viewModel.setRemoteJudge(JudgeId.LUOGU)
        viewModel.syncLuoguCatalog()
        awaitCatalogIdle(viewModel)

        assertEquals(42, viewModel.remoteState.value.catalogSyncItems)
        assertNull(viewModel.remoteState.value.catalogSyncError)
    }

    @Test
    fun `Luogu catalog action suppresses duplicate starts while busy`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger(0)
        val sync = LuoguPublicCatalogSync {
            calls.incrementAndGet()
            started.complete(Unit)
            release.await()
            StageOutcome(SyncStage.PROBLEMS, true, itemsProcessed = 1)
        }
        val viewModel = viewModel(sync)

        viewModel.setRemoteJudge(JudgeId.LUOGU)
        viewModel.syncLuoguCatalog()
        started.await()
        viewModel.syncLuoguCatalog()
        assertEquals(1, calls.get())

        release.complete(Unit)
        awaitCatalogIdle(viewModel)
        assertEquals(1, viewModel.remoteState.value.catalogSyncItems)
    }

    @Test
    fun `Luogu catalog action exposes a failed outcome`() = runBlocking {
        val viewModel = viewModel(
            FixedCatalogSync(
                StageOutcome(
                    stage = SyncStage.PROBLEMS,
                    ok = false,
                    errorType = "Network",
                    errorMessage = "offline",
                    itemsProcessed = 3,
                ),
            ),
        )

        viewModel.setRemoteJudge(JudgeId.LUOGU)
        viewModel.syncLuoguCatalog()
        awaitCatalogIdle(viewModel)

        assertEquals(3, viewModel.remoteState.value.catalogSyncItems)
        assertEquals("offline", viewModel.remoteState.value.catalogSyncError)
    }

    private fun viewModel(sync: LuoguPublicCatalogSync) = ProblemsViewModel(
        repository = ProblemRepository(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC),
        ),
        judgeDataRepository = JudgeDataRepository(database),
        publicCatalogSync = sync,
    )

    private suspend fun awaitCatalogIdle(viewModel: ProblemsViewModel) {
        withTimeout(5_000) {
            while (viewModel.remoteState.value.catalogSyncing) delay(5)
        }
    }

    private class FixedCatalogSync(
        private val outcome: StageOutcome,
    ) : LuoguPublicCatalogSync {
        override suspend fun syncPublicProblemCatalog(force: Boolean): StageOutcome = outcome
    }
}
