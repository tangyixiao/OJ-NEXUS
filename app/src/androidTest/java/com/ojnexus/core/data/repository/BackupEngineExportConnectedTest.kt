package com.ojnexus.core.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ojnexus.core.data.backup.SnapshotManifestCodec
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ProblemEntity
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 127D: a committed backup export must capture every committed row on THIS device's
 * SQLite engine. On engines without VACUUM INTO (SQLite < 3.27, API 26-28) this exercises
 * the checkpoint fallback; on modern engines the VACUUM INTO path. The engine version is
 * asserted into the failure messages so each run's evidence names its real engine.
 */
@RunWith(AndroidJUnit4::class)
class BackupEngineExportConnectedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun exportCapturesCommittedRowsOnThisEngine() = runBlocking {
        val databaseName = "engine-export-${System.nanoTime()}.db"
        val database = Room.databaseBuilder(context, OjNexusDatabase::class.java, databaseName)
            .setJournalMode(RoomDatabase.JournalMode.AUTOMATIC)
            .build()
        try {
            database.problemDao().insert(
                ProblemEntity(
                    judge = "codeforces", externalId = "engine-row", title = "engine-row",
                    difficulty = 800, createdAt = 1L, updatedAt = 1L,
                ),
            )
            // Guarantee the WAL journal mode the export contract assumes on every engine.
            database.openHelper.writableDatabase.query("PRAGMA journal_mode=WAL").use { it.moveToFirst() }
            val engineVersion = database.openHelper.writableDatabase
                .query("SELECT sqlite_version()").use { it.moveToFirst(); it.getString(0) }
            val repository = BackupRepository(database, context)
            val zip = File(context.cacheDir, "engine-export-${System.nanoTime()}.zip")
            try {
                val exported = repository.exportSnapshot(context.contentResolver, Uri.fromFile(zip))
                assertTrue("export failed on engine $engineVersion", exported)
                val snapshotRows = ZipFile(zip).use { archive ->
                    val entry = archive.getEntry(SnapshotManifestCodec.DATABASE_ENTRY)
                    val snapshot = File(context.cacheDir, "engine-export-check-${System.nanoTime()}.db")
                    try {
                        archive.getInputStream(entry).use { input -> snapshot.outputStream().use(input::copyTo) }
                        val check = SQLiteDatabase.openDatabase(
                            snapshot.path, null, SQLiteDatabase.OPEN_READONLY,
                        )
                        try {
                            check.rawQuery(
                                "SELECT COUNT(*) FROM problems WHERE external_id = 'engine-row'", null,
                            ).use { cursor ->
                                cursor.moveToFirst()
                                cursor.getInt(0)
                            }
                        } finally {
                            check.close()
                        }
                    } finally {
                        snapshot.delete()
                    }
                }
                assertEquals(
                    "exported snapshot on engine $engineVersion must contain committed rows",
                    1,
                    snapshotRows,
                )
            } finally {
                zip.delete()
            }
        } finally {
            database.close()
            context.getDatabasePath(databaseName).delete()
            File(context.getDatabasePath(databaseName).path + "-wal").delete()
            File(context.getDatabasePath(databaseName).path + "-shm").delete()
        }
    }
}
