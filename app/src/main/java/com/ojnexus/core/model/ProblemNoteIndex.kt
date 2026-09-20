package com.ojnexus.core.model

/**
 * Which note field a search or a row preview is scoped to.
 * [ALL] reads every field; a preview still resolves to the first non-empty one in reading order.
 */
enum class NoteField {
    ALL,
    KEY_INSIGHT,
    IMPLEMENTATION,
    COMPLEXITY,
    GENERAL,
}

/** True when at least one note field carries non-blank text. */
fun ProblemNotes.hasContent(): Boolean =
    keyInsight.isNotBlank() ||
        implementationNotes.isNotBlank() ||
        complexity.isNotBlank() ||
        general.isNotBlank()

/**
 * The note text a row shows for [field]. Blank input degrades to null instead of an empty
 * string, so callers can fall back to a localized placeholder.
 */
fun ProblemNotes.textFor(field: NoteField): String? {
    val raw = when (field) {
        NoteField.ALL -> listOf(keyInsight, implementationNotes, complexity, general)
            .firstOrNull { it.isNotBlank() }
        NoteField.KEY_INSIGHT -> keyInsight
        NoteField.IMPLEMENTATION -> implementationNotes
        NoteField.COMPLEXITY -> complexity
        NoteField.GENERAL -> general
    }
    return raw?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Note texts a query may match for [field]. [NoteField.ALL] covers every field, so a term that
 * only appears in the complexity line still finds the problem.
 */
fun ProblemNotes.searchScope(field: NoteField): List<String> {
    val raw = when (field) {
        NoteField.ALL -> listOf(keyInsight, implementationNotes, complexity, general)
        NoteField.KEY_INSIGHT -> listOf(keyInsight)
        NoteField.IMPLEMENTATION -> listOf(implementationNotes)
        NoteField.COMPLEXITY -> listOf(complexity)
        NoteField.GENERAL -> listOf(general)
    }
    return raw.filter { it.isNotBlank() }
}

/**
 * One indexed note row: locally saved notes plus the problem identity they belong to.
 * Built from Room only — never from the network, and never fabricated.
 */
data class ProblemNoteEntry(
    val problemId: Long,
    val key: ProblemKey,
    val title: String,
    val difficulty: Int?,
    val attemptCount: Int,
    val solved: Boolean,
    val inReview: Boolean,
    val notes: ProblemNotes,
) {
    val status: ProblemStatus
        get() = ProblemStatus.of(solved, attemptCount, inReview)
}
