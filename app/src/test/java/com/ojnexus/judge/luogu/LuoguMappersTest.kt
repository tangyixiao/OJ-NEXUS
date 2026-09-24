package com.ojnexus.judge.luogu

import com.ojnexus.core.model.DifficultySource
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.luogu.api.dto.LuoguContestDto
import com.ojnexus.judge.luogu.api.dto.LuoguEloEntryDto
import com.ojnexus.judge.luogu.api.dto.LuoguPreviousRatingDto
import com.ojnexus.judge.luogu.api.dto.LuoguProblemDto
import com.ojnexus.judge.luogu.api.dto.LuoguPublicUserDto
import com.ojnexus.judge.luogu.api.dto.LuoguRatingContestDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LuoguMappersTest {
    @Test
    fun `profile mapper retains public Luogu metadata and rating fallback`() {
        val profile = LuoguPublicUserDto(
            uid = 7,
            name = "alice",
            introduction = "bio",
            slogan = "hello",
            avatar = "http://cdn/avatar.png",
            eloValue = null,
            ranking = 42,
            followerCount = 8,
            followingCount = 9,
            passedProblemCount = 10,
            submittedProblemCount = 12,
            badge = "verified",
            ccfLevel = 6,
            xcpcLevel = 3,
        )

        val entity = LuoguMappers.toProfileEntity(
            profile = profile,
            currentRating = 1200,
            judge = JudgeId.LUOGU,
            updatedAt = 99,
        )

        assertEquals("alice", entity.handle)
        assertEquals("https://cdn/avatar.png", entity.avatar)
        assertEquals("bio", entity.introduction)
        assertEquals("hello", entity.slogan)
        assertEquals(1200, entity.rating)
        assertEquals(42, entity.ranking)
        assertEquals(8, entity.followerCount)
        assertEquals(6, entity.ccfLevel)
    }

    @Test
    fun `rating mapper is chronological and keeps unavailable facts null`() {
        val entries = listOf(
            LuoguEloEntryDto(
                rating = 1100,
                time = 100,
                contest = LuoguRatingContestDto(id = 1, name = "First"),
            ),
            LuoguEloEntryDto(
                rating = 1200,
                time = 200,
                contest = LuoguRatingContestDto(id = 2, name = "Second"),
                previous = LuoguPreviousRatingDto(rating = 1100),
            ),
        )

        val changes = LuoguMappers.toRatingChangeEntities(JudgeId.LUOGU, "alice", entries)

        assertEquals(listOf("1", "2"), changes.map { it.contestId })
        assertNull(changes[0].oldRating)
        assertEquals(1100, changes[1].oldRating)
        assertEquals(1200, changes[1].newRating)
        assertNull(changes[1].rank)
    }

    @Test
    fun `problem mapper stores official difficulty and Luogu tags`() {
        val entity = LuoguMappers.toRemoteProblemEntity(
            LuoguProblemDto(
                pid = "P1000",
                name = "A+B Problem",
                type = "P",
                difficulty = 1,
                tags = listOf("math", "入门"),
                totalAccepted = 3,
            ),
            JudgeId.LUOGU,
            updatedAt = 99,
        )

        assertEquals("P1000", entity.externalId)
        assertEquals(DifficultySource.OFFICIAL.name, entity.difficultySource)
        assertEquals(listOf("math", "入门"), entity.tags.split('\u001F'))
        assertEquals(3, entity.solvedCount)
    }

    @Test
    fun `contest mapper derives phase from timestamps`() {
        val entity = LuoguMappers.toContestEntity(
            LuoguContestDto(
                id = 12,
                startTime = 1000,
                endTime = 4600,
                name = "Contest",
                method = 2,
                rated = 1,
                problemCount = 5,
            ),
            JudgeId.LUOGU,
            updatedAt = 99,
            nowSeconds = 2000,
        )

        assertEquals("12", entity.externalContestId)
        assertEquals("LIVE", entity.phase)
        assertEquals(3600, entity.durationSeconds)
        assertEquals("LUOGU_METHOD_2_RATED", entity.type)
    }
}
