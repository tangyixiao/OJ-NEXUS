package com.ojnexus.feature.problems

import com.ojnexus.core.model.Problem
import com.ojnexus.core.model.ProblemStatus

data class ProblemLibrarySummary(
    val total: Int,
    val visible: Int,
    val solved: Int,
    val review: Int,
    val favorites: Int,
)

fun summarizeProblemLibrary(
    problems: List<Problem>,
    visibleProblems: List<Problem>,
): ProblemLibrarySummary = ProblemLibrarySummary(
    total = problems.size,
    visible = visibleProblems.size,
    solved = problems.count { it.solved },
    review = problems.count { it.status == ProblemStatus.REVIEW },
    favorites = problems.count { it.favorite },
)

fun isProblemLibraryDefaultView(filter: ProblemFilter, sort: ProblemSort): Boolean =
    filter.isDefault && sort == ProblemSort.UPDATED
