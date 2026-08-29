package com.ojnexus.core.data.repository

import androidx.room.withTransaction
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.data.DataError
import com.ojnexus.core.data.DataResult
import com.ojnexus.judge.codeforces.CodeforcesAdapter
import com.ojnexus.judge.codeforces.CodeforcesApiError
import kotlinx.coroutines.flow.Flow
import java.time.Clock

/**
 * Judge account connection lifecycle. No passwords, cookies or API secrets exist anywhere
 * in this flow — a public handle validated through the judge's public API is the only
 * credential concept (docs/CODEFORCES.md).
 *
 * Single-active-per-judge invariant: connect() replaces the previous account for the judge
 * inside one transaction, so two rapid CONNECT calls can never leave two active accounts
 * (mirrors the training-session guard pattern).
 */
class JudgeAccountRepository(
    private val database: OjNexusDatabase,
    private val adapter: CodeforcesAdapter,
    private val clock: Clock,
) {
    private val accountDao = database.judgeAccountDao()

    fun observeAll(): Flow<List<JudgeAccountEntity>> = accountDao.observeAll()

    fun observeActive(judge: JudgeId): Flow<JudgeAccountEntity?> =
        accountDao.observeActiveByJudge(judge.id)

    suspend fun findActive(judge: JudgeId): JudgeAccountEntity? =
        accountDao.findActiveByJudge(judge.id)

    suspend fun findById(id: Long): JudgeAccountEntity? = accountDao.findById(id)

    /** Validation failures surfaced as typed errors; UI maps them to inline strings. */
    sealed class ConnectError : Exception() {
        class HandleEmpty : ConnectError()
        class UserNotFound(val comment: String?) : ConnectError()
        class ApiFailure(val comment: String?) : ConnectError()
        class Network(val wrappedCause: Exception) : ConnectError()
    }

    /**
     * Validates the handle through user.info, adopts the canonical handle from the API and
     * (re)creates the account for the judge atomically. Sync state is reset when the
     * canonical handle changes — the incremental cursor belongs to the old handle.
     */
    suspend fun connect(judge: JudgeId, rawHandle: String): JudgeAccountEntity {
        val trimmed = rawHandle.trim()
        if (trimmed.isEmpty()) throw ConnectError.HandleEmpty()

        val profile = try {
            adapter.fetchProfile(trimmed)
        } catch (e: CodeforcesApiError.UserNotFound) {
            throw ConnectError.UserNotFound(e.rawComment)
        } catch (e: CodeforcesApiError) {
            throw ConnectError.ApiFailure(e.rawComment)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ConnectError.Network(e)
        }
        val canonical = profile.handle.trim()
        require(canonical.isNotEmpty()) { "API returned an empty handle" }

        val now = clock.millis()
        database.withTransaction {
            val existing = accountDao.findActiveByJudge(judge.id)
            val sameHandle = existing?.canonicalHandle == canonical
            if (existing != null && !sameHandle) {
                // Replacing with a different handle: drop the old account and its cursor.
                accountDao.delete(existing.id)
                database.syncStateDao().deleteByJudge(judge.id)
            }
            val accountId = if (existing != null && sameHandle) {
                accountDao.update(existing.copy(handle = trimmed, updatedAt = now))
                existing.id
            } else {
                accountDao.insert(
                    JudgeAccountEntity(
                        judge = judge.id,
                        handle = trimmed,
                        canonicalHandle = canonical,
                        connectedAt = now,
                        updatedAt = now,
                    ),
                )
            }
            if (database.syncStateDao().findByJudge(judge.id) == null) {
                database.syncStateDao().upsert(SyncStateEntity(judge = judge.id))
            }
            accountId
        }
        return accountDao.findActiveByJudge(judge.id)
            ?: throw ConnectError.ApiFailure("account disappeared after connect")
    }

    /**
     * Disconnect always removes the account + sync state. Cached remote data (profile
     * snapshot, rating history, contests, remote catalog) is removed only when the user
     * opts in — imported attempts and the user's own training history are NEVER touched.
     */
    suspend fun disconnect(accountId: Long, removeCachedRemoteData: Boolean) {
        database.withTransaction {
            val account = accountDao.findById(accountId) ?: return@withTransaction
            accountDao.delete(account.id)
            database.syncStateDao().deleteByJudge(account.judge)
            if (removeCachedRemoteData) {
                database.judgeProfileDao().deleteByJudge(account.judge)
                database.ratingChangeDao().deleteByJudge(account.judge)
                database.contestDao().deleteByJudge(account.judge)
                database.remoteProblemDao().deleteByJudge(account.judge)
            }
        }
    }

    companion object {
        fun isAccountEnabled(account: JudgeAccountEntity?): Boolean =
            account != null && account.enabled
    }
}

/** Small typed helper for callers that prefer DataResult-style handling of connect. */
fun JudgeAccountRepository.ConnectError.toDataError(): DataError = when (this) {
    is JudgeAccountRepository.ConnectError.HandleEmpty -> DataError.NotFound("handle")
    is JudgeAccountRepository.ConnectError.UserNotFound -> DataError.NotFound(comment ?: "handle")
    else -> DataError.Storage(message ?: "connect failed")
}
