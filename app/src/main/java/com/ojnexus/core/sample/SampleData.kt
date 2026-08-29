package com.ojnexus.core.sample

import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.KnowledgeArea
import com.ojnexus.core.model.TrainingType
import com.ojnexus.core.model.Verdict
import com.ojnexus.feature.analytics.AnalyticsUiState
import com.ojnexus.feature.analytics.HeatmapUi
import com.ojnexus.feature.analytics.TagCountUi
import com.ojnexus.feature.analytics.VerdictCountUi
import com.ojnexus.feature.dashboard.DashboardUiState
import com.ojnexus.feature.dashboard.JudgeSyncUi
import com.ojnexus.feature.dashboard.NextContestUi
import com.ojnexus.feature.dashboard.SubmissionRowUi
import com.ojnexus.feature.dashboard.SyncStateUi
import com.ojnexus.feature.dashboard.TodayTaskUi
import com.ojnexus.feature.problems.ProblemRowUi
import com.ojnexus.feature.problems.ProblemStatusUi
import com.ojnexus.feature.problems.ProblemsUiState
import com.ojnexus.feature.profile.GlobalStatsUi
import com.ojnexus.feature.profile.JudgeCardUi
import com.ojnexus.feature.profile.ProfileUiState
import com.ojnexus.feature.training.AreaMasteryUi
import com.ojnexus.feature.training.TargetItemUi
import com.ojnexus.feature.training.TrainingReasonUi
import com.ojnexus.feature.training.TrainingUiState

/**
 * DEVELOPMENT SAMPLE DATASET — UI PREVIEW ONLY.
 *
 * Nothing here is connected to a repository, engine, or network source. These objects exist so
 * the shell and screens can be developed and reviewed against realistic shapes before the sync
 * engine (Phase 2+) produces real data. Every screen renders this via a default parameter and
 * the dashboard marks itself with the DEV SAMPLE tag so the state is never mistaken for live data.
 */
object SampleData {

    val dashboard = DashboardUiState(
        sync = listOf(
            JudgeSyncUi(JudgeId.CODEFORCES, SyncStateUi.SYNCED, lastSyncText = "12:40"),
            JudgeSyncUi(JudgeId.ATCODER, SyncStateUi.NOT_LINKED),
            JudgeSyncUi(JudgeId.LUOGU, SyncStateUi.NOT_LINKED),
        ),
        rating = 1842,
        ratingDelta = 24,
        weeklyAc = 37,
        streakDays = 12,
        todayTasks = listOf(
            TodayTaskUi(
                code = "CF 1029E",
                title = "Tree with Small Distances",
                priority = 91,
                masteryPercent = 61,
            ),
            TodayTaskUi(
                code = "ABC 332F",
                title = "Prefix Query",
                priority = 78,
                masteryPercent = 55,
            ),
            TodayTaskUi(
                code = "P4551",
                title = "Longest Chain in a Tree",
                priority = 64,
                masteryPercent = 72,
            ),
        ),
        nextContest = NextContestUi(
            name = "Codeforces Round 1042 (Div. 2)",
            judge = JudgeId.CODEFORCES,
            startsText = "SAT 19:35",
            countdownText = "41:27:03",
        ),
        recentActivity = listOf(
            SubmissionRowUi("21:43", JudgeId.LUOGU, "P11242", Verdict.AC),
            SubmissionRowUi("20:51", JudgeId.CODEFORCES, "2134C", Verdict.WA),
            SubmissionRowUi("20:45", JudgeId.CODEFORCES, "2134C", Verdict.WA),
            SubmissionRowUi("19:22", JudgeId.ATCODER, "ABC 352E", Verdict.AC),
            SubmissionRowUi("18:04", JudgeId.CODEFORCES, "2134B", Verdict.AC),
        ),
        trainingLoad = listOf(2, 3, 1, 4, 2, 3, 1),
    )

    val problems = ProblemsUiState(
        totalCount = 1284,
        rows = listOf(
            ProblemRowUi(
                JudgeId.CODEFORCES, "2134C", "Yet Another Array Query",
                rating = 1700, status = ProblemStatusUi.SOLVED, masteryPercent = 86,
            ),
            ProblemRowUi(
                JudgeId.CODEFORCES, "1029E", "Tree with Small Distances",
                rating = 1900, status = ProblemStatusUi.ATTEMPTED, masteryPercent = 41,
            ),
            ProblemRowUi(
                JudgeId.LUOGU, "P4551", "Longest Chain in a Tree",
                rating = null, status = ProblemStatusUi.SOLVED, masteryPercent = 78,
            ),
            ProblemRowUi(
                JudgeId.ATCODER, "ABC 332F", "Prefix Query",
                rating = 2000, status = ProblemStatusUi.ATTEMPTED, masteryPercent = 55,
            ),
            ProblemRowUi(
                JudgeId.CODEFORCES, "1980F", "Field Division",
                rating = 2100, status = ProblemStatusUi.UNSOLVED, masteryPercent = 12,
            ),
            ProblemRowUi(
                JudgeId.ATCODER, "ABC 242G", "Range Pairing Query",
                rating = 1900, status = ProblemStatusUi.UNSOLVED, masteryPercent = 0,
            ),
            ProblemRowUi(
                JudgeId.CODEFORCES, "1896D", "Frequency Table",
                rating = 2200, status = ProblemStatusUi.UNSOLVED, masteryPercent = 8,
            ),
            ProblemRowUi(
                JudgeId.LUOGU, "P6280", "Bounded Knapsack",
                rating = null, status = ProblemStatusUi.UNSOLVED, masteryPercent = null,
            ),
        ),
    )

    val training = TrainingUiState(
        sessionActive = false,
        selectedType = TrainingType.PRACTICE,
        durationMinutes = 90,
        targetProblemCount = 5,
        targets = listOf(
            TargetItemUi(
                code = "CF 1029E",
                priority = 91,
                reasons = listOf(TrainingReasonUi.WEAK_MASTERY, TrainingReasonUi.REVIEW_GAP),
                reviewGapDays = 18,
                targetRange = "1700–1900",
            ),
            TargetItemUi(
                code = "ABC 332F",
                priority = 76,
                reasons = listOf(TrainingReasonUi.WEAK_MASTERY),
                reviewGapDays = null,
                targetRange = "1900–2100",
            ),
            TargetItemUi(
                code = "P6280",
                priority = 64,
                reasons = listOf(TrainingReasonUi.REVIEW_GAP),
                reviewGapDays = 9,
                targetRange = "1600–1800",
            ),
        ),
        weakAreas = listOf(
            AreaMasteryUi(KnowledgeArea.GRAPH, 41),
            AreaMasteryUi(KnowledgeArea.STRING, 47),
            AreaMasteryUi(KnowledgeArea.DYNAMIC_PROGRAMMING, 62),
            AreaMasteryUi(KnowledgeArea.DATA_STRUCTURE, 58),
        ),
    )

    val analytics = AnalyticsUiState(
        heatmap = HeatmapUi(weeks = buildHeatmap(weeks = 22, seed = 0x4E4558u)),
        ratingTrend = listOf(1770, 1781, 1806, 1799, 1815, 1842),
        verdictCounts = listOf(
            VerdictCountUi(Verdict.AC, 342),
            VerdictCountUi(Verdict.WA, 118),
            VerdictCountUi(Verdict.TLE, 42),
            VerdictCountUi(Verdict.RE, 21),
            VerdictCountUi(Verdict.CE, 7),
        ),
        weakTags = listOf(
            TagCountUi("dp", 31),
            TagCountUi("trees", 24),
            TagCountUi("greedy", 19),
            TagCountUi("data structures", 17),
            TagCountUi("graphs", 15),
        ),
    )

    val profile = ProfileUiState(
        handle = "NEXUS_PILOT",
        judgeCards = listOf(
            JudgeCardUi(
                judge = JudgeId.CODEFORCES,
                linked = true,
                ratingText = "1842",
                rankText = "SPECIALIST",
                solvedCount = 1204,
                contestCount = 87,
            ),
            JudgeCardUi(
                judge = JudgeId.ATCODER,
                linked = true,
                ratingText = "982",
                rankText = "GREEN",
                solvedCount = 311,
                contestCount = 42,
            ),
            JudgeCardUi(
                judge = JudgeId.LUOGU,
                linked = false,
            ),
        ),
        global = GlobalStatsUi(
            solved = 1284,
            submissions = 4931,
            activeDays = 217,
            streakDays = 12,
            maxDifficulty = 2400,
        ),
    )
}

/** Deterministic pseudo-random intensities so previews are stable across renders. */
private fun buildHeatmap(weeks: Int, seed: UInt): List<List<Int>> {
    var state = seed
    fun next(): Int {
        state = state * 1664525U + 1013904223U
        return ((state shr 24) % 11U).toInt().let { raw -> if (raw < 4) 0 else (raw - 3) / 2 }
    }
    return List(weeks) { List(7) { next().coerceIn(0, 4) } }
}
