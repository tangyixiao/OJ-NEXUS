package com.ojnexus.feature.contests

import com.ojnexus.core.domain.ContestTimeState
import com.ojnexus.core.model.JudgeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContestCommandCenterTest {
    private fun row(
        id: String,
        judge: JudgeId,
        start: Long,
        phase: ContestTimeState,
        countdown: Long = 0,
    ) = ContestRow(
        contestId = id,
        name = "Contest $id",
        judge = judge,
        startTimeSeconds = start,
        durationSeconds = 60,
        phase = phase,
        countdownSeconds = countdown,
        rawPhase = phase.name,
    )

    @Test
    fun summaryCountsGroupsAndSelectsEarliestUpcoming() {
        val live = row("live", JudgeId.CODEFORCES, 300, ContestTimeState.LIVE)
        val first = row("first", JudgeId.ATCODER, 100, ContestTimeState.UPCOMING, countdown = 50)
        val later = row("later", JudgeId.LUOGU, 200, ContestTimeState.UPCOMING, countdown = 150)
        val recent = row("recent", JudgeId.CODEFORCES, 50, ContestTimeState.ENDED)

        val summary = summarizeContestCenter(
            ContestCenterUiState(
                upcoming = listOf(later, first),
                live = listOf(live),
                recent = listOf(recent),
            ),
        )

        assertEquals(1, summary.live)
        assertEquals(2, summary.upcoming)
        assertEquals(1, summary.recent)
        assertEquals(4, summary.total)
        assertEquals(first, summary.nextUpcoming)
    }

    @Test
    fun summaryBreaksUpcomingTiesByJudgeThenContestId() {
        val codeforcesLaterId = row("z", JudgeId.CODEFORCES, 100, ContestTimeState.UPCOMING)
        val atcoder = row("a", JudgeId.ATCODER, 100, ContestTimeState.UPCOMING)
        val codeforcesEarlierId = row("a", JudgeId.CODEFORCES, 100, ContestTimeState.UPCOMING)

        val summary = summarizeContestCenter(
            ContestCenterUiState(
                upcoming = listOf(codeforcesLaterId, atcoder, codeforcesEarlierId),
                live = emptyList(),
                recent = emptyList(),
            ),
        )

        assertEquals(codeforcesEarlierId, summary.nextUpcoming)
    }

    @Test
    fun summaryHasNoNextContestWhenUpcomingIsEmpty() {
        val summary = summarizeContestCenter(
            ContestCenterUiState(upcoming = emptyList(), live = emptyList(), recent = emptyList()),
        )

        assertEquals(0, summary.total)
        assertNull(summary.nextUpcoming)
    }

    @Test
    fun phaseFiltersKeepOnlyTheSelectedGroup() {
        val live = row("live", JudgeId.CODEFORCES, 300, ContestTimeState.LIVE)
        val upcoming = row("upcoming", JudgeId.ATCODER, 100, ContestTimeState.UPCOMING)
        val recent = row("recent", JudgeId.LUOGU, 50, ContestTimeState.ENDED)
        val source = ContestCenterUiState(
            upcoming = listOf(upcoming),
            live = listOf(live),
            recent = listOf(recent),
        )

        val all = filterContestCenter(source, ContestPhaseFilter.ALL)
        val liveOnly = filterContestCenter(source, ContestPhaseFilter.LIVE)
        val upcomingOnly = filterContestCenter(source, ContestPhaseFilter.UPCOMING)
        val recentOnly = filterContestCenter(source, ContestPhaseFilter.RECENT)

        assertEquals(source, all)
        assertEquals(listOf(live), liveOnly.live)
        assertTrue(liveOnly.upcoming.isEmpty())
        assertTrue(liveOnly.recent.isEmpty())
        assertEquals(listOf(upcoming), upcomingOnly.upcoming)
        assertTrue(upcomingOnly.live.isEmpty())
        assertTrue(upcomingOnly.recent.isEmpty())
        assertEquals(listOf(recent), recentOnly.recent)
        assertTrue(recentOnly.live.isEmpty())
        assertTrue(recentOnly.upcoming.isEmpty())
        assertEquals(listOf(live), source.live)
        assertEquals(listOf(upcoming), source.upcoming)
        assertEquals(listOf(recent), source.recent)
        assertNotSame(source, liveOnly)
    }
}
