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

/**
 * One debrief row, pre-resolved to display strings. The builder never touches Android so the
 * whole document is a pure function of this payload.
 */
data class SessionDebriefExportRow(
    val judge: String,
    val externalId: String,
    val title: String,
    val difficulty: Int?,
    val attempts: Int,
    val lane: SessionDebriefLane,
    val verdictLabel: String?,
    val reviewLabel: String?,
)

/** Everything the Markdown builder needs about a finished session. */
data class SessionDebriefExport(
    val title: String,
    val typeLabel: String,
    val stateLabel: String,
    val startedAtLabel: String,
    val finishedAtLabel: String?,
    val durationLabel: String?,
    val targetLabel: String?,
    val noteLabel: String?,
    val solvedCount: Int,
    val attemptCount: Int,
    val reviewCount: Int,
    val rows: List<SessionDebriefExportRow>,
)

private const val EMPTY_PROBLEMS_LINE = "NO PROBLEMS LOGGED IN THIS SESSION."

/** Markdown cell/text escaping: a pipe would break the table, a newline would break the line. */
private fun String.markdownSafe(): String =
    replace("\\", "\\\\").replace("|", "\\|").replace("\r\n", " ").replace("\n", " ").replace("\r", " ")

/**
 * Renders a finished session debrief as Markdown. Optional values are omitted rather than
 * rendered empty, and the document ends with exactly one newline.
 */
fun buildSessionDebriefMarkdown(export: SessionDebriefExport): String = buildString {
    appendLine("# ${export.title.markdownSafe()}")
    appendLine("- TYPE: ${export.typeLabel.markdownSafe()}")
    appendLine("- STATE: ${export.stateLabel.markdownSafe()}")
    appendLine("- STARTED: ${export.startedAtLabel.markdownSafe()}")
    export.finishedAtLabel?.let { appendLine("- FINISHED: ${it.markdownSafe()}") }
    export.durationLabel?.let { appendLine("- DURATION: ${it.markdownSafe()}") }
    export.targetLabel?.let { appendLine("- TARGET: ${it.markdownSafe()}") }
    export.noteLabel?.let { appendLine("- NOTE: ${it.markdownSafe()}") }
    appendLine()
    appendLine("## SUMMARY")
    appendLine("- SOLVED: ${export.solvedCount}")
    appendLine("- ATTEMPTS: ${export.attemptCount}")
    appendLine("- IN REVIEW: ${export.reviewCount}")
    appendLine()
    appendLine("## PROBLEMS")
    if (export.rows.isEmpty()) {
        appendLine(EMPTY_PROBLEMS_LINE)
    } else {
        appendLine("| JUDGE | ID | TITLE | RATING | ATTEMPTS | STATUS |")
        appendLine("| --- | --- | --- | --- | --- | --- |")
        export.rows.forEach { row ->
            val rating = row.difficulty?.toString().orEmpty()
            val status = listOfNotNull(
                row.lane.name.markdownSafe(),
                row.verdictLabel?.markdownSafe(),
                row.reviewLabel?.markdownSafe(),
            ).joinToString(" / ")
            appendLine(
                "| ${row.judge.markdownSafe()} | ${row.externalId.markdownSafe()} | " +
                    "${row.title.markdownSafe()} | $rating | ${row.attempts} | $status |",
            )
        }
    }
    // buildString + appendLine already ends with a single '\n'; trim any accidental extras.
}.trimEnd('\n') + "\n"
