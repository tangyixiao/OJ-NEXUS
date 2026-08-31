package com.ojnexus.judge.luogu

import com.ojnexus.judge.luogu.api.dto.LuoguContestDetailData
import com.ojnexus.judge.luogu.api.dto.LuoguContestDetailDto
import com.ojnexus.judge.luogu.api.dto.LuoguContestProblemDto
import com.ojnexus.judge.luogu.api.dto.LuoguContestProblemRefDto
import org.junit.Assert.assertEquals
import org.junit.Test

class LuoguContestDetailTest {

    @Test
    fun `maps contest description and official contest problem members`() {
        val detail = LuoguContestDetailMapper.toDomain(
            LuoguContestDetailData(
                contest = LuoguContestDetailDto(
                    id = 123,
                    name = "Monthly Contest",
                    startTime = 1_700_000_000,
                    endTime = 1_700_003_600,
                    description = "## Rules\n\nPlease submit.",
                    problemCount = 1,
                ),
                contestProblems = listOf(
                    LuoguContestProblemDto(
                        score = 100,
                        problem = LuoguContestProblemRefDto(pid = "P1001", name = "A+B Problem", difficulty = 1),
                        no = "A",
                    ),
                ),
            ),
        )

        assertEquals("Monthly Contest", detail.name)
        assertEquals("## Rules\n\nPlease submit.", detail.description)
        assertEquals(1, detail.problems.size)
        assertEquals("A", detail.problems.single().index)
        assertEquals("P1001", detail.problems.single().pid)
        assertEquals(100, detail.problems.single().score)
    }
}
