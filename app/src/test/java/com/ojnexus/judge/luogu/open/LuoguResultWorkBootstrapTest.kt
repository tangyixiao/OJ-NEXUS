package com.ojnexus.judge.luogu.open

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.SubmissionJobEntity
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
class LuoguResultWorkBootstrapTest {
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
    fun `startup reconciliation is bounded and isolates one scheduler failure`() = runBlocking {
        repeat(51) { index ->
            database.submissionJobDao().insert(
                SubmissionJobEntity(
                    judge = "luogu",
                    requestId = "req-$index",
                    kind = SubmissionJobKind.PROBLEM.name,
                    pid = "P$index",
                    language = "cxx/14/gcc",
                    status = SubmissionJobStatus.PENDING.name,
                    createdAt = index.toLong(),
                    updatedAt = index.toLong(),
                ),
            )
        }
        val enqueued = mutableListOf<String>()
        val scheduler = object : LuoguResultWorkScheduler {
            override fun enqueue(requestId: String) {
                enqueued += requestId
                if (requestId == "req-10") throw IllegalStateException("duplicate queue")
            }
        }

        LuoguResultWorkBootstrap(database.submissionJobDao(), scheduler).reconcilePending()

        assertEquals(50, enqueued.size)
        assertEquals("req-0", enqueued.first())
        assertEquals("req-49", enqueued.last())
    }
}
