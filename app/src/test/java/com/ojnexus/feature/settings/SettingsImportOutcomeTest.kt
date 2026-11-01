package com.ojnexus.feature.settings

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.R
import com.ojnexus.core.data.preferences.UserPreferencesRepository
import com.ojnexus.core.data.repository.BackupRepository
import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.repository.JudgeDataRepository
import com.ojnexus.core.data.restore.RestoreFailure
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.judge.JudgeRegistry
import java.io.File
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Phase 127C: a rejected import surfaces its typed failure in [BackupResult] instead of
 * collapsing to a Boolean, and the result copy maps storage failures to retry guidance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsImportOutcomeTest {
    private lateinit var context: Context
    private lateinit var database: OjNexusDatabase
    private lateinit var databaseName: String
    private lateinit var viewModelStore: ViewModelStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val uniqueId = System.nanoTime()
        databaseName = "settings-import-db-$uniqueId.db"
        viewModelStore = ViewModelStore()
        database = Room.databaseBuilder(
            context,
            OjNexusDatabase::class.java,
            databaseName,
        ).setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).build()
        File(context.filesDir, "oj-nexus-pending-restore.db").delete()
        File(context.filesDir, "oj-nexus-pending-restore.db.tmp").delete()
        File(context.filesDir, "oj-nexus-restore.journal").delete()
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        database.close()
        context.getDatabasePath(databaseName).delete()
        File(context.filesDir, "oj-nexus-pending-restore.db").delete()
        File(context.filesDir, "oj-nexus-pending-restore.db.tmp").delete()
        File(context.filesDir, "oj-nexus-restore.journal").delete()
    }

    private fun viewModel(): SettingsViewModel {
        val viewModel = SettingsViewModel(
            accountRepository = JudgeAccountRepository(database, JudgeRegistry(emptyList()), Clock.systemUTC()),
            dataRepository = JudgeDataRepository(database),
            registry = JudgeRegistry(emptyList()),
            backupRepository = BackupRepository(database, context),
            preferencesRepository = UserPreferencesRepository(context),
            openAppCredentialStore = null,
            openAppQuotaReader = null,
        )
        viewModelStore.put("settings", viewModel)
        return viewModel
    }

    @Test
    fun `rejected import surfaces the typed failure`() {
        val garbage = File(context.filesDir, "settings-import-garbage.db")
        garbage.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        val viewModel = viewModel()
        viewModel.importBackup(context.contentResolver, Uri.fromFile(garbage))
        awaitResult(viewModel)
        val result = viewModel.backup.value
        assertFalse(result?.success == true)
        assertEquals(RestoreFailure.INVALID_SQLITE, result?.failure)
        garbage.delete()
    }

    @Test
    fun `result copy maps storage failures to retry guidance`() {
        assertEquals(
            R.string.settings_backup_success,
            backupResultLabel(BackupResult(BackupOperation.EXPORT, success = true)),
        )
        assertEquals(
            R.string.settings_backup_import_success,
            backupResultLabel(BackupResult(BackupOperation.IMPORT, success = true)),
        )
        assertEquals(
            R.string.settings_import_storage_full,
            backupResultLabel(BackupResult(BackupOperation.IMPORT, success = false, failure = RestoreFailure.STORAGE_FULL)),
        )
        assertEquals(
            R.string.settings_import_storage_io,
            backupResultLabel(BackupResult(BackupOperation.IMPORT, success = false, failure = RestoreFailure.STORAGE_IO)),
        )
        assertEquals(
            R.string.settings_import_rejected,
            backupResultLabel(BackupResult(BackupOperation.IMPORT, success = false, failure = RestoreFailure.INVALID_SQLITE)),
        )
        assertEquals(
            R.string.settings_backup_failed,
            backupResultLabel(BackupResult(BackupOperation.EXPORT, success = false)),
        )
    }

    private fun awaitResult(viewModel: SettingsViewModel) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (viewModel.backup.value == null && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Import result did not arrive", viewModel.backup.value != null)
    }
}
