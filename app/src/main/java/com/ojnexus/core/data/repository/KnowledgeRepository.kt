package com.ojnexus.core.data.repository

import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ProblemKnowledgeEntity
import com.ojnexus.core.domain.KnowledgeEvidence
import com.ojnexus.core.domain.MasteryEngine
import com.ojnexus.core.domain.MasteryReason
import com.ojnexus.core.model.KnowledgeArea
import com.ojnexus.core.model.pendingAutoLinks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class KnowledgeAreaState(
    val area: KnowledgeArea,
    val attemptedProblems: Int,
    val solvedProblems: Int,
    val attempts: Int,
    val failures: Int,
    val score: Int,
    val reasons: Set<MasteryReason>,
)

/** Owns explicit knowledge relations and maps SQL evidence through the pure mastery policy. */
class KnowledgeRepository(private val database: OjNexusDatabase) {
    private val knowledgeDao = database.knowledgeDao()

    fun observeRelations(problemId: Long): Flow<Set<KnowledgeArea>> = knowledgeDao
        .observeForProblem(problemId)
        .map { rows -> rows.mapNotNull { raw -> KnowledgeArea.entries.firstOrNull { it.name == raw } }.toSet() }

    fun observeMastery(): Flow<List<KnowledgeAreaState>> = knowledgeDao
        .observePerformance()
        .map { rows ->
            val byArea = rows.associateBy { it.area }
            KnowledgeArea.entries.map { area ->
                val row = byArea[area.name]
                val evidence = KnowledgeEvidence(
                    attemptedProblems = row?.attemptedProblems ?: 0,
                    solvedProblems = row?.solvedProblems ?: 0,
                    attempts = row?.attempts ?: 0,
                    failures = row?.failures ?: 0,
                )
                val result = MasteryEngine.evaluate(area, evidence)
                KnowledgeAreaState(
                    area = area,
                    attemptedProblems = evidence.attemptedProblems,
                    solvedProblems = evidence.solvedProblems,
                    attempts = evidence.attempts,
                    failures = evidence.failures,
                    score = result.score,
                    reasons = result.reasons,
                )
            }
        }

    suspend fun setRelation(problemId: Long, area: KnowledgeArea, selected: Boolean) {
        if (selected) {
            knowledgeDao.upsert(ProblemKnowledgeEntity(problemId, area.name))
        } else {
            knowledgeDao.delete(problemId, area.name)
        }
    }

    /**
     * Applies the deterministic alias match for one problem's tags, then returns the areas it
     * linked. Already-linked areas are skipped, so a repeated call links nothing new.
     */
    suspend fun autoLink(problemId: Long, tags: Collection<String>): List<KnowledgeArea> {
        val existing = knowledgeDao.findForProblem(problemId)
            .mapNotNull { raw -> KnowledgeArea.entries.firstOrNull { it.name == raw } }
            .toSet()
        val pending = pendingAutoLinks(tags, existing)
        if (pending.isNotEmpty()) {
            knowledgeDao.upsertAll(
                pending.map { area -> ProblemKnowledgeEntity(problemId, area.name) },
            )
        }
        return pending
    }
}
