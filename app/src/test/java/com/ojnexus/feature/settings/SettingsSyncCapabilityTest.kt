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

    @Test
    fun `sync phase label exposes queued and terminal phases`() {
        assertEquals("QUEUED", syncPhaseLabel(SyncStateEntity(judge = "luogu", state = SyncPhase.QUEUED.name)))
        assertEquals("SYNCING", syncPhaseLabel(SyncStateEntity(judge = "luogu", state = SyncPhase.SYNCING.name)))
        assertEquals("SUCCESS", syncPhaseLabel(SyncStateEntity(judge = "luogu", state = SyncPhase.SUCCESS.name)))
    }

    @Test
    fun `sync error mapping keeps technical messages out of the UI`() {
        assertEquals("sync_error_rate_limited", syncErrorLabelKey("RateLimited"))
        assertEquals("sync_error_network", syncErrorLabelKey("Timeout"))
        assertEquals("sync_error_network", syncErrorLabelKey("NetworkUnavailable"))
        assertEquals("sync_error_user_not_found", syncErrorLabelKey("UserNotFound"))
        assertEquals("sync_error_api", syncErrorLabelKey("UnexpectedPayload"))
        assertEquals("sync_error_api", syncErrorLabelKey(null))
    }
}
