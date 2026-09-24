package com.ojnexus.feature.contests

enum class ContestPhaseFilter {
    ALL,
    LIVE,
    UPCOMING,
    RECENT,
}

data class ContestCenterSummary(
    val live: Int,
    val upcoming: Int,
    val recent: Int,
    val total: Int,
    val nextUpcoming: ContestRow?,
)

fun summarizeContestCenter(state: ContestCenterUiState): ContestCenterSummary {
    val next = state.upcoming.minWithOrNull(
        compareBy<ContestRow> { it.startTimeSeconds ?: Long.MAX_VALUE }
            .thenBy { it.judge.ordinal }
            .thenBy { it.contestId },
    )
    return ContestCenterSummary(
        live = state.live.size,
        upcoming = state.upcoming.size,
        recent = state.recent.size,
        total = state.live.size + state.upcoming.size + state.recent.size,
        nextUpcoming = next,
    )
}

fun filterContestCenter(
    state: ContestCenterUiState,
    filter: ContestPhaseFilter,
): ContestCenterUiState = when (filter) {
    ContestPhaseFilter.ALL -> state.copy()
    ContestPhaseFilter.LIVE -> ContestCenterUiState(
        live = state.live,
        upcoming = emptyList(),
        recent = emptyList(),
    )
    ContestPhaseFilter.UPCOMING -> ContestCenterUiState(
        live = emptyList(),
        upcoming = state.upcoming,
        recent = emptyList(),
    )
    ContestPhaseFilter.RECENT -> ContestCenterUiState(
        live = emptyList(),
        upcoming = emptyList(),
        recent = state.recent,
    )
}
