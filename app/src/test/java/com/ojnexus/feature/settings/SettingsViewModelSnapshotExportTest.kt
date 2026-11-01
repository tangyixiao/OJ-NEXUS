package com.ojnexus.feature.settings

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelStore
import com.ojnexus.core.data.backup.SnapshotManifestCodec
import com.ojnexus.core.data.preferences.UserPreferencesRepository
import com.ojnexus.core.data.repository.BackupRepository
import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.repository.JudgeDataRepository
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.judge.JudgeRegistry
import java.io.File
import java.time.Clock
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsSnapshotVmTest {
    private lateinit var context: Context
    private lateinit var database: OjNexusDatabase
    private lateinit var databaseName: String
    private lateinit var output: File
    private lateinit var viewModelStore: ViewModelStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val uniqueId = System.nanoTime()
        databaseName = "settings-snapshot-db-$uniqueId.db"
        viewModelStore = ViewModelStore()
        database = Room.databaseBuilder(
            context,
            OjNexusDatabase::class.java,
            databaseName,
        ).setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).build()
        output = File(context.cacheDir, "settings-snapshot-$uniqueId.zip")
        File(context.filesDir, "oj-nexus-pending-restore.db").delete()
        File(context.filesDir, "oj-nexus-pending-restore.db.tmp").delete()
        File(context.filesDir, "oj-nexus-restore.journal").delete()
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        database.close()
        output.delete()
        context.getDatabasePath(databaseName).delete()
        File(context.filesDir, "oj-nexus-pending-restore.db").delete()
        File(context.filesDir, "oj-nexus-pending-restore.db.tmp").delete()
        File(context.filesDir, "oj-nexus-restore.journal").delete()
    }

    @Test
    fun `settings export creates a snapshot that previews and cancels safely`() = runBlocking {
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
        val repository = BackupRepository(database, context)
        val viewModel = SettingsViewModel(
            accountRepository = JudgeAccountRepository(database, JudgeRegistry(emptyList()), Clock.systemUTC()),
            dataRepository = JudgeDataRepository(database),
            registry = JudgeRegistry(emptyList()),
            backupRepository = repository,
            preferencesRepository = UserPreferencesRepository(context),
            openAppCredentialStore = null,
            openAppQuotaReader = null,
        )
        viewModelStore.put("settings", viewModel)

        viewModel.exportBackup(context.contentResolver, Uri.fromFile(output))
        awaitExportResult(viewModel)
        assertTrue(viewModel.backup.value?.success == true)
        assertTrue("Settings export must be a ZIP snapshot", output.inputStream().use {
            it.readNBytes(2).contentEquals(byteArrayOf('P'.code.toByte(), 'K'.code.toByte()))
        })

        ZipFile(output).use { zip ->
            assertEquals(
                listOf(SnapshotManifestCodec.MANIFEST_ENTRY, SnapshotManifestCodec.DATABASE_ENTRY),
                zip.entries().toList().map { it.name },
            )
            val manifestText = zip.getInputStream(
                zip.getEntry(SnapshotManifestCodec.MANIFEST_ENTRY),
            ).readBytes().toString(Charsets.UTF_8)
            val manifest = SnapshotManifestCodec.decode(manifestText)
            assertNotNull(manifest)
            assertEquals(OjNexusDatabase.CURRENT_SCHEMA_VERSION, manifest?.schemaVersion)
            assertEquals(1, manifest?.rowsFor("problems"))
            val databaseBytes = zip.getInputStream(
                zip.getEntry(SnapshotManifestCodec.DATABASE_ENTRY),
            ).readBytes()
            val forbidden = listOf("password", "token", "cookie", "secret", "luogu-openapp")
            forbidden.forEach { marker ->
                assertFalse(String(databaseBytes, Charsets.ISO_8859_1).contains(marker, ignoreCase = true))
                assertFalse(manifestText.contains(marker, ignoreCase = true))
            }
        }

        viewModel.inspectImport(context.contentResolver, Uri.fromFile(output))
        awaitMainLooper { viewModel.pendingImportFlow.value != null }
        assertNotNull(viewModel.pendingImportFlow.value)
        viewModel.cancelImport()
        assertEquals(null, viewModel.pendingImportFlow.value)
        assertFalse(File(context.filesDir, "oj-nexus-pending-restore.db").exists())
        assertFalse(File(context.filesDir, "oj-nexus-pending-restore.db.tmp").exists())
        assertFalse(File(context.filesDir, "oj-nexus-restore.journal").exists())
        assertEquals(1, database.problemDao().count())
    }

    private fun awaitExportResult(viewModel: SettingsViewModel) = awaitMainLooper {
        viewModel.backup.value?.operation == BackupOperation.EXPORT
    }

    private fun awaitMainLooper(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("ViewModel operation did not complete", condition())
    }
}
