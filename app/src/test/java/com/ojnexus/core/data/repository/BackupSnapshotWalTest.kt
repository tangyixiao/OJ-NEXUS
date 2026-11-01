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
class BackupSnapshotWalTest {
    private lateinit var context: Context
    private lateinit var database: OjNexusDatabase
    private lateinit var databaseFile: File
    private lateinit var archive: File
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val uniqueId = System.nanoTime()
        databaseName = "wal-$uniqueId.db"
        database = Room.databaseBuilder(context, OjNexusDatabase::class.java, databaseName)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        databaseFile = context.getDatabasePath(databaseName)
        archive = File(context.cacheDir, "wal-$uniqueId.zip")
    }

    @After
    fun tearDown() {
        database.close()
        archive.delete()
        databaseFile.delete()
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
    }

    @Test
    fun `snapshot contains committed rows still in wal`() = runBlocking {
        seedProblem("1A", "Base row")
        database.openHelper.writableDatabase.query("PRAGMA wal_autocheckpoint=0").use { it.moveToFirst() }
        seedProblem("2B", "Committed after checkpoint was disabled")

        val walFile = File(databaseFile.path + "-wal")
        assertTrue("test must leave committed data in SQLite WAL", walFile.isFile && walFile.length() > 0)

        val repository = BackupRepository(database, context)
        assertTrue(repository.exportSnapshot(context.contentResolver, Uri.fromFile(archive)))

        val extractedDatabase = File(context.cacheDir, "wal-export-$databaseName")
        try {
            ZipFile(archive).use { zip ->
                assertEquals(
                    listOf(SnapshotManifestCodec.MANIFEST_ENTRY, SnapshotManifestCodec.DATABASE_ENTRY),
                    zip.entries().toList().map { it.name },
                )
                val manifest = zip.getInputStream(zip.getEntry(SnapshotManifestCodec.MANIFEST_ENTRY)).use { input ->
                    SnapshotManifestCodec.decode(input.readBytes().toString(Charsets.UTF_8))
                }
                assertEquals(2, manifest?.rowsFor("problems"))
                zip.getInputStream(zip.getEntry(SnapshotManifestCodec.DATABASE_ENTRY)).use { input ->
                    extractedDatabase.outputStream().use(input::copyTo)
                }
            }

            assertTrue("archived database must stand alone without a WAL sidecar", extractedDatabase.isFile)
            SQLiteDatabase.openDatabase(
                extractedDatabase.path,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { snapshotDb ->
                snapshotDb.rawQuery("SELECT COUNT(*) FROM problems", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(2, cursor.getInt(0))
                }
                snapshotDb.rawQuery("SELECT title FROM problems WHERE external_id='2B'", null).use { cursor ->
                    assertTrue("the newest WAL-only row must be present", cursor.moveToFirst())
                    assertEquals("Committed after checkpoint was disabled", cursor.getString(0))
                }
            }

            val preview = repository.inspectSnapshot(context.contentResolver, Uri.fromFile(archive))
            assertEquals(2, preview?.rows?.firstOrNull { it.table == "problems" }?.incoming)
        } finally {
            extractedDatabase.delete()
        }
    }

    @Test
    fun `unwritable destination preserves source and cleans snapshot`() = runBlocking {
        seedProblem("1A", "Preserved active row")
        val destinationContents = "existing destination contents".toByteArray()
        val destinationDirectory = File(context.cacheDir, "destination-$databaseName").apply { mkdir() }
        val sentinel = File(destinationDirectory, "sentinel.txt").apply { writeBytes(destinationContents) }
        val snapshotsBefore = snapshotFiles()

        val repository = BackupRepository(database, context)
        assertTrue(
            "snapshot export must report failure when the destination is a directory",
            !repository.exportSnapshot(context.contentResolver, Uri.fromFile(destinationDirectory)),
        )
        assertTrue(destinationContents.contentEquals(sentinel.readBytes()))
        assertEquals(snapshotsBefore, snapshotFiles())
        assertEquals(1, database.problemDao().count())
        destinationDirectory.deleteRecursively()
        Unit
    }

    private fun snapshotFiles(): Set<String> = context.cacheDir.listFiles()
        .orEmpty()
        .filter { it.name.startsWith("ojn-") && it.name.endsWith(".db") }
        .map { it.name }
        .toSet()

    private suspend fun seedProblem(externalId: String, title: String) {
        database.problemDao().insert(
            ProblemEntity(
                judge = "codeforces",
                externalId = externalId,
                title = title,
                difficulty = 800,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
    }
}
