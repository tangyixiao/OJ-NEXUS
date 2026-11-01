package com.ojnexus.core.data.restore

import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.Test

@RunWith(Parameterized::class)
class RecoveryVectorDecisionTest(
    private val vectorId: String,
    private val vector: RecoveryVector,
) {
    @Test
    fun `android projection matches the production decision`() {
        val projection = requireNotNull(vector.android)

        when (projection.kind) {
            AndroidProjectionKind.RECOVERY_DECISION -> {
                val files = requireNotNull(projection.files)
                assertEquals(
                    vectorId,
                    requireNotNull(projection.expected_action),
                    decideRestoreRecovery(
                        projection.journal?.let { journal ->
                            RestoreJournal(journal.stage, journal.generation, journal.detail)
                        },
                        RestoreFileState(
                            target = files.target,
                            candidate = files.candidate,
                            rollback = files.rollback,
                            staged = files.staged,
                        ),
                    ),
                )
            }

            AndroidProjectionKind.GENERATION_DECISION -> assertEquals(
                vectorId,
                requireNotNull(projection.expected_decision),
                decideRestoreWorkGeneration(projection.expected_generation, requireNotNull(projection.current_generation)),
            )
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun projections(): Collection<Array<Any>> = RecoveryVectorFixtures.load().vectors
            .filter { it.android != null }
            .map { vector -> arrayOf(vector.id, vector) }
    }
}

class RecoveryVectorDecisionCoverageTest {
    @Test
    fun `consumer binds exactly nine projections and leaves all other vectors unbound`() {
        val vectors = RecoveryVectorFixtures.load().vectors
        val boundIds = vectors.filter { it.android != null }.map { it.id }.toSet()
        val expectedBoundIds = setOf("RV010", "RV011", "RV012", "RV013", "RV014", "RV016", "RV020", "RV021", "RV022")
        val explicitlyUnboundRecoveryCases = setOf("RV015", "RV017")
        val unboundIds = vectors.filter { it.android == null }.map { it.id }.toSet()
        val expectedUnboundIds = (1..32).map { "RV%03d".format(it) }.toSet() - expectedBoundIds

        assertEquals(expectedBoundIds, boundIds)
        assertEquals(expectedUnboundIds, unboundIds)
        assertEquals(explicitlyUnboundRecoveryCases, unboundIds intersect explicitlyUnboundRecoveryCases)
    }
}
