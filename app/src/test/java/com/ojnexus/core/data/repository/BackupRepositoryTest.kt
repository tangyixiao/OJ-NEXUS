package com.ojnexus.core.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ProblemEntity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: OjNexusDatabase
    private lateinit var output: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.databaseBuilder(
            context,
            OjNexusDatabase::class.java,
            "backup-test-${System.nanoTime()}.db",
        ).setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).build()
        output = File(context.cacheDir, "oj-nexus-backup-test-${System.nanoTime()}.db")
    }

    @After
    fun tearDown() {
        database.close()
        output.delete()
        File(context.filesDir, "oj-nexus-pending-restore.db").delete()
        File(context.filesDir, "oj-nexus-pending-restore.db.tmp").delete()
        context.getDatabasePath("oj-nexus.db").delete()
    }

    @Test
    fun `export writes a readable copy of the local database`() = runBlocking {
        database.problemDao().insert(
            ProblemEntity(
                judge = "codeforces",
                externalId = "1A",
                title = "Theatre Square",
                difficulty = 800,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        val exported = BackupRepository(database, context).exportTo(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Uri.fromFile(output),
        )

        assertTrue(exported)
        assertTrue(output.isFile)
        assertTrue(output.length() > 0L)
        SQLiteDatabase.openDatabase(output.path, null, SQLiteDatabase.OPEN_READONLY).use { copy ->
            copy.rawQuery("SELECT COUNT(*) FROM problems", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getInt(0) > 0)
            }
        }
    }

    @Test
    fun `import validates an exported database and restores it on next startup`() = runBlocking {
        database.problemDao().insert(
            ProblemEntity(
                judge = "codeforces",
                externalId = "4A",
                title = "Watermelon",
                difficulty = 800,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        val repository = BackupRepository(database, context)
        assertTrue(
            repository.exportTo(
                context.contentResolver,
                Uri.fromFile(output),
            ),
        )
        assertTrue(repository.importFrom(context.contentResolver, Uri.fromFile(output)))

        database.close()
        assertTrue(BackupRepository.restorePending(context))
        SQLiteDatabase.openDatabase(
            context.getDatabasePath("oj-nexus.db").path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { restored ->
            restored.rawQuery("SELECT COUNT(*) FROM problems", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getInt(0) > 0)
            }
        }
    }

    @Test
    fun `import rejects a non database file`() = runBlocking {
        output.writeText("not an sqlite database")

        val imported = BackupRepository(database, context).importFrom(
            context.contentResolver,
            Uri.fromFile(output),
        )

        assertFalse(imported)
        assertFalse(File(context.filesDir, "oj-nexus-pending-restore.db").exists())
    }
}
