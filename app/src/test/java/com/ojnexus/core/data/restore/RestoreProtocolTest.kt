package com.ojnexus.core.data.restore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreProtocolTest {
    @Test
    fun `restore stages move through the recoverable lifecycle only`() {
        assertTrue(RestoreStage.STAGED.canTransitionTo(RestoreStage.SWAPPING))
        assertTrue(RestoreStage.STAGED.canTransitionTo(RestoreStage.REJECTED))
        assertTrue(RestoreStage.SWAPPING.canTransitionTo(RestoreStage.APPLIED))
        assertTrue(RestoreStage.SWAPPING.canTransitionTo(RestoreStage.ROLLED_BACK))
        assertFalse(RestoreStage.APPLIED.canTransitionTo(RestoreStage.SWAPPING))
        assertFalse(RestoreStage.REJECTED.canTransitionTo(RestoreStage.APPLIED))
    }

    @Test
    fun `staged journal applies a complete candidate`() {
        assertEquals(
            RestoreRecoveryAction.APPLY_CANDIDATE,
            decideRestoreRecovery(
                RestoreJournal(RestoreStage.STAGED, "g1", null),
                RestoreFileState(target = true, candidate = true, rollback = false, staged = true),
            ),
        )
    }

    @Test
    fun `swapping with rollback and no target restores the old database`() {
        assertEquals(
            RestoreRecoveryAction.RESTORE_ROLLBACK,
            decideRestoreRecovery(
                RestoreJournal(RestoreStage.SWAPPING, "g1", null),
                RestoreFileState(target = false, candidate = false, rollback = true, staged = true),
            ),
        )
    }

    @Test
    fun `swapping with target and rollback validates the replacement before cleanup`() {
        assertEquals(
            RestoreRecoveryAction.VALIDATE_TARGET,
            decideRestoreRecovery(
                RestoreJournal(RestoreStage.SWAPPING, "g1", null),
                RestoreFileState(target = true, candidate = false, rollback = true, staged = true),
            ),
        )
    }

    @Test
    fun `completed journal only clears recovery artifacts`() {
        listOf(RestoreStage.APPLIED, RestoreStage.ROLLED_BACK, RestoreStage.REJECTED).forEach { stage ->
            assertEquals(
                RestoreRecoveryAction.CLEAR_COMPLETED_ARTIFACTS,
                decideRestoreRecovery(
                    RestoreJournal(stage, "g1", null),
                    RestoreFileState(target = true, candidate = true, rollback = true, staged = true),
                ),
            )
        }
    }

    @Test
    fun `missing staged file rejects an incomplete staging journal`() {
        assertEquals(
            RestoreRecoveryAction.REJECT_STAGING,
            decideRestoreRecovery(
                RestoreJournal(RestoreStage.STAGED, "g1", null),
                RestoreFileState(target = true, candidate = false, rollback = false, staged = false),
            ),
        )
    }

    @Test
    fun `failure categories are stable and never expose raw errors`() {
        assertEquals("INVALID_SQLITE", RestoreFailure.INVALID_SQLITE.name)
        assertEquals("SCHEMA_MISMATCH", RestoreFailure.SCHEMA_MISMATCH.name)
        assertEquals("INTEGRITY_FAILURE", RestoreFailure.INTEGRITY_FAILURE.name)
        assertEquals("FILE_REPLACEMENT_FAILURE", RestoreFailure.FILE_REPLACEMENT_FAILURE.name)
        assertEquals("ROLLBACK_FAILURE", RestoreFailure.ROLLBACK_FAILURE.name)
    }
}
