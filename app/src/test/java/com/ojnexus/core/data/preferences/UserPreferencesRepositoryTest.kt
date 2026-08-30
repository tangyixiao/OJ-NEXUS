package com.ojnexus.core.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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

        val stored = repository.preferences.first()
        assertTrue(stored.reduceMotion)
        assertFalse(stored.hapticsEnabled)

        repository.setReduceMotion(false)
        repository.setHapticsEnabled(true)
    }
}
