package com.ojnexus.feature.training

import com.ojnexus.core.model.SessionProblem

enum class SessionDebriefLane {
    SOLVED,
    ATTENTION,
    PENDING,
}

data class SessionDebriefPulse(
    val solved: Int,
    val attention: Int,
    val pending: Int,
)

fun SessionProblem.debriefLane(): SessionDebriefLane = when {
    solved -> SessionDebriefLane.SOLVED
    attempts > 0 -> SessionDebriefLane.ATTENTION
    else -> SessionDebriefLane.PENDING
}

fun deriveSessionDebriefPulse(problems: List<SessionProblem>): SessionDebriefPulse =
    SessionDebriefPulse(
        solved = problems.count { it.debriefLane() == SessionDebriefLane.SOLVED },
        attention = problems.count { it.debriefLane() == SessionDebriefLane.ATTENTION },
        pending = problems.count { it.debriefLane() == SessionDebriefLane.PENDING },
    )

fun filterSessionDebrief(
    problems: List<SessionProblem>,
    lane: SessionDebriefLane?,
): List<SessionProblem> = lane?.let { selected ->
    problems.filter { it.debriefLane() == selected }
} ?: problems

/** Problems the terminal debrief can add to the review ladder without changing existing rows. */
fun sessionReviewCandidates(problems: List<SessionProblem>): List<SessionProblem> =
    problems.filter { problem ->
        problem.debriefLane() != SessionDebriefLane.SOLVED && !problem.inReview
    }
