package com.ojnexus.judge.luogu

import com.ojnexus.core.database.entity.ContestEntity
import com.ojnexus.core.database.entity.JudgeProfileEntity
import com.ojnexus.core.database.entity.RatingChangeEntity
import com.ojnexus.core.database.entity.RemoteProblemEntity
import com.ojnexus.core.model.DifficultySource
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.codeforces.mapper.ContestPhase
import com.ojnexus.judge.luogu.api.dto.LuoguContestDto
import com.ojnexus.judge.luogu.api.dto.LuoguEloEntryDto
import com.ojnexus.judge.luogu.api.dto.LuoguProblemDto
import com.ojnexus.judge.luogu.api.dto.LuoguPublicUserDto

/** Deterministic Luogu DTO-to-local mappings; no network or UI dependencies. */
object LuoguMappers {
    fun toProfileEntity(
        profile: LuoguPublicUserDto,
        currentRating: Int?,
        judge: JudgeId,
        updatedAt: Long,
    ): JudgeProfileEntity = JudgeProfileEntity(
        judge = judge.id,
        handle = profile.name,
        rating = profile.eloValue ?: currentRating,
        registrationTimeSeconds = profile.registerTime,
        avatar = normalizeAvatar(profile.avatar),
        introduction = profile.introduction,
        slogan = profile.slogan,
        badge = profile.badge,
        ranking = profile.ranking,
        followerCount = profile.followerCount,
        followingCount = profile.followingCount,
        passedProblemCount = profile.passedProblemCount,
        submittedProblemCount = profile.submittedProblemCount,
        ccfLevel = profile.ccfLevel,
        xcpcLevel = profile.xcpcLevel,
        updatedAt = updatedAt,
    )

    fun toRatingChangeEntities(
        judge: JudgeId,
        handle: String,
        entries: List<LuoguEloEntryDto>,
    ): List<RatingChangeEntity> {
        val chronological = entries
            .filter { it.contest?.id != null && it.rating != null && it.time != null }
            .sortedBy { it.time }
        var priorRating: Int? = null
        return chronological.mapNotNull { entry ->
            val contest = entry.contest ?: return@mapNotNull null
            val rating = entry.rating ?: return@mapNotNull null
            val result = RatingChangeEntity(
                judge = judge.id,
                handle = handle,
                contestId = contest.id.toString(),
                contestName = contest.name,
                rank = null,
                oldRating = entry.previous?.rating ?: priorRating,
                newRating = rating,
                ratingUpdateTimeSeconds = entry.time ?: return@mapNotNull null,
            )
            priorRating = rating
            result
        }
    }

    fun toRemoteProblemEntity(
        problem: LuoguProblemDto,
        judge: JudgeId,
        updatedAt: Long,
    ): RemoteProblemEntity = RemoteProblemEntity(
        judge = judge.id,
        externalId = problem.pid,
        name = problem.name,
        type = problem.type,
        rating = problem.difficulty,
        difficultySource = if (problem.difficulty != null) DifficultySource.OFFICIAL.name
        else DifficultySource.UNKNOWN.name,
        tags = problem.tags.joinToString(separator = "\u001F"),
        solvedCount = problem.totalAccepted,
        updatedAt = updatedAt,
    )

    fun toContestEntity(
        contest: LuoguContestDto,
        judge: JudgeId,
        updatedAt: Long,
        nowSeconds: Long,
    ): ContestEntity {
        val duration = if (contest.startTime != null && contest.endTime != null) {
            (contest.endTime - contest.startTime).coerceAtLeast(0L)
        } else {
            0L
        }
        return ContestEntity(
            judge = judge.id,
            externalContestId = contest.id.toString(),
            name = contest.name,
            type = "LUOGU_METHOD_${contest.method ?: 0}${if (contest.rated == 1) "_RATED" else ""}",
            phase = ContestPhase.of(
                rawPhase = "LUOGU",
                startTimeSeconds = contest.startTime,
                durationSeconds = duration,
                nowSeconds = nowSeconds,
            ).name,
            frozen = false,
            durationSeconds = duration,
            startTimeSeconds = contest.startTime,
            preparedBy = contest.host?.name,
            updatedAt = updatedAt,
        )
    }

    fun normalizeAvatar(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val candidate = when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> "https://${url.removePrefix("http://")}"
            url.startsWith("https://") -> url
            else -> return null
        }
        return candidate.takeIf { it.length < 2048 }
    }
}
