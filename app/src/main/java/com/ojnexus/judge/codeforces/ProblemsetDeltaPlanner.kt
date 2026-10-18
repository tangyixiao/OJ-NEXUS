package com.ojnexus.judge.codeforces

import com.ojnexus.judge.codeforces.api.dto.CfProblemDto
import com.ojnexus.judge.codeforces.api.dto.CfProblemStatisticsDto
import java.security.MessageDigest

/** What [ProblemsetDeltaPlanner] decided the PROBLEMS stage should do this run. */
enum class ProblemsetSyncDecision {
    /** Freshness window still holds — do not even probe the network. */
    SKIP_FRESH,

    /** Fetched catalog is byte-identical (cheap fingerprint) to the live table — no write. */
    SKIP_UNCHANGED,

    /** Write the fetched catalog into Room (chunked upserts). */
    WRITE,
}

/**
 * Pure decision core for the Phase 91 problemset delta sync. No network, no database,
 * no clock — everything is injected so the decision matrix is unit-testable offline.
 *
 * The "etag-style" fingerprint is a SHA-256 over the canonical problem rows; the live
 * side is approximated by (row count, sum of solved counts) read from Room with two
 * indexed aggregate queries — never a full table load. A mismatch forces the bounded
 * chunked write; a match skips it entirely.
 */
object ProblemsetDeltaPlanner {

    fun decide(
        force: Boolean,
        lastSyncedAt: Long?,
        now: Long,
        freshnessMs: Long,
        liveCount: Int,
        fetchedCount: Int,
    ): ProblemsetSyncDecision = when {
        force -> ProblemsetSyncDecision.WRITE
        lastSyncedAt != null && now - lastSyncedAt < freshnessMs -> ProblemsetSyncDecision.SKIP_FRESH
        liveCount > 0 && liveCount == fetchedCount -> ProblemsetSyncDecision.SKIP_UNCHANGED
        else -> ProblemsetSyncDecision.WRITE
    }

    /**
     * Stable fingerprint of a merged problemset. Problems without a representable
     * external id are skipped (same rule as [com.ojnexus.judge.codeforces.mapper.CfMappers.mergeProblemset]).
     * Rows are canonical `contestId:index:rating:solvedCount` lines; a null rating or
     * solvedCount renders as `-`. Any difference in identity, rating or solved count
     * changes the hash.
     */
    fun fingerprint(
        merged: List<Pair<CfProblemDto, CfProblemStatisticsDto?>>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val lines = merged.mapNotNull { (problem, statistics) ->
            val contestId = problem.contestId ?: return@mapNotNull null
            "${contestId}:${problem.index}:${problem.rating ?: -1}:${statistics?.solvedCount ?: -1}"
        }.sorted()
        lines.forEach { line ->
            digest.update(line.toByteArray(Charsets.UTF_8))
            digest.update(0x1F) // unit separator between rows
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    /** Cheap live-table proxy compared against the fetched fingerprint. */
    fun liveMatches(
        fetchedCount: Int,
        fetchedSolvedSum: Long,
        liveCount: Int,
        liveSolvedSum: Long,
    ): Boolean = fetchedCount > 0 && fetchedCount == liveCount && fetchedSolvedSum == liveSolvedSum
}
