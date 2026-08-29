package com.ojnexus.core.domain

import com.ojnexus.core.model.SessionEvent
import com.ojnexus.core.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateMachineTest {

    @Test
    fun `planned can start or cancel`() {
        assertEquals(SessionState.RUNNING, SessionStateMachine.transition(SessionState.PLANNED, SessionEvent.START))
        assertEquals(SessionState.CANCELLED, SessionStateMachine.transition(SessionState.PLANNED, SessionEvent.CANCEL))
    }

    @Test
    fun `running can pause finish or cancel`() {
        assertEquals(SessionState.PAUSED, SessionStateMachine.transition(SessionState.RUNNING, SessionEvent.PAUSE))
        assertEquals(SessionState.FINISHED, SessionStateMachine.transition(SessionState.RUNNING, SessionEvent.FINISH))
        assertEquals(SessionState.CANCELLED, SessionStateMachine.transition(SessionState.RUNNING, SessionEvent.CANCEL))
    }

    @Test
    fun `paused can resume finish or cancel`() {
        assertEquals(SessionState.RUNNING, SessionStateMachine.transition(SessionState.PAUSED, SessionEvent.RESUME))
        assertEquals(SessionState.FINISHED, SessionStateMachine.transition(SessionState.PAUSED, SessionEvent.FINISH))
        assertEquals(SessionState.CANCELLED, SessionStateMachine.transition(SessionState.PAUSED, SessionEvent.CANCEL))
    }

    @Test
    fun `illegal transitions throw`() {
        assertThrows(IllegalStateException::class.java) {
            SessionStateMachine.transition(SessionState.PLANNED, SessionEvent.PAUSE)
        }
        assertThrows(IllegalStateException::class.java) {
            SessionStateMachine.transition(SessionState.PLANNED, SessionEvent.FINISH)
        }
        assertThrows(IllegalStateException::class.java) {
            SessionStateMachine.transition(SessionState.PLANNED, SessionEvent.RESUME)
        }
        assertThrows(IllegalStateException::class.java) {
            SessionStateMachine.transition(SessionState.RUNNING, SessionEvent.RESUME)
        }
        assertThrows(IllegalStateException::class.java) {
            SessionStateMachine.transition(SessionState.RUNNING, SessionEvent.START)
        }
        assertThrows(IllegalStateException::class.java) {
            SessionStateMachine.transition(SessionState.PAUSED, SessionEvent.PAUSE)
        }
    }

    @Test
    fun `terminal states reject every event`() {
        for (state in listOf(SessionState.FINISHED, SessionState.CANCELLED)) {
            for (event in SessionEvent.entries) {
                assertThrows(IllegalStateException::class.java) {
                    SessionStateMachine.transition(state, event)
                }
            }
        }
        // A finished session can never be resumed again.
        assertFalse(SessionStateMachine.isLive(SessionState.FINISHED))
        assertTrue(SessionStateMachine.isTerminal(SessionState.FINISHED))
        assertTrue(SessionStateMachine.isTerminal(SessionState.CANCELLED))
    }

    @Test
    fun `terminal detection matches the enum`() {
        for (state in SessionState.entries) {
            assertEquals(SessionStateMachine.isTerminal(state), state == SessionState.FINISHED || state == SessionState.CANCELLED)
            assertEquals(SessionStateMachine.isLive(state), state == SessionState.RUNNING || state == SessionState.PAUSED)
        }
    }
}

class SessionClockTest {

    @Test
    fun `running session counts from start minus pauses`() {
        val elapsed = SessionClock.elapsedMs(
            startedAt = 1_000_000L,
            totalPausedMs = 60_000L,
            pausedAt = null,
            finishedAt = null,
            now = 1_000_000L + 10 * 60_000L,
        )
        assertEquals(9 * 60_000L, elapsed)
    }

    @Test
    fun `paused session freezes at the pause point`() {
        val start = 1_000_000L
        val pauseStart = start + 10 * 60_000L
        val elapsedNow = SessionClock.elapsedMs(start, 0, pauseStart, null, now = pauseStart + 5 * 60_000L)
        assertEquals(10 * 60_000L, elapsedNow)
    }

    @Test
    fun `finished session is exact regardless of now`() {
        val start = 1_000_000L
        val end = start + 30 * 60_000L
        val elapsed = SessionClock.elapsedMs(start, 5 * 60_000L, null, end, now = end + 999_999L)
        assertEquals(25 * 60_000L, elapsed)
    }

    @Test
    fun `clock changes between pause and now never produce negative elapsed`() {
        val start = 1_000_000L
        val elapsed = SessionClock.elapsedMs(
            startedAt = start,
            totalPausedMs = 10 * 60_000L,
            pausedAt = null,
            finishedAt = null,
            // now earlier than start + pauses (e.g. wall clock adjustment): clamps to 0.
            now = start + 1_000L,
        )
        assertEquals(0L, elapsed)
    }
}
