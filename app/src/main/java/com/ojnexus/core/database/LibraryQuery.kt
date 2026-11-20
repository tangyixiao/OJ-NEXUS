package com.ojnexus.core.database

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.ojnexus.core.model.ProblemStatus

/**
 * Core-level library query: the SQL-translatable form of the feature-layer
 * `ProblemFilter` (core/database must not depend on feature). Semantics are
 * the frozen phase-136 problem contract (`contracts/problems/v1/`):
 * AND-combined optional constraints and a sort that always ends in the
 * stable code-point tie-break.
 */
data class LibraryQuery(
    val search: String = "",
    val status: ProblemStatus? = null,
    val judge: String? = null,
    val favoriteOnly: Boolean = false,
    val tag: String? = null,
    val difficultyMin: Int? = null,
    val difficultyMax: Int? = null,
    val sort: LibrarySort = LibrarySort.UPDATED,
) {
    val isDefault: Boolean
        get() = this == LibraryQuery()
}

enum class LibrarySort {
    UPDATED,
    DIFFICULTY,
    TITLE,
    ATTEMPTS,
}

/**
 * Builds the bounded library SQL. The WHERE reproduces the phase-136
 * contract exactly (derived status, inclusive difficulty bounds excluding
 * unknown difficulty, exact tag membership, simple-lowercase substring
 * search over `title_lower` and `external_id` with literal wildcard
 * escaping); every ORDER BY ends with the stable code-point tie-break
 * (`judge, external_id` — SQLite BINARY collation on UTF-8 is code-point
 * order). [query.search] is trimmed and simple-lowered here, matching the
 * contract's search semantics.
 */
object LibraryQueryBuilder {

    private const val DERIVED_STATUS =
        "(CASE WHEN EXISTS(SELECT 1 FROM reviews r WHERE r.problem_id = problems.id) THEN 'REVIEW' " +
            "WHEN problems.solved THEN 'SOLVED' " +
            "WHEN problems.attempt_count > 0 THEN 'ATTEMPTED' " +
            "ELSE 'UNSOLVED' END)"

    private const val TAG_EXISTS =
        "(EXISTS(SELECT 1 FROM problem_tag_cross_ref c JOIN problem_tags t ON t.id = c.tag_id " +
            "WHERE c.problem_id = problems.id AND t.name = ?))"

    fun pagingLibrary(query: LibraryQuery): SupportSQLiteQuery =
        SimpleSQLiteQuery(buildSelect(query, ordered = true), bindArgs(query))

    fun countLibrary(query: LibraryQuery): SupportSQLiteQuery =
        SimpleSQLiteQuery("SELECT COUNT(*) FROM problems${whereClauseSql(query)}", bindArgs(query))

    fun summaryLibrary(query: LibraryQuery): SupportSQLiteQuery = SimpleSQLiteQuery(
        "SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN problems.solved THEN 1 ELSE 0 END), 0) AS solved, " +
            "COALESCE(SUM(CASE WHEN EXISTS(SELECT 1 FROM reviews r WHERE r.problem_id = problems.id) " +
            "THEN 1 ELSE 0 END), 0) AS review, " +
            "COALESCE(SUM(CASE WHEN problems.favorite THEN 1 ELSE 0 END), 0) AS favorites " +
            "FROM problems" + whereClauseSql(query),
        bindArgs(query),
    )

    private fun buildSelect(query: LibraryQuery, ordered: Boolean): String {
        val orderBy = when (query.sort) {
            LibrarySort.UPDATED -> "problems.updated_at DESC"
            LibrarySort.DIFFICULTY -> "(problems.difficulty IS NULL) ASC, problems.difficulty ASC"
            LibrarySort.TITLE -> "problems.title_lower ASC"
            LibrarySort.ATTEMPTS -> "problems.attempt_count DESC"
        }
        // The stable tie-break: code-point order of the key string "judge/externalId"
        // (BINARY collation on UTF-8 is code-point order; secondary keys break ties).
        val order = orderBy + ", problems.judge ASC, problems.external_id ASC, problems.id ASC"
        return "SELECT *, EXISTS(SELECT 1 FROM reviews WHERE reviews.problem_id = problems.id) " +
            "AS in_review FROM problems" + whereClauseSql(query) + " ORDER BY " + order
    }

    /** The contract's frozen ASCII trim set (`check.py` ASCII_WHITESPACE): Kotlin's
     * trim() would also strip Unicode whitespace like NBSP, which vector 026 pins
     * as NOT trimmed.
     */
    private fun contractTrim(value: String): String =
        value.trim { it == ' ' || it == '\t' || it == '\n' || it == '\r' || it == '\u000B' || it == '\u000C' }

    private fun whereClauseSql(query: LibraryQuery): String {
        val clauses = mutableListOf<String>()
        val search = contractTrim(query.search).lowercase()
        if (search.isNotEmpty()) {
            // lower(external_id) mirrors the in-memory `externalId.lowercase().contains()`
            // for ASCII judge identifiers; the title uses the stored Kotlin-lowered column
            // because SQL lower() is ASCII-only.
            clauses += "(problems.title_lower LIKE ? ESCAPE '\\' " +
                "OR lower(problems.external_id) LIKE ? ESCAPE '\\')"
        }
        query.status?.let {
            clauses += "$DERIVED_STATUS = ?"
        }
        query.judge?.let {
            clauses += "problems.judge = ?"
        }
        if (query.favoriteOnly) {
            clauses += "problems.favorite = 1"
        }
        query.tag?.let {
            clauses += TAG_EXISTS
        }
        if (query.difficultyMin != null || query.difficultyMax != null) {
            clauses += "problems.difficulty IS NOT NULL"
            query.difficultyMin?.let { clauses += "problems.difficulty >= ?" }
            query.difficultyMax?.let { clauses += "problems.difficulty <= ?" }
        }
        return if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")
    }

    private fun bindArgs(query: LibraryQuery): Array<Any?> {
        val args = mutableListOf<Any?>()
        // Same contract trim as the clause builder: Kotlin trim() would also strip
        // Unicode whitespace like U+3000, desyncing the WHERE from its bind args.
        val search = contractTrim(query.search).lowercase()
        if (search.isNotEmpty()) {
            val escaped = escapeLike(search)
            args += "%$escaped%"
            args += "%$escaped%"
        }
        query.status?.let { args += it.name }
        query.judge?.let { args += it }
        query.tag?.let { args += it }
        query.difficultyMin?.let { args += it }
        query.difficultyMax?.let { args += it }
        return args.toTypedArray()
    }

    /** Literal substring semantics: the query's own %, _ and escape chars never act as wildcards. */
    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
