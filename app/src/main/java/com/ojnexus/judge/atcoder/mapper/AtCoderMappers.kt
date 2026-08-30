package com.ojnexus.judge.atcoder.mapper

import com.ojnexus.core.database.entity.ContestEntity
import com.ojnexus.core.database.entity.RemoteProblemEntity
import com.ojnexus.core.model.DifficultySource
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Verdict
import com.ojnexus.judge.atcoder.api.dto.AtCoderContestDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderMergedProblemDto
import com.ojnexus.judge.atcoder.api.dto.AtCoderProblemModelDto
import kotlin.math.exp
import kotlin.math.roundToInt

object AtCoderMappers {
    fun verdict(raw: String?): Verdict = when (raw) {
        "AC" -> Verdict.AC
        "WA" -> Verdict.WA
        "TLE" -> Verdict.TLE
        "MLE" -> Verdict.MLE
        "RE" -> Verdict.RE
        "CE" -> Verdict.CE
        else -> Verdict.OTHER
    }

    /** Same display curve used by AtCoder Problems for values below 400. */
    fun displayDifficulty(raw: Double?): Int? {
        if (raw == null || !raw.isFinite()) return null
        val displayed = if (raw < 400.0) 400.0 / exp((400.0 - raw) / 400.0) else raw
        if (!displayed.isFinite() || displayed !in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) return null
        return displayed.roundToInt()
    }

    fun toRemoteProblem(
        problem: AtCoderMergedProblemDto,
        model: AtCoderProblemModelDto?,
        contest: AtCoderContestDto?,
        now: Long,
    ): RemoteProblemEntity {
        val heuristic = problem.contestId.startsWith("ahc", ignoreCase = true) ||
            contest?.title?.contains("heuristic", ignoreCase = true) == true ||
            contest?.title?.contains("marathon", ignoreCase = true) == true
        val difficulty = if (heuristic) null else displayDifficulty(model?.difficulty)
        return RemoteProblemEntity(
            judge = JudgeId.ATCODER.id,
            externalId = problem.id,
            contestId = problem.contestId,
            index = problem.problemIndex,
            name = problem.name,
            type = contest?.rateChange,
            rating = difficulty,
            difficultySource = if (difficulty == null) {
                DifficultySource.UNKNOWN.name
            } else {
                DifficultySource.ESTIMATED.name
            },
            points = problem.point,
            solvedCount = problem.solverCount,
            updatedAt = now,
            lastSeenAt = now,
        )
    }

    fun toContest(contest: AtCoderContestDto, now: Long): ContestEntity = ContestEntity(
        judge = JudgeId.ATCODER.id,
        externalContestId = contest.id,
        name = contest.title,
        type = contest.rateChange,
        phase = "TIME_DERIVED",
        durationSeconds = contest.durationSecond,
        startTimeSeconds = contest.startEpochSecond,
        updatedAt = now,
    )
}
