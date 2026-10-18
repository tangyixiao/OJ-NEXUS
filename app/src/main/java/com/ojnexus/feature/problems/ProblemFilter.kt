package com.ojnexus.feature.problems

import androidx.annotation.StringRes
import com.ojnexus.R
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Problem
import com.ojnexus.core.model.ProblemStatus

/** Sort options for the library. Ordering is applied by [applyFilterSort]. */
enum class ProblemSort(@param:StringRes val labelRes: Int) {
    UPDATED(R.string.problems_sort_updated),
    DIFFICULTY(R.string.problems_sort_difficulty),
    TITLE(R.string.problems_sort_title),
    ATTEMPTS(R.string.problems_sort_attempts),
}

/** Library filter state. All criteria are AND-combined. */
data class ProblemFilter(
    val query: String = "",
    val status: ProblemStatus? = null,
    val judge: JudgeId? = null,
    val favoriteOnly: Boolean = false,
    val tag: String? = null,
    /** Inclusive difficulty range; null bounds are open. Unknown difficulty passes only open bounds. */
    val difficultyMin: Int? = null,
    val difficultyMax: Int? = null,
) {
    val isDefault: Boolean
        get() = this == ProblemFilter()
}

/**
 * Pure, unit-testable library filtering + ordering.
 * Difficulty sorts ascending with unknown ratings last; ties keep the input order.
 */
fun List<Problem>.applyFilterSort(filter: ProblemFilter, sort: ProblemSort): List<Problem> {
    val query = filter.query.trim().lowercase()
    val result = filter { problem ->
        if (query.isNotEmpty() &&
            !problem.title.lowercase().contains(query) &&
            !problem.key.externalId.lowercase().contains(query)
        ) {
            return@filter false
        }
        if (filter.status != null && problem.status != filter.status) return@filter false
        if (filter.judge != null && problem.key.judge != filter.judge) return@filter false
        if (filter.favoriteOnly && !problem.favorite) return@filter false
        if (filter.tag != null && filter.tag !in problem.tags) return@filter false
        val difficulty = problem.difficulty
        if (filter.difficultyMin != null || filter.difficultyMax != null) {
            if (difficulty == null) return@filter false
            if (filter.difficultyMin != null && difficulty < filter.difficultyMin) return@filter false
            if (filter.difficultyMax != null && difficulty > filter.difficultyMax) return@filter false
        }
        true
    }
    return when (sort) {
        ProblemSort.UPDATED -> result.sortedByDescending { it.updatedAt }
        ProblemSort.DIFFICULTY -> result.sortedWith(
            compareBy<Problem> { it.difficulty == null }.thenBy { it.difficulty ?: 0 },
        )
        ProblemSort.TITLE -> result.sortedBy { it.title.lowercase() }
        ProblemSort.ATTEMPTS -> result.sortedByDescending { it.attemptCount }
    }
}
