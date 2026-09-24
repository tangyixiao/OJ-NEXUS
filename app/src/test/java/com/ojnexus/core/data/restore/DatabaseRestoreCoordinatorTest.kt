package com.ojnexus.core.data.restore

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.data.repository.BackupRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseRestoreCoordinatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val source = File(context.cacheDir, "restore-coordinator-${System.nanoTime()}.db")
    private val databaseName = "restore-coordinator-live-${System.nanoTime()}.db"
    private var database: OjNexusDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        source.delete()
        File(context.filesDir, "oj-nexus-pending-restore.db").delete()
        File(context.filesDir, "oj-nexus-pending-restore.db.tmp").delete()
        File(context.filesDir, "oj-nexus-restore.journal").delete()
        File(context.filesDir, "oj-nexus-data-generation").delete()
        context.databaseList().filter { it.startsWith("restore-coordinator-live-") }.forEach {
            context.deleteDatabase(it)
        }
        context.deleteDatabase(OjNexusDatabase.DATABASE_NAME)
    }

    @Test
    fun `invalid sqlite is rejected with a stable failure`() {
        source.writeText("not sqlite")

        val outcome = DatabaseRestoreCoordinator(context).prepareImport(Uri.fromFile(source))

        assertEquals(RestoreOutcome.Rejected(RestoreFailure.INVALID_SQLITE), outcome)
    }

    @Test
    fun `valid export is staged and atomically applied before room opens`() = runBlocking {
        database = Room.databaseBuilder(context, OjNexusDatabase::class.java, databaseName)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .build()
        database!!.problemDao().insert(
            ProblemEntity(
                judge = "codeforces",
                externalId = "restore-identity",
                title = "Keep this identity",
                difficulty = 800,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        assertTrue(BackupRepository(database!!, context).exportTo(context.contentResolver, Uri.fromFile(source)))
        android.database.sqlite.SQLiteDatabase.openDatabase(
            source.path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        ).use { copied ->
            copied.rawQuery("PRAGMA user_version", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(OjNexusDatabase.CURRENT_SCHEMA_VERSION, cursor.getInt(0))
            }
        }
        database!!.close()
        database = null

        val coordinator = DatabaseRestoreCoordinator(context)
        val staged = coordinator.prepareImport(Uri.fromFile(source))
        assertTrue("outcome=$staged", staged is RestoreOutcome.Staged)
        assertTrue(File(context.filesDir, "oj-nexus-restore.journal").readText().isNotBlank())
        assertTrue(File(context.filesDir, "oj-nexus-pending-restore.db").isFile)
        val applied = coordinator.restoreBeforeRoomOpen()

        assertTrue(
            "outcome=$applied journal=${File(context.filesDir, "oj-nexus-restore.journal")
                .takeIf { it.isFile }?.readText()}",
            applied is RestoreOutcome.Applied,
        )
        val restored = Room.databaseBuilder(
            context,
            OjNexusDatabase::class.java,
            OjNexusDatabase.DATABASE_NAME,
        ).build()
        try {
            assertEquals(1, restored.problemDao().findByJudge("codeforces").size)
            assertEquals(
                "restore-identity",
                restored.problemDao().findByJudge("codeforces").single().externalId,
            )
        } finally {
            restored.close()
        }
    }
}
