package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One connected judge account per judge (single-active invariant is enforced in the
 * account repository transaction; the unique index additionally blocks duplicate rows
 * for the same canonical handle). Handles are canonical — the API's spelling, never the
 * user's input casing.
 */
@Entity(
    tableName = "judge_accounts",
    indices = [Index(value = ["judge", "canonical_handle"], unique = true)],
)
data class JudgeAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [com.ojnexus.core.model.JudgeId] id. */
    val judge: String,
    /** Handle as entered by the user, kept for reference only. */
    val handle: String,
    /** Canonical handle as returned by the judge API — the authoritative identity. */
    @ColumnInfo(name = "canonical_handle") val canonicalHandle: String,
    @ColumnInfo(name = "connected_at") val connectedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val enabled: Boolean = true,
    /** VERIFIED or UNVERIFIED; soft-bound judges may remain connected while unverified. */
    @ColumnInfo(name = "verification_state") val verificationState: String = "VERIFIED",
    /** [com.ojnexus.judge.DataSourceReliability] name. */
    @ColumnInfo(name = "source_reliability") val sourceReliability: String = "OFFICIAL",
)
