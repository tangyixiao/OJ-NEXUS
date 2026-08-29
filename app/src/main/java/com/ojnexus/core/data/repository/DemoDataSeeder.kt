package com.ojnexus.core.data.repository

import androidx.room.withTransaction
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.AttemptEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.ProblemKey
import com.ojnexus.core.model.TaskType
import com.ojnexus.core.model.Verdict
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * DEBUG-ONLY development seeder. The demo dataset is always distinguishable from real data:
 * every demo problem is tagged [DEMO_TAG] and its title carries the DEMO prefix. Release
 * builds never expose the entry points; a shipped database starts empty.
 */
class DemoDataSeeder(
    private val database: OjNexusDatabase,
    private val problemRepository: ProblemRepository,
    private val reviewRepository: ReviewRepository,
    private val trainingRepository: TrainingRepository,
    private val clock: Clock,
) {

    suspend fun insertDemoData() {
        val today = clock.dayIndex()
        demoProblems.forEachIndexed { index, demo ->
            val key = ProblemKey(demo.judge, demo.externalId)
            val problemId = if (problemRepository.keyExists(key)) {
                problemRepository.findProblemByKey(key)?.id ?: return@forEachIndexed
            } else {
                problemRepository.addProblem(
                    ProblemRepository.ProblemInput(
                        key = key,
                        title = "$DEMO_PREFIX${demo.title}",
                        difficulty = demo.difficulty,
                        tags = demo.tags + DEMO_TAG,
                        sourceUrl = demo.url,
                    ),
                ).getOrNull() ?: return@forEachIndexed
            }

            // A short attempt history spread over the last days.
            val base = clock.instant().minus((index + 1L) * 22L, ChronoUnit.HOURS)
            demo.attempts.forEachIndexed { attemptIndex, verdict ->
                addAttemptAt(problemId, verdict, base.plus(attemptIndex * 35L, ChronoUnit.MINUTES))
            }

            if (demo.inReview) {
                reviewRepository.scheduleReview(problemId)
            }
        }

        // Today's task list.
        trainingRepository.addTask(today, TaskType.SOLVE, problemId = null, title = "$DEMO_PREFIX Solve one graph problem", priority = 80)
        trainingRepository.addTask(today, TaskType.READ, problemId = null, title = "$DEMO_PREFIX Read SCC notes", priority = 60)
        trainingRepository.addTask(today, TaskType.UPSOLVE, problemId = null, title = "$DEMO_PREFIX Upsolve virtual round", priority = 40)
    }

    /** Removes every problem tagged [DEMO_TAG]; cascades clean attempts/reviews/notes/tasks. */
    suspend fun clearDemoData() {
        val demoIds = database.problemDao().findLibrary()
            .filter { pojo -> pojo.tags.any { it.name == DEMO_TAG } }
            .map { it.problem.id }
        demoIds.forEach { problemRepository.deleteProblem(it) }
    }

    /**
     * Attempts are written with explicit timestamps (not the clock) so the demo dataset
     * produces a realistic multi-day heatmap. The write mirrors the repository's transaction.
     */
    private suspend fun addAttemptAt(problemId: Long, verdict: Verdict, at: Instant) {
        database.withTransaction {
            val dayIndex = LocalDate.ofInstant(at, clock.zone).toEpochDay()
            database.attemptDao().insert(
                AttemptEntity(
                    problemId = problemId,
                    timestamp = at.toEpochMilli(),
                    dayIndex = dayIndex,
                    verdict = verdict.name,
                    note = DEMO_TAG,
                ),
            )
            database.problemDao().applyAttempt(
                id = problemId,
                timestamp = at.toEpochMilli(),
                solved = verdict.isAccepted,
                firstSolvedAt = if (verdict.isAccepted) at.toEpochMilli() else null,
                updatedAt = at.toEpochMilli(),
            )
        }
    }

    companion object {
        const val DEMO_TAG = "demo"
        const val DEMO_PREFIX = "DEMO · "
    }
}

private data class DemoProblem(
    val judge: JudgeId,
    val externalId: String,
    val title: String,
    val difficulty: Int?,
    val tags: List<String>,
    val url: String?,
    val attempts: List<Verdict>,
    val inReview: Boolean,
)

private val demoProblems = listOf(
    DemoProblem(JudgeId.CODEFORCES, "1029E", "Tree with Small Distances", 1900, listOf("trees", "greedy"), null, listOf(Verdict.WA, Verdict.WA, Verdict.AC), inReview = true),
    DemoProblem(JudgeId.LUOGU, "P4551", "Longest Chain in a Tree", null, listOf("trees", "dp"), null, listOf(Verdict.AC), inReview = false),
    DemoProblem(JudgeId.ATCODER, "ABC 332F", "Prefix Query", 2000, listOf("data structures", "segment tree"), null, listOf(Verdict.WA, Verdict.TLE, Verdict.AC), inReview = true),
    DemoProblem(JudgeId.CODEFORCES, "1980F", "Field Division", 2100, listOf("binary search", "dp"), null, listOf(Verdict.WA, Verdict.TLE), inReview = false),
    DemoProblem(JudgeId.CODEFORCES, "1896D", "Frequency Table", 2200, listOf("data structures"), null, listOf(Verdict.RE), inReview = false),
    DemoProblem(JudgeId.LUOGU, "P6280", "Bounded Knapsack", null, listOf("dp", "knapsack"), null, listOf(Verdict.WA, Verdict.WA, Verdict.WA, Verdict.AC), inReview = false),
    DemoProblem(JudgeId.ATCODER, "ABC 242G", "Range Pairing Query", 1900, listOf("data structures", "mo's algorithm"), null, listOf(Verdict.TLE), inReview = false),
    DemoProblem(JudgeId.CODEFORCES, "2134C", "Yet Another Array Query", 1700, listOf("constructive"), null, listOf(Verdict.AC), inReview = false),
)
