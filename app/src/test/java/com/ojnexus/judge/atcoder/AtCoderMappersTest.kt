package com.ojnexus.judge.atcoder

import com.ojnexus.core.domain.ContestTimeState
import com.ojnexus.core.domain.ContestTimeStateCalculator
import com.ojnexus.core.model.DifficultySource
import com.ojnexus.core.model.Verdict
import com.ojnexus.judge.DataSourceReliability
import com.ojnexus.judge.JudgeCapability
import com.ojnexus.judge.atcoder.api.dto.AtCoderContestDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderMergedProblemDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderProblemModelDto
import com.ojnexus.judge.atcoder.mapper.AtCoderMappers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AtCoderMappersTest {

    @Test
    fun `adapter declares only capabilities backed by phase 3 sources`() {
        val adapter = object : AtCoderAdapter {
            override suspend fun fetchSubmissions(handle: String, fromSecond: Long) = emptyList<com.ojnexus.judge.atcoder.api.dto.AtCoderSubmissionDto>()
            override suspend fun fetchContests() = emptyList<AtCoderContestDto>()
            override suspend fun fetchMergedProblems() = emptyList<AtCoderMergedProblemDto>()
            override suspend fun fetchProblemModels() = emptyMap<String, AtCoderProblemModelDto>()
        }

        assertEquals(DataSourceReliability.COMMUNITY, adapter.reliability)
        assertTrue(JudgeCapability.SUBMISSIONS in adapter.capabilities)
        assertTrue(JudgeCapability.PROBLEM_DIFFICULTY in adapter.capabilities)
        assertFalse(JudgeCapability.PROFILE in adapter.capabilities)
        assertFalse(JudgeCapability.RATING_HISTORY in adapter.capabilities)
    }

    @Test
    fun `AtCoder verdicts map safely and preserve unknown through caller`() {
        val expected = mapOf(
            "AC" to Verdict.AC,
            "WA" to Verdict.WA,
            "TLE" to Verdict.TLE,
            "MLE" to Verdict.MLE,
            "RE" to Verdict.RE,
            "CE" to Verdict.CE,
            "WJ" to Verdict.OTHER,
            "IE" to Verdict.OTHER,
            "FUTURE" to Verdict.OTHER,
        )
        expected.forEach { (raw, verdict) -> assertEquals(verdict, AtCoderMappers.verdict(raw)) }
    }

    @Test
    fun `difficulty uses AtCoder display curve and remains estimated`() {
        assertEquals(400, AtCoderMappers.displayDifficulty(400.0))
        assertEquals(1200, AtCoderMappers.displayDifficulty(1199.6))
        assertEquals(147, AtCoderMappers.displayDifficulty(0.0))
        assertEquals(54, AtCoderMappers.displayDifficulty(-400.0))
        assertEquals(5000, AtCoderMappers.displayDifficulty(5000.0))
        assertNull(AtCoderMappers.displayDifficulty(Double.NaN))

        val problem = AtCoderMergedProblemDto(
            id = "abc350_a",
            contestId = "abc350",
            problemIndex = "A",
            name = "Past ABCs",
            solverCount = 10_000,
            point = 100.0,
        )
        val entity = AtCoderMappers.toRemoteProblem(
            problem,
            AtCoderProblemModelDto(difficulty = 800.2, isExperimental = false),
            contest = AtCoderContestDto(1, "0-1999", "abc350", 6_000, "AtCoder Beginner Contest 350"),
            now = 10,
        )
        assertEquals(800, entity.rating)
        assertEquals(DifficultySource.ESTIMATED.name, entity.difficultySource)
        assertEquals("abc350", entity.contestId)
    }

    @Test
    fun `heuristic and marathon problem difficulty remains unknown`() {
        val problem = AtCoderMergedProblemDto(
            id = "ahc001_a",
            contestId = "ahc001",
            problemIndex = "A",
            name = "AtCoder Heuristic Contest",
        )
        val entity = AtCoderMappers.toRemoteProblem(
            problem,
            AtCoderProblemModelDto(difficulty = 1800.0, isExperimental = false),
            contest = AtCoderContestDto(1, "-", "ahc001", 14_400, "AtCoder Heuristic Contest 001"),
            now = 10,
        )
        assertNull(entity.rating)
        assertEquals(DifficultySource.UNKNOWN.name, entity.difficultySource)
    }

    @Test
    fun `contest time calculator handles exact boundaries`() {
        assertEquals(ContestTimeState.UPCOMING, ContestTimeStateCalculator.calculate(1_000, 100, 999))
        assertEquals(ContestTimeState.LIVE, ContestTimeStateCalculator.calculate(1_000, 100, 1_000))
        assertEquals(ContestTimeState.LIVE, ContestTimeStateCalculator.calculate(1_000, 100, 1_099))
        assertEquals(ContestTimeState.ENDED, ContestTimeStateCalculator.calculate(1_000, 100, 1_100))
    }

    @Test
    fun `official URLs encode path segments and never use WebView routes`() {
        assertEquals("https://atcoder.jp/contests/abc350", AtCoderUrls.contest("abc350"))
        assertEquals(
            "https://atcoder.jp/contests/abc350/tasks/abc350_a",
            AtCoderUrls.problem("abc350", "abc350_a"),
        )
        assertEquals("https://atcoder.jp/users/Case%20Name", AtCoderUrls.user("Case Name"))
    }
}
