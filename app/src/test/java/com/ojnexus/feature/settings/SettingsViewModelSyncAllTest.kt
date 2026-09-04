package com.ojnexus.feature.settings

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.data.preferences.UserPreferencesRepository
import com.ojnexus.core.data.repository.BackupRepository
import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.repository.JudgeDataRepository
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.AccountBinding
import com.ojnexus.judge.AccountVerificationState
import com.ojnexus.judge.AdapterStatus
import com.ojnexus.judge.DataSourceReliability
import com.ojnexus.judge.JudgeAccountConnector
import com.ojnexus.judge.JudgeAdapter
import com.ojnexus.judge.JudgeCapability
import com.ojnexus.judge.JudgeRegistry
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelSyncAllTest {
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
    fun `sync all queues eligible accounts once and skips unsupported or disconnected judges`() = runBlocking {
        val codeforces = JudgeId.CODEFORCES
        val atCoder = JudgeId.ATCODER
        val registry = JudgeRegistry(
            adapters = listOf(
                FakeAdapter(codeforces, setOf(JudgeCapability.BACKGROUND_SYNC)),
                FakeAdapter(atCoder, emptySet()),
            ),
            accountConnectors = listOf(FakeConnector(codeforces), FakeConnector(atCoder)),
        )
        val eligible = account(codeforces, id = 11L)
        val unsupported = account(atCoder, id = 12L)
        database.judgeAccountDao().insert(eligible)
        database.judgeAccountDao().insert(unsupported)

        val enqueued = CopyOnWriteArrayList<Pair<JudgeId, Long>>()
        val viewModel = SettingsViewModel(
            accountRepository = JudgeAccountRepository(database, registry, Clock.systemUTC()),
            dataRepository = JudgeDataRepository(database),
            registry = registry,
            backupRepository = BackupRepository(database, ApplicationProvider.getApplicationContext()),
            preferencesRepository = UserPreferencesRepository(ApplicationProvider.getApplicationContext()),
            manualSyncEnqueuer = { judge, accountId -> enqueued += judge to accountId },
        )

        val connections = listOf(
            JudgeConnectionUi(
                judge = codeforces,
                account = eligible,
                profile = null,
                syncState = null,
                capabilities = setOf(JudgeCapability.BACKGROUND_SYNC),
                reliability = DataSourceReliability.OFFICIAL,
            ),
            JudgeConnectionUi(
                judge = atCoder,
                account = unsupported,
                profile = null,
                syncState = null,
                capabilities = emptySet(),
                reliability = DataSourceReliability.OFFICIAL,
            ),
            JudgeConnectionUi(
                judge = JudgeId.LUOGU,
                account = null,
                profile = null,
                syncState = null,
                capabilities = setOf(JudgeCapability.BACKGROUND_SYNC),
                reliability = DataSourceReliability.OFFICIAL,
            ),
        )
        viewModel.syncAll(connections)
        viewModel.syncAll(connections)
        waitUntil { enqueued.size == 1 && !viewModel.syncAllInFlight.value }

        assertEquals(listOf(codeforces to 11L), enqueued.toList())
        assertEquals(SyncPhase.QUEUED.name, database.syncStateDao().findByJudge(codeforces.id)?.state)
        assertEquals(null, database.syncStateDao().findByJudge(atCoder.id))
    }

    private fun account(judge: JudgeId, id: Long) = JudgeAccountEntity(
        id = id,
        judge = judge.id,
        handle = "raw",
        canonicalHandle = "canonical",
        connectedAt = 1L,
        updatedAt = 1L,
    )

    private suspend fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            delay(20)
        }
        check(condition()) { "condition was not met before timeout" }
    }

    private class FakeAdapter(
        override val id: JudgeId,
        override val capabilities: Set<JudgeCapability>,
    ) : JudgeAdapter {
        override val reliability = DataSourceReliability.OFFICIAL
        override suspend fun status() = AdapterStatus.AVAILABLE
    }

    private class FakeConnector(override val judgeId: JudgeId) : JudgeAccountConnector {
        override suspend fun bind(rawHandle: String) = AccountBinding(
            storedHandle = rawHandle,
            canonicalHandle = rawHandle,
            verificationState = AccountVerificationState.VERIFIED,
            reliability = DataSourceReliability.OFFICIAL,
        )
    }
}
