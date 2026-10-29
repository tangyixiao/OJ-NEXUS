package com.ojnexus.core.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.data.backup.SnapshotManifestCodec
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ProblemEntity
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 127D: engines below SQLite 3.27 export through the exclusive-checkpoint fallback,
 * and the fallback must capture committed WAL rows — never a stale main file. Modern engines
 * keep the VACUUM INTO path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupEngineExportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val opened = mutableListOf<OjNexusDatabase>()

    @After
    fun cleanup() {
        opened.forEach { runCatching { it.close() } }
        opened.clear()
    }

    private fun buildDatabase(): OjNexusDatabase {
        val name = "engine-export-${System.nanoTime()}.db"
        val database = Room.databaseBuilder(context, OjNexusDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.AUTOMATIC)
            .build()
        opened += database
        runBlocking {
            database.openHelper.writableDatabase.query("PRAGMA journal_mode=WAL").use { it.moveToFirst() }
            database.openHelper.writableDatabase.query("PRAGMA wal_autocheckpoint=0").use { it.moveToFirst() }
            database.problemDao().insert(
                ProblemEntity(
                    judge = "codeforces", externalId = "base-row", title = "base-row",
                    difficulty = 800, createdAt = 1L, updatedAt = 1L,
                ),
            )
        }
        return database
    }

    private fun exportedRows(zip: File): List<String> = ZipFile(zip).use { archive ->
        val entry = archive.getEntry(SnapshotManifestCodec.DATABASE_ENTRY)
        val snapshot = File(context.cacheDir, "engine-export-check-${System.nanoTime()}.db")
        try {
            archive.getInputStream(entry).use { input -> snapshot.outputStream().use(input::copyTo) }
            val check = SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                check.rawQuery(
                    "SELECT external_id FROM problems WHERE external_id IN ('base-row', 'wal-row') ORDER BY external_id",
                    null,
                ).use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
            } finally {
                check.close()
            }
        } finally {
            snapshot.delete()
        }
    }

    @Test
    fun `forced-old engine captures wal rows through the checkpoint fallback`() = runBlocking {
        val database = buildDatabase()
        database.problemDao().insert(
            ProblemEntity(
                judge = "codeforces", externalId = "wal-row", title = "wal-row",
                difficulty = 900, createdAt = 2L, updatedAt = 2L,
            ),
        )
        val databasePath = database.openHelper.writableDatabase.path
        assertTrue(
            "test precondition: the newest commit must sit in the WAL file",
            File("$databasePath-wal").length() > 0L,
        )
        val repository = BackupRepository(database, context, engineSupportsVacuumInto = { false })
        val zip = File(context.cacheDir, "engine-export-old-${System.nanoTime()}.zip")
        try {
            val exported = repository.exportSnapshot(context.contentResolver, Uri.fromFile(zip))
            assertTrue("fallback export must succeed on a pre-3.27 engine", exported)
            assertEquals(listOf("base-row", "wal-row"), exportedRows(zip))
        } finally {
            zip.delete()
        }
    }

    @Test
    fun `forced-modern engine keeps the vacuum path`() = runBlocking {
        val database = buildDatabase()
        val repository = BackupRepository(database, context, engineSupportsVacuumInto = { true })
        val zip = File(context.cacheDir, "engine-export-new-${System.nanoTime()}.zip")
        try {
            val exported = repository.exportSnapshot(context.contentResolver, Uri.fromFile(zip))
            assertTrue(exported)
            assertEquals(listOf("base-row"), exportedRows(zip))
        } finally {
            zip.delete()
        }
    }

    @Test
    fun `version parser orders sqlite triples`() {
        assertTrue(SqliteVersion.parse("3.19.4") < VACUUM_INTO_MIN_VERSION)
        assertTrue(SqliteVersion.parse("3.22.1") < VACUUM_INTO_MIN_VERSION)
        assertEquals(VACUUM_INTO_MIN_VERSION, SqliteVersion.parse("3.27.0"))
        assertEquals(VACUUM_INTO_MIN_VERSION, SqliteVersion.parse("3.27"))
        assertTrue(SqliteVersion.parse("3.28.0") > VACUUM_INTO_MIN_VERSION)
        assertEquals(SqliteVersion.parse("3.0.0"), SqliteVersion.parse("3"))
    }
}
