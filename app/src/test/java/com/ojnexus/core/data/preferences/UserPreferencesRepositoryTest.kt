package com.ojnexus.core.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.designsystem.NexusThemeSlot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserPreferencesRepositoryTest {
    @Test
    fun `interaction preferences persist through datastore`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = UserPreferencesRepository(context)

        repository.setReduceMotion(true)
        repository.setHapticsEnabled(false)
        repository.setThemeSlot(NexusThemeSlot.TERMINAL_GREEN)

        val stored = repository.preferences.first()
        assertTrue(stored.reduceMotion)
        assertFalse(stored.hapticsEnabled)
        org.junit.Assert.assertEquals(NexusThemeSlot.TERMINAL_GREEN, stored.themeSlot)

        repository.setReduceMotion(false)
        repository.setHapticsEnabled(true)
        repository.setThemeSlot(NexusThemeSlot.NEXUS_BLUE)
    }
}
