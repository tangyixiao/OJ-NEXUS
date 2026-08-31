package com.ojnexus.feature.settings

import com.ojnexus.judge.JudgeCapability
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.data.sync.SyncStage
import com.ojnexus.core.database.entity.SyncStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `sync stage is exposed only while a sync is active`() {
        assertEquals(
            SyncStage.PROBLEMS.name,
            syncStageName(SyncStateEntity(judge = "luogu", state = SyncPhase.SYNCING.name, currentStage = SyncStage.PROBLEMS.name)),
        )
        assertNull(syncStageName(SyncStateEntity(judge = "luogu", state = SyncPhase.PARTIAL.name, currentStage = SyncStage.PROBLEMS.name)))
    }
}
