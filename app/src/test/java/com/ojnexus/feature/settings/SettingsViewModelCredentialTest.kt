package com.ojnexus.feature.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.data.preferences.UserPreferencesRepository
import com.ojnexus.core.data.repository.BackupRepository
import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.repository.JudgeDataRepository
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.judge.JudgeRegistry
import com.ojnexus.judge.luogu.open.OpenAppCredential
import com.ojnexus.judge.luogu.open.OpenAppCredentialStore
import java.time.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelCredentialTest {
    private lateinit var database: OjNexusDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OjNexusDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `blank credential is rejected before storage`() {
        val store = RecordingCredentialStore()
        val viewModel = viewModel(store)

        viewModel.saveOpenAppCredential("  ", "secret")

        assertEquals(OpenAppCredentialInputError.USER_REQUIRED, viewModel.openApp.value.inputError)
        assertFalse(viewModel.openApp.value.error)
        assertEquals(0, store.writeCount)
    }

    private fun viewModel(store: RecordingCredentialStore) = SettingsViewModel(
        accountRepository = JudgeAccountRepository(database, JudgeRegistry(emptyList()), Clock.systemUTC()),
        dataRepository = JudgeDataRepository(database),
        registry = JudgeRegistry(emptyList()),
        backupRepository = BackupRepository(
            database = database,
            context = ApplicationProvider.getApplicationContext(),
        ),
        preferencesRepository = UserPreferencesRepository(ApplicationProvider.getApplicationContext()),
        openAppCredentialStore = store,
        openAppQuotaReader = null,
    )

    private class RecordingCredentialStore : OpenAppCredentialStore {
        var writeCount = 0

        override suspend fun read(): OpenAppCredential? = null

        override suspend fun write(value: OpenAppCredential) {
            writeCount++
        }

        override suspend fun clear() = Unit
    }
}
