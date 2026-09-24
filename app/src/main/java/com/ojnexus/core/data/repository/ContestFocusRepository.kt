package com.ojnexus.core.data.repository

import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ContestEntity
import com.ojnexus.core.database.entity.ContestProblemMarkerEntity
import com.ojnexus.core.model.ContestMarker
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A problem row shown by the local Arena focus view. */
data class ContestFocusProblem(
    val judge: String,
    val externalId: String,
    val name: String,
    val index: String?,
    val rating: Int?,
    val difficultySource: String,
    val points: Double?,
    val localProblemId: Long?,
    val solved: Boolean,
    val attemptCount: Int,
    val latestVerdict: String?,
    val marker: ContestMarker,
)

data class ContestFocusSnapshot(
    val contest: ContestEntity?,
    val problems: List<ContestFocusProblem>,
)

/**
 * Local-first read/write boundary for Arena. Remote contest catalog rows and local submission
 * progress are joined by Room; markers are deliberately stored in their own local-only table.
 */
class ContestFocusRepository(
    private val database: OjNexusDatabase,
    private val clock: Clock,
) {
    fun observeFocus(judge: String, contestId: String): Flow<ContestFocusSnapshot> = combine(
        database.contestDao().observeByKey(judge, contestId),
        database.remoteProblemDao().observeContestProgress(judge, contestId),
        database.contestProblemMarkerDao().observeByContest(judge, contestId),
    ) { contest, rows, markers ->
        val markerByProblem = markers.associateBy { it.problemExternalId }
        ContestFocusSnapshot(
            contest = contest,
            problems = rows.map { row ->
                ContestFocusProblem(
                    judge = row.judge,
                    externalId = row.externalId,
                    name = row.name,
                    index = row.index,
                    rating = row.rating,
                    difficultySource = row.difficultySource,
                    points = row.points,
                    localProblemId = row.localProblemId,
                    solved = row.solved == true,
                    attemptCount = row.attemptCount ?: 0,
                    latestVerdict = row.latestVerdict,
                    marker = markerByProblem[row.externalId]?.marker.toContestMarker(),
                )
            },
        )
    }

    suspend fun cycleMarker(judge: String, contestId: String, problemExternalId: String) {
        val existing = database.contestProblemMarkerDao().find(judge, contestId, problemExternalId)
        val current = existing?.marker.toContestMarker()
        val next = current.next()
        if (next == ContestMarker.NONE) {
            database.contestProblemMarkerDao().delete(judge, contestId, problemExternalId)
        } else {
            database.contestProblemMarkerDao().upsert(
                ContestProblemMarkerEntity(
                    judge = judge,
                    contestId = contestId,
                    problemExternalId = problemExternalId,
                    marker = next.name,
                    updatedAt = clock.millis(),
                ),
            )
        }
    }
}

private fun String?.toContestMarker(): ContestMarker =
    ContestMarker.entries.firstOrNull { it.name == this } ?: ContestMarker.NONE
