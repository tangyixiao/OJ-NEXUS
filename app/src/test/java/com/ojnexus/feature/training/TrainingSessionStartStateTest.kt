package com.ojnexus.feature.training

import com.ojnexus.core.data.DataError
import com.ojnexus.core.data.DataResult
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingSessionStartStateTest {

    @Test
    fun `starting session cannot dismiss its dialog`() {
        assertEquals(false, canDismissSessionDialog(TrainingSessionStartState.Starting))
        assertEquals(true, canDismissSessionDialog(TrainingSessionStartState.Idle))
        assertEquals(true, canDismissSessionDialog(TrainingSessionStartState.Failed("FAILED")))
    }

    @Test
    fun `successful creation produces a started state`() {
        assertEquals(
            TrainingSessionStartState.Started,
            trainingSessionStartState(
                result = DataResult.Success(42L),
                genericError = "LOAD FAILED",
                activeSessionError = "ACTIVE",
            ),
        )
    }

    @Test
    fun `active session failure uses its localized message`() {
        assertEquals(
            TrainingSessionStartState.Failed("ACTIVE"),
            trainingSessionStartState(
                result = DataResult.Failure(DataError.Storage("A session is already active")),
                genericError = "LOAD FAILED",
                activeSessionError = "ACTIVE",
            ),
        )
    }

    @Test
    fun `other failures use the generic localized message`() {
        assertEquals(
            TrainingSessionStartState.Failed("LOAD FAILED"),
            trainingSessionStartState(
                result = DataResult.Failure(DataError.NotFound("problem 7")),
                genericError = "LOAD FAILED",
                activeSessionError = "ACTIVE",
            ),
        )
    }
}
