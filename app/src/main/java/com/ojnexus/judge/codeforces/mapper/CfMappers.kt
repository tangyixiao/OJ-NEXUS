package com.ojnexus.judge.codeforces.mapper

import com.ojnexus.core.database.entity.AttemptEntity
import com.ojnexus.core.database.entity.ContestEntity
import com.ojnexus.core.database.entity.JudgeProfileEntity
import com.ojnexus.core.database.entity.RatingChangeEntity
import com.ojnexus.core.database.entity.RemoteProblemEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Verdict
import com.ojnexus.judge.codeforces.api.dto.CfContestDto
import com.ojnexus.judge.codeforces.api.dto.CfProblemDto
import com.ojnexus.judge.codeforces.api.dto.CfProblemStatisticsDto
import com.ojnexus.judge.codeforces.api.dto.CfRatingChangeDto
import com.ojnexus.judge.codeforces.api.dto.CfSubmissionDto
import com.ojnexus.judge.codeforces.api.dto.CfUserDto
import java.time.LocalDate
import java.time.ZoneId

/**
 * Codeforces DTO → OJ NEXUS database entities. DTOs never leave the judge package; these
 * mappers are total functions — missing/unknown remote fields degrade to null/OTHER and
 * never throw (API compatibility rule).
 */
object CfMappers {

    // --- Problem identity ---

    /**
     * Stable external id: contestId + index without separator ("2134" + "C" = "2134C").
     * Problemset-only problems (no contestId, e.g. some gym/marathon problems) are not
     * representable and are skipped by the sync before reaching this mapper.
     */
    fun externalId(contestId: Long, index: String): String = "$contestId$index"

    fun externalId(problem: CfProblemDto): String? =
        problem.contestId?.let { externalId(it, problem.index) }

    // --- Profile ---

    fun CfUserDto.toProfileEntity(judge: JudgeId, updatedAt: Long): JudgeProfileEntity =
        JudgeProfileEntity(
            judge = judge.id,
            handle = handle,
            firstName = firstName,
            lastName = lastName,
            country = country,
            city = city,
            organization = organization,
            contribution = contribution,
            rating = rating,
            maxRating = maxRating,
            rank = rank,
            maxRank = maxRank,
            friendOfCount = friendOfCount,
            registrationTimeSeconds = registrationTimeSeconds,
            lastOnlineTimeSeconds = lastOnlineTimeSeconds,
            avatar = normalizeAvatar(avatar),
            titlePhoto = normalizeAvatar(titlePhoto),
            updatedAt = updatedAt,
        )

    /**
     * Avatar URLs arrive as absolute, protocol-relative ("//host/…") or occasionally http.
     * Only http(s) is allowed (https preferred); anything else (file:, javascript:, data:)
     * is rejected and renders as the placeholder.
     */
    fun normalizeAvatar(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val candidate = when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> "https://" + url.removePrefix("http://")
            url.startsWith("https://") -> url
            else -> return null
        }
        return candidate.takeIf { it.length < 2048 }
    }

    // --- Remote problem catalog ---

    fun CfProblemDto.toRemoteProblemEntity(
        judge: JudgeId,
        statistics: CfProblemStatisticsDto?,
        updatedAt: Long,
    ): RemoteProblemEntity? {
        val externalId = externalId(this) ?: return null
        return RemoteProblemEntity(
            judge = judge.id,
            externalId = externalId,
            contestId = contestId,
            index = index,
            name = name,
            type = type,
            rating = rating,
            points = points,
            tags = tags.joinToString(separator = "\u001F"),
            solvedCount = statistics?.solvedCount,
            updatedAt = updatedAt,
        )
    }

    /**
     * problemset.problems returns two parallel lists. They are merged by (contestId, index)
     * — never by list position, which the API does not guarantee to align. Statistics are
     * optional per problem; problems without a representable external id are dropped.
     */
    fun mergeProblemset(
        problems: List<CfProblemDto>,
        statistics: List<CfProblemStatisticsDto>,
    ): List<Pair<CfProblemDto, CfProblemStatisticsDto?>> {
        val byKey = statistics.associateBy { stat ->
            stat.contestId?.let { externalId(it, stat.index) } ?: stat.index
        }
        val result = mutableListOf<Pair<CfProblemDto, CfProblemStatisticsDto?>>()
        for (problem in problems) {
            val key = externalId(problem) ?: continue
            result += problem to byKey[key]
        }
        return result
    }

    // --- Submissions ---

    fun submissionVerdict(raw: String?): Verdict = Verdict.fromRaw(raw)

    fun CfSubmissionDto.toAttemptEntity(
        problemId: Long,
        zone: ZoneId,
    ): AttemptEntity = AttemptEntity(
        problemId = problemId,
        timestamp = creationTimeSeconds * 1000,
        dayIndex = LocalDate.ofInstant(
            java.time.Instant.ofEpochSecond(creationTimeSeconds),
            zone,
        ).toEpochDay(),
        verdict = submissionVerdict(verdict).name,
        rawVerdict = verdict,
        language = programmingLanguage,
        note = null,
        sourceJudge = JudgeId.CODEFORCES.id,
        externalSubmissionId = id.toString(),
        contestId = contestId,
        participantType = participantType,
        testset = testset,
        passedTestCount = passedTestCount,
        executionTimeMs = timeConsumedMillis,
        memoryBytes = memoryConsumedBytes,
    )

    // --- Rating history ---

    fun CfRatingChangeDto.toRatingChangeEntity(judge: JudgeId, handle: String): RatingChangeEntity =
        RatingChangeEntity(
            judge = judge.id,
            handle = handle,
            contestId = contestId,
            contestName = contestName,
            rank = rank,
            oldRating = oldRating,
            newRating = newRating,
            ratingUpdateTimeSeconds = ratingUpdateTimeSeconds,
        )

    // --- Contests ---

    fun CfContestDto.toContestEntity(judge: JudgeId, updatedAt: Long): ContestEntity =
        ContestEntity(
            judge = judge.id,
            externalContestId = id,
            name = name,
            type = type,
            phase = phase,
            frozen = frozen ?: false,
            durationSeconds = durationSeconds,
            startTimeSeconds = startTimeSeconds,
            relativeTimeSeconds = relativeTimeSeconds,
            preparedBy = preparedBy,
            updatedAt = updatedAt,
        )
}

/** App-level contest phase; the raw judge-side phase stays on the row. */
enum class ContestPhase { UPCOMING, LIVE, ENDED;

    companion object {

        /**
         * Derives the phase from real time first (the raw phase lags on Codeforces);
         * unknown raw phases degrade via the time rules instead of crashing.
         */
        fun of(
            rawPhase: String,
            startTimeSeconds: Long?,
            durationSeconds: Long,
            nowSeconds: Long,
        ): ContestPhase {
            val start = startTimeSeconds ?: return ContestPhase.ENDED
            return when {
                nowSeconds < start -> UPCOMING
                nowSeconds < start + durationSeconds -> LIVE
                else -> ENDED
            }
        }
    }
}
