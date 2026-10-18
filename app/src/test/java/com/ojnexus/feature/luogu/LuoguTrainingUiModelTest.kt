package com.ojnexus.feature.luogu

import com.ojnexus.judge.luogu.api.dto.LuoguTrainingProblemDto
import com.ojnexus.judge.luogu.api.dto.LuoguTrainingProviderDto
import com.ojnexus.judge.luogu.api.dto.LuoguTrainingSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-model checks behind the catalog/detail screens (no Robolectric UI needed). */
class LuoguTrainingUiModelTest {

    @Test
    fun `catalog rows derive a context line from provider and counts`() {
        val training = LuoguTrainingSummaryDto(
            id = 100,
            name = "【入门1】顺序结构",
            provider = LuoguTrainingProviderDto(uid = 3, name = "洛谷", color = "Purple"),
            problemCount = 15,
            markCount = 38862,
        )
        val context = trainingCatalogContext(training, problemCountLabel = "15 PROBLEMS")

        assertTrue("洛谷" in context)
        assertTrue("15 PROBLEMS" in context)
    }

    @Test
    fun `ladder rows fall back to pid when the problem has no name`() {
        val problem = LuoguTrainingProblemDto(pid = "B2002", name = "")
        assertEquals("B2002", trainingProblemTitle(problem))
    }

    @Test
    fun `ladder positions render as zero-padded indices starting at one`() {
        assertEquals("01", ladderPositionLabel(0))
        assertEquals("15", ladderPositionLabel(14))
    }
}
