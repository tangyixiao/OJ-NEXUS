package com.ojnexus.feature.problems

import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.NoteField
import com.ojnexus.core.model.ProblemNoteEntry
import com.ojnexus.core.model.searchScope

/**
 * Note-index filter state. All criteria are AND-combined; the search term is matched against
 * the selected field scope only.
 */
data class ProblemNoteFilter(
    val query: String = "",
    val field: NoteField = NoteField.ALL,
    val judge: JudgeId? = null,
    val unsolvedOnly: Boolean = false,
) {
    val isDefault: Boolean
        get() = this == ProblemNoteFilter()
}

/** Note-index readout values, counted from the local index only. */
data class ProblemNoteIndexSummary(
    val indexed: Int,
    val visible: Int,
    val unsolved: Int,
    val reviewed: Int,
)

fun summarizeNoteIndex(
    entries: List<ProblemNoteEntry>,
    visibleEntries: List<ProblemNoteEntry>,
): ProblemNoteIndexSummary = ProblemNoteIndexSummary(
    indexed = entries.size,
    visible = visibleEntries.size,
    unsolved = entries.count { !it.solved },
    reviewed = entries.count { it.inReview },
)

/**
 * Pure, unit-testable note-index filtering. Input order (newest note first) is preserved, so the
 * list never re-sorts behind the user. A term matches the problem title, its external id, or the
 * note text inside the selected field scope.
 */
fun List<ProblemNoteEntry>.applyNoteFilter(filter: ProblemNoteFilter): List<ProblemNoteEntry> {
    val query = filter.query.trim().lowercase()
    return this.filter { entry ->
        if (filter.judge != null && entry.key.judge != filter.judge) return@filter false
        if (filter.unsolvedOnly && entry.solved) return@filter false
        if (query.isEmpty()) return@filter true
        entry.title.lowercase().contains(query) ||
            entry.key.externalId.lowercase().contains(query) ||
            entry.notes.searchScope(filter.field).any { it.lowercase().contains(query) }
    }
}
