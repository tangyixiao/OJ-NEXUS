package com.ojnexus.core.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.selectTrainingTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainingTargetPreferencesTest {
    @Test
    fun `targets round trip per judge and fall back to default`() = runBlocking {
        val repository = UserPreferencesRepository(
            ApplicationProvider.getApplicationContext<Context>(),
        )
        repository.clearTrainingTarget(null)
        repository.clearTrainingTarget(JudgeId.ATCODER)

        assertNull(repository.trainingTargets.first().firstOrNull())
        assertTrue(repository.setTrainingTarget(null, center = 1600, tolerance = 200))
        assertTrue(repository.setTrainingTarget(JudgeId.ATCODER, center = 2100, tolerance = 100))

        val targets = repository.trainingTargets.first()
        assertEquals(1600, selectTrainingTarget(targets, JudgeId.CODEFORCES)?.center)
        assertEquals(2100, selectTrainingTarget(targets, JudgeId.ATCODER)?.center)
        assertEquals(100, targets.single { it.judge == JudgeId.ATCODER }.tolerance)
    }

    @Test
    fun `clearing and invalid tolerance never leave a target`() = runBlocking {
        val repository = UserPreferencesRepository(
            ApplicationProvider.getApplicationContext<Context>(),
        )
        repository.clearTrainingTarget(JudgeId.LUOGU)

        assertFalse(repository.setTrainingTarget(JudgeId.LUOGU, center = 1800, tolerance = 0))
        assertNull(repository.trainingTargets.first().firstOrNull { it.judge == JudgeId.LUOGU })

        assertTrue(repository.setTrainingTarget(JudgeId.LUOGU, center = null, tolerance = 200))
        assertNull(repository.trainingTargets.first().firstOrNull { it.judge == JudgeId.LUOGU })
    }
}
