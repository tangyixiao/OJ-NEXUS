package com.ojnexus.feature.training

import com.ojnexus.core.model.SessionProblem
import com.ojnexus.core.model.TrainingSession

/** Ephemeral command-loop projection for an active training session. */
data class SessionMomentumState(
    val now: SessionProblem?,
    val next: SessionProblem?,
    val pendingCount: Int,
    val isComplete: Boolean,
    val selectedProblemId: Long?,
    val remainingTargetMs: Long?,
)

fun deriveSessionMomentum(
    session: TrainingSession?,
    problems: List<SessionProblem>,
    elapsedMs: Long,
    selectedProblemId: Long?,
): SessionMomentumState {
    val normalizedSelection = selectedProblemId?.takeIf { selected ->
        problems.any { it.problemId == selected }
    }
    val pending = problems.filterNot(SessionProblem::solved)
    val targetMs = session?.targetDurationMin?.toLong()?.times(60_000L)

    return SessionMomentumState(
        now = problems.firstOrNull { it.problemId == normalizedSelection },
        next = pending.firstOrNull(),
        pendingCount = pending.size,
        isComplete = problems.isNotEmpty() && pending.isEmpty(),
        selectedProblemId = normalizedSelection,
        remainingTargetMs = targetMs?.let { (it - elapsedMs).coerceAtLeast(0L) },
    )
}
