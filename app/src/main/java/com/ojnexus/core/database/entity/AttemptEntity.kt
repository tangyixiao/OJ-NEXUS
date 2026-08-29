package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One recorded attempt — manual entry today, synced judge submissions share the same row. */
@Entity(
    tableName = "attempts",
    foreignKeys = [
        ForeignKey(
            entity = ProblemEntity::class,
            parentColumns = ["id"],
            childColumns = ["problem_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["problem_id"]),
        Index(value = ["day_index"]),
        Index(value = ["timestamp"]),
        // Remote idempotency key. SQLite UNIQUE treats NULLs as distinct, so manual
        // attempts (both columns NULL) never conflict with each other or with syncs.
        Index(
            value = ["source_judge", "external_submission_id"],
            unique = true,
        ),
    ],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "problem_id") val problemId: Long,
    /** UTC epoch millis of the attempt. */
    val timestamp: Long,
    /** Local calendar day of the attempt, as epoch day — stable day bucketing key. */
    @ColumnInfo(name = "day_index") val dayIndex: Long,
    /** Unified verdict name, see [com.ojnexus.core.model.Verdict]. */
    val verdict: String,
    /** Judge-side verdict as sent by the OJ; null for manual entries. */
    @ColumnInfo(name = "raw_verdict") val rawVerdict: String? = null,
    @ColumnInfo(name = "duration_min") val durationMinutes: Int? = null,
    val language: String? = null,
    val note: String? = null,
    // --- Remote-sync origin (v2). NULL = manual entry. ---
    /** [com.ojnexus.core.model.JudgeId] id of the source judge; null for manual entries. */
    @ColumnInfo(name = "source_judge") val sourceJudge: String? = null,
    /** Judge-side submission id — the sync idempotency key for imported attempts. */
    @ColumnInfo(name = "external_submission_id") val externalSubmissionId: String? = null,
    @ColumnInfo(name = "contest_id") val contestId: Long? = null,
    /** Codeforces participant type (CONTESTANT / OUT_OF_COMPETITION / VIRTUAL / PRACTICE). */
    @ColumnInfo(name = "participant_type") val participantType: String? = null,
    /** Codeforces testset (SAMPLES / SYSTEM / PRETESTS). */
    val testset: String? = null,
    @ColumnInfo(name = "passed_test_count") val passedTestCount: Int? = null,
    /** Judge-reported execution time in millis (Codeforces timeConsumedMillis). */
    @ColumnInfo(name = "execution_time_ms") val executionTimeMs: Int? = null,
    /** Judge-reported memory in bytes (Codeforces memoryConsumedBytes). */
    @ColumnInfo(name = "memory_bytes") val memoryBytes: Long? = null,
)
