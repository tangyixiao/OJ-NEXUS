package com.ojnexus.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.data.repository.RoomWorkspaceDraftRepository
import com.ojnexus.core.data.repository.WorkspaceDraft
import com.ojnexus.core.model.JudgeId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
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
class WorkspaceDraftRepositoryTest {
    private lateinit var database: OjNexusDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OjNexusDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `drafts are persisted and isolated by judge and problem`() = runBlocking {
        val repository = RoomWorkspaceDraftRepository(
            dao = database.workspaceDraftDao(),
            clock = Clock.fixed(Instant.ofEpochMilli(1_234L), ZoneOffset.UTC),
        )
        val luoguDraft = WorkspaceDraft(
            code = "int main() {}",
            input = "1 2",
            language = "cxx/14/gcc",
            o2 = true,
        )
        val otherDraft = luoguDraft.copy(code = "print(3)", language = "python3/c")

        repository.save(JudgeId.LUOGU, "P1001", luoguDraft)
        repository.save(JudgeId.LUOGU, "P1002", otherDraft)

        assertEquals(luoguDraft, repository.find(JudgeId.LUOGU, "P1001"))
        assertEquals(otherDraft, repository.find(JudgeId.LUOGU, "P1002"))
        assertNull(repository.find(JudgeId.CODEFORCES, "P1001"))
        assertEquals(1_234L, database.workspaceDraftDao().findByKey("luogu", "P1001")!!.updatedAt)
    }
}
