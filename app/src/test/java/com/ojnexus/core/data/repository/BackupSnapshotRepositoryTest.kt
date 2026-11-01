package com.ojnexus.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.data.backup.SnapshotManifestCodec
import com.ojnexus.core.data.restore.RestoreOutcome
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ProblemEntity
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupSnapshotRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: OjNexusDatabase
    private lateinit var output: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.databaseBuilder(
            context,
            OjNexusDatabase::class.java,
            "snapshot-test-${System.nanoTime()}.db",
        ).setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).build()
        output = File(context.cacheDir, "oj-nexus-snapshot-test-${System.nanoTime()}.zip")
    }

    @After
    fun tearDown() {
        database.close()
        output.delete()
        File(context.filesDir, "oj-nexus-pending-restore.db").delete()
        File(context.filesDir, "oj-nexus-pending-restore.db.tmp").delete()
        context.getDatabasePath("oj-nexus.db").delete()
    }

    private suspend fun seedProblem(externalId: String, title: String, difficulty: Int) {
        database.problemDao().insert(
            ProblemEntity(
                judge = "codeforces",
                externalId = externalId,
                title = title,
                difficulty = difficulty,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
    }

    @Test
    fun `snapshot archive carries exactly the manifest and database entries`() = runBlocking {
        seedProblem("1A", "Theatre Square", 800)
        val repository = BackupRepository(database, context)

        assertTrue(repository.exportSnapshot(context.contentResolver, Uri.fromFile(output)))

        ZipFile(output).use { zip ->
            assertEquals(
                listOf(SnapshotManifestCodec.MANIFEST_ENTRY, SnapshotManifestCodec.DATABASE_ENTRY),
                zip.entries().toList().map { it.name },
            )
            val manifest = SnapshotManifestCodec.decode(
                zip.getInputStream(zip.getEntry(SnapshotManifestCodec.MANIFEST_ENTRY))
                    .readBytes().toString(Charsets.UTF_8),
            )
            assertTrue(manifest != null)
            assertEquals(OjNexusDatabase.CURRENT_SCHEMA_VERSION, manifest?.schemaVersion)
            assertTrue(manifest?.dataGeneration?.isNotBlank() == true)
            assertEquals(1, manifest?.rowsFor("problems"))
        }
    }

    @Test
    fun `snapshot archive contains no keystore or secret material`() = runBlocking {
        seedProblem("4A", "Watermelon", 800)
        val repository = BackupRepository(database, context)

        assertTrue(repository.exportSnapshot(context.contentResolver, Uri.fromFile(output)))

        val databaseEntry = ZipFile(output).use { zip ->
            zip.getInputStream(zip.getEntry(SnapshotManifestCodec.DATABASE_ENTRY)).readBytes()
        }
        val manifestText = ZipFile(output).use { zip ->
            zip.getInputStream(zip.getEntry(SnapshotManifestCodec.MANIFEST_ENTRY))
                .readBytes().toString(Charsets.UTF_8)
        }
        val forbidden = listOf("secret", "token", "cookie", "password", "luogu-openapp")
        forbidden.forEach { marker ->
            assertFalse(
                "archive leaked marker: $marker",
                String(databaseEntry, Charsets.ISO_8859_1).contains(marker, ignoreCase = true),
            )
            assertFalse(
                "manifest leaked marker: $marker",
                manifestText.contains(marker, ignoreCase = true),
            )
        }
    }

    @Test
    fun `round trip export then import then restore matches counts`() = runBlocking {
        seedProblem("1A", "Theatre Square", 800)
        seedProblem("4A", "Watermelon", 800)
        val repository = BackupRepository(database, context)

        assertTrue(repository.exportSnapshot(context.contentResolver, Uri.fromFile(output)))
        assertTrue(repository.importFrom(context.contentResolver, Uri.fromFile(output)) is RestoreOutcome.Staged)

        database.close()
        assertTrue("restore outcome was not applied", BackupRepository.restorePending(context))
        val restored = android.database.sqlite.SQLiteDatabase.openDatabase(
            context.getDatabasePath(OjNexusDatabase.DATABASE_NAME).path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        )
        restored.rawQuery("SELECT COUNT(*) FROM problems", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        restored.close()
    }

    @Test
    fun `import rejects a snapshot with a mismatched schema version`() = runBlocking {
        seedProblem("1A", "Theatre Square", 800)
        val repository = BackupRepository(database, context)
        assertTrue(repository.exportSnapshot(context.contentResolver, Uri.fromFile(output)))
        tamperManifest { text ->
            text.replace("\"schema_version\":${OjNexusDatabase.CURRENT_SCHEMA_VERSION}", "\"schema_version\":1")
        }

        assertTrue(repository.importFrom(context.contentResolver, Uri.fromFile(output)) is RestoreOutcome.Rejected)
        assertFalse(File(context.filesDir, "oj-nexus-pending-restore.db").exists())
    }

    @Test
    fun `import rejects a snapshot with a blank generation stamp`() = runBlocking {
        seedProblem("1A", "Theatre Square", 800)
        val repository = BackupRepository(database, context)
        assertTrue(repository.exportSnapshot(context.contentResolver, Uri.fromFile(output)))
        tamperManifest { text ->
            text.replace(Regex("\"data_generation\":\"[^\"]*\""), "\"data_generation\":\"\"")
        }

        assertTrue(repository.importFrom(context.contentResolver, Uri.fromFile(output)) is RestoreOutcome.Rejected)
    }

    @Test
    fun `import rejects a snapshot missing the database entry`() = runBlocking {
        seedProblem("1A", "Theatre Square", 800)
        val repository = BackupRepository(database, context)
        assertTrue(repository.exportSnapshot(context.contentResolver, Uri.fromFile(output)))

        val manifestOnly = File(context.cacheDir, "manifest-only-${System.nanoTime()}.zip")
        ZipFile(output).use { source ->
            java.util.zip.ZipOutputStream(manifestOnly.outputStream()).use { target ->
                target.putNextEntry(java.util.zip.ZipEntry(SnapshotManifestCodec.MANIFEST_ENTRY))
                target.write(
                    source.getInputStream(source.getEntry(SnapshotManifestCodec.MANIFEST_ENTRY))
                        .readBytes(),
                )
                target.closeEntry()
            }
        }

        val result = repository.importFrom(context.contentResolver, Uri.fromFile(manifestOnly))
        manifestOnly.delete()
        // Falls back to the legacy raw path, which rejects the non-database payload.
        assertTrue(result is RestoreOutcome.Rejected)
    }

    @Test
    fun `legacy raw database import still works`() = runBlocking {
        seedProblem("4A", "Watermelon", 800)
        val repository = BackupRepository(database, context)
        val legacy = File(context.cacheDir, "legacy-${System.nanoTime()}.db")
        assertTrue(repository.exportTo(context.contentResolver, Uri.fromFile(legacy)))

        val imported = repository.importFrom(context.contentResolver, Uri.fromFile(legacy))
        legacy.delete()
        assertTrue(imported is RestoreOutcome.Staged)
    }

    @Test
    fun `inspect preview reports counts and stages nothing`() = runBlocking {
        seedProblem("1A", "Theatre Square", 800)
        seedProblem("4A", "Watermelon", 800)
        val repository = BackupRepository(database, context)
        assertTrue(repository.exportSnapshot(context.contentResolver, Uri.fromFile(output)))
        // Grow the live database after the export so the report must show a real delta.
        seedProblem("5A", "Small as Few", 800)

        val report = repository.inspectSnapshot(context.contentResolver, Uri.fromFile(output))

        assertTrue(report != null)
        assertEquals(OjNexusDatabase.CURRENT_SCHEMA_VERSION, report?.schemaVersion)
        assertTrue(report?.dataGeneration?.isNotBlank() == true)
        val problemsRow = report?.rows?.firstOrNull { it.table == "problems" }
        assertEquals(2, problemsRow?.incoming)
        assertEquals(3, problemsRow?.current)

        // DoD: the preview must not stage anything — no pending-restore file, no journal,
        // and the live database still holds the pre-import row count.
        assertFalse(File(context.filesDir, "oj-nexus-pending-restore.db").exists())
        assertFalse(File(context.filesDir, "oj-nexus-pending-restore.db.tmp").exists())
        assertFalse(File(context.filesDir, "oj-nexus-restore.journal").exists())
        assertEquals(3, database.problemDao().count())
    }

    @Test
    fun `inspect preview rejects an archive with a mismatched schema`() = runBlocking {
        seedProblem("1A", "Theatre Square", 800)
        val repository = BackupRepository(database, context)
        assertTrue(repository.exportSnapshot(context.contentResolver, Uri.fromFile(output)))
        tamperManifest { text ->
            text.replace("\"schema_version\":${OjNexusDatabase.CURRENT_SCHEMA_VERSION}", "\"schema_version\":1")
        }

        assertNull(repository.inspectSnapshot(context.contentResolver, Uri.fromFile(output)))
        assertFalse(File(context.filesDir, "oj-nexus-pending-restore.db").exists())
    }

    @Test
    fun `inspect preview rejects a non-zip payload`() = runBlocking {
        val repository = BackupRepository(database, context)
        val legacy = File(context.cacheDir, "legacy-inspect-${System.nanoTime()}.db")
        assertTrue(repository.exportTo(context.contentResolver, Uri.fromFile(legacy)))

        assertNull(repository.inspectSnapshot(context.contentResolver, Uri.fromFile(legacy)))
        legacy.delete()
        assertFalse(File(context.filesDir, "oj-nexus-pending-restore.db").exists())
    }

    private fun tamperManifest(transform: (String) -> String) {
        val tampered = File(context.cacheDir, "tampered-${System.nanoTime()}.zip")
        ZipFile(output).use { source ->
            java.util.zip.ZipOutputStream(tampered.outputStream()).use { target ->
                target.putNextEntry(java.util.zip.ZipEntry(SnapshotManifestCodec.MANIFEST_ENTRY))
                val original = source.getInputStream(
                    source.getEntry(SnapshotManifestCodec.MANIFEST_ENTRY),
                ).readBytes().toString(Charsets.UTF_8)
                target.write(transform(original).toByteArray(Charsets.UTF_8))
                target.closeEntry()
                target.putNextEntry(java.util.zip.ZipEntry(SnapshotManifestCodec.DATABASE_ENTRY))
                source.getInputStream(source.getEntry(SnapshotManifestCodec.DATABASE_ENTRY))
                    .copyTo(target)
                target.closeEntry()
            }
        }
        output.delete()
        assertTrue(tampered.renameTo(output))
    }
}
