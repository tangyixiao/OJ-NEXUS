package com.ojnexus.feature.problems

import com.ojnexus.core.database.entity.RemoteProblemEntity
import com.ojnexus.core.model.JudgeId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProblemWorkspaceTest {
    @Test
    fun `only Luogu remote problems expose the Open Platform workspace`() {
        assertTrue(remoteWorkspaceAvailable(problem(JudgeId.LUOGU)))
        assertFalse(remoteWorkspaceAvailable(problem(JudgeId.CODEFORCES)))
        assertFalse(remoteWorkspaceAvailable(problem(JudgeId.ATCODER)))
    }

    private fun problem(judge: JudgeId) = RemoteProblemEntity(
        judge = judge.id,
        externalId = "P1001",
        name = "A+B Problem",
        updatedAt = 1,
    )
}
