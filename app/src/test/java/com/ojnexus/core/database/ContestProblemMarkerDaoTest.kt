package com.ojnexus.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.entity.ContestProblemMarkerEntity
import com.ojnexus.core.database.entity.AttemptEntity
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.RemoteProblemEntity
import com.ojnexus.core.data.repository.ContestFocusRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import com.ojnexus.core.model.ContestMarker
import kotlinx.coroutines.flow.first
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
class ContestProblemMarkerDaoTest {
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
    fun `marker upsert is isolated by judge contest and problem`() = runBlocking {
        database.contestProblemMarkerDao().upsert(
            ContestProblemMarkerEntity(
                judge = "atcoder",
                contestId = "abc350",
                problemExternalId = "abc350_a",
                marker = ContestMarker.WORKING.name,
                updatedAt = 1,
            ),
        )
        database.contestProblemMarkerDao().upsert(
            ContestProblemMarkerEntity(
                judge = "codeforces",
                contestId = "abc350",
                problemExternalId = "abc350_a",
                marker = ContestMarker.SOLVED.name,
                updatedAt = 2,
            ),
        )

        val rows = database.contestProblemMarkerDao()
            .observeByContest("atcoder", "abc350")
            .first()

        assertEquals(1, rows.size)
        assertEquals(ContestMarker.WORKING.name, rows.single().marker)
    }

    @Test
    fun `contest progress joins cached problems with local submission state`() = runBlocking {
        database.remoteProblemDao().upsertAll(
            listOf(
                RemoteProblemEntity(
                    judge = "atcoder",
                    externalId = "abc350_a",
                    contestId = "abc350",
                    index = "A",
                    name = "A",
                    updatedAt = 1,
                ),
            ),
        )
        val localId = database.problemDao().insert(
            ProblemEntity(
                judge = "atcoder",
                externalId = "abc350_a",
                title = "A",
                difficulty = null,
                createdAt = 1,
                updatedAt = 1,
                solved = true,
                attemptCount = 2,
            ),
        )
        database.attemptDao().insert(AttemptEntity(problemId = localId, timestamp = 10, dayIndex = 0, verdict = "WA"))
        database.attemptDao().insert(AttemptEntity(problemId = localId, timestamp = 20, dayIndex = 0, verdict = "AC"))

        val rows = database.remoteProblemDao()
            .observeContestProgress("atcoder", "abc350")
            .first()

        assertEquals(1, rows.size)
        assertEquals(localId, rows.single().localProblemId)
        assertEquals(true, rows.single().solved)
        assertEquals(2, rows.single().attemptCount)
        assertEquals("AC", rows.single().latestVerdict)
    }

    @Test
    fun `focus repository cycles a marker and removes it after the final state`() = runBlocking {
        val repository = ContestFocusRepository(
            database,
            Clock.fixed(Instant.ofEpochMilli(100), ZoneOffset.UTC),
        )

        repository.cycleMarker("atcoder", "abc350", "abc350_a")
        assertEquals(
            ContestMarker.WORKING.name,
            database.contestProblemMarkerDao().find("atcoder", "abc350", "abc350_a")?.marker,
        )
        repository.cycleMarker("atcoder", "abc350", "abc350_a")
        assertEquals(
            ContestMarker.SOLVED.name,
            database.contestProblemMarkerDao().find("atcoder", "abc350", "abc350_a")?.marker,
        )
        repository.cycleMarker("atcoder", "abc350", "abc350_a")
        repository.cycleMarker("atcoder", "abc350", "abc350_a")
        assertEquals(null, database.contestProblemMarkerDao().find("atcoder", "abc350", "abc350_a"))
    }
}
