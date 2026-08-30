package com.ojnexus.feature.settings

import com.ojnexus.judge.JudgeCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSyncCapabilityTest {
    @Test
    fun `binding-only judge does not schedule background sync`() {
        assertFalse(shouldScheduleJudgeSync(setOf(JudgeCapability.ACCOUNT_BINDING)))
    }

    @Test
    fun `background-sync capability schedules sync`() {
        assertTrue(shouldScheduleJudgeSync(setOf(JudgeCapability.ACCOUNT_BINDING, JudgeCapability.BACKGROUND_SYNC)))
    }
}
