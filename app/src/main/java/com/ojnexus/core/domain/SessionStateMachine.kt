package com.ojnexus.core.domain

import com.ojnexus.core.model.SessionEvent
import com.ojnexus.core.model.SessionState

/**
 * Central, testable session state machine. Every state change must go through
 * [transition] — composables never decide transitions inline.
 *
 * Allowed:
 *   PLANNED → RUNNING | CANCELLED
 *   RUNNING → PAUSED | FINISHED | CANCELLED
 *   PAUSED  → RUNNING | FINISHED | CANCELLED
 * Terminal: FINISHED, CANCELLED (no further transitions).
 */
object SessionStateMachine {

    fun transition(current: SessionState, event: SessionEvent): SessionState =
        when (current) {
            SessionState.PLANNED -> when (event) {
                SessionEvent.START -> SessionState.RUNNING
                SessionEvent.CANCEL -> SessionState.CANCELLED
                else -> illegal(current, event)
            }

            SessionState.RUNNING -> when (event) {
                SessionEvent.PAUSE -> SessionState.PAUSED
                SessionEvent.FINISH -> SessionState.FINISHED
                SessionEvent.CANCEL -> SessionState.CANCELLED
                else -> illegal(current, event)
            }

            SessionState.PAUSED -> when (event) {
                SessionEvent.RESUME -> SessionState.RUNNING
                SessionEvent.FINISH -> SessionState.FINISHED
                SessionEvent.CANCEL -> SessionState.CANCELLED
                else -> illegal(current, event)
            }

            SessionState.FINISHED -> illegal(current, event)
            SessionState.CANCELLED -> illegal(current, event)
        }

    fun isTerminal(state: SessionState): Boolean =
        state == SessionState.FINISHED || state == SessionState.CANCELLED

    fun isLive(state: SessionState): Boolean =
        state == SessionState.RUNNING || state == SessionState.PAUSED

    private fun illegal(current: SessionState, event: SessionEvent): Nothing =
        throw IllegalStateException("Illegal session transition: $current + $event")
}

/**
 * Elapsed active-time computation. Uses wall-clock snapshots (all persisted), so a session
 * survives backgrounding, screen rotation and process recreation — elapsed time is always
 * derived, never accumulated by ticking.
 */
object SessionClock {

    /**
     * @param startedAt UTC epoch millis of session start.
     * @param totalPausedMs pause duration accumulated before the current pause.
     * @param pausedAt UTC epoch millis of the current pause start, null when not paused
     *   (finished sessions always have this cleared).
     * @param finishedAt UTC epoch millis of session end, null while live.
     * @param now UTC epoch millis reference point.
     */
    fun elapsedMs(
        startedAt: Long,
        totalPausedMs: Long,
        pausedAt: Long?,
        finishedAt: Long?,
        now: Long,
    ): Long {
        val elapsed = when {
            finishedAt != null -> finishedAt - startedAt - totalPausedMs
            pausedAt != null -> pausedAt - startedAt - totalPausedMs
            else -> now - startedAt - totalPausedMs
        }
        return elapsed.coerceAtLeast(0L)
    }
}
