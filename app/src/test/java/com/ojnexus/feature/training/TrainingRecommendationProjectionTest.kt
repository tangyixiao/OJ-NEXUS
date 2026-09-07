package com.ojnexus.feature.training

import com.ojnexus.core.database.dao.TrainingCandidateRow
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.KnowledgeArea
import com.ojnexus.core.model.TrainingTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingRecommendationProjectionTest {
    @Test
    fun `projection uses judge target and linked weak areas as explanations`() {
        val recommendation = projectTrainingRecommendation(
            row = TrainingCandidateRow(
                id = 8L,
                judge = JudgeId.CODEFORCES.id,
                externalId = "1800A",
                title = "Graph",
                difficulty = 1800,
                solved = false,
                attemptCount = 1,
                failureCount = 1,
                reviewDue = false,
                knowledgeAreas = "GRAPH,DYNAMIC_PROGRAMMING",
            ),
            targets = listOf(TrainingTarget(null, 1500, 100), TrainingTarget(JudgeId.CODEFORCES, 1750, 100)),
            masteryScores = mapOf(
                KnowledgeArea.GRAPH to 20,
                KnowledgeArea.DYNAMIC_PROGRAMMING to 90,
            ),
        )

        assertEquals(8L, recommendation.problemId)
        assertTrue(com.ojnexus.core.domain.TrainingReason.DIFFICULTY_FIT in recommendation.reasons)
        assertTrue(com.ojnexus.core.domain.TrainingReason.LOW_MASTERY in recommendation.reasons)
        assertEquals("1800A", recommendation.externalId)
    }
}
