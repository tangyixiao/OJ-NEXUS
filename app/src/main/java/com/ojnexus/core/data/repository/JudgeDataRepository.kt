package com.ojnexus.core.data.repository

import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.JudgeProfileEntity
import com.ojnexus.core.database.entity.RemoteProblemEntity
import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.model.JudgeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

fun interface RemoteProblemSearchProvider {
    suspend fun fetch(
        judge: JudgeId,
        query: String,
        limit: Int,
        offset: Int,
    ): List<RemoteProblemEntity>
}

data class JudgeConnectionSnapshot(
    val accounts: Map<JudgeId, JudgeAccountEntity>,
    val profiles: Map<JudgeId, JudgeProfileEntity>,
    val syncStates: Map<JudgeId, SyncStateEntity>,
)

/**
 * Judge-agnostic remote-data facade used by features. Room remains the first read path;
 * an optional provider is consulted only for an explicit non-blank query with no local hit.
 */
class JudgeDataRepository(
    private val database: OjNexusDatabase,
    private val remoteProblemProviders: Map<JudgeId, RemoteProblemSearchProvider> = emptyMap(),
) {
    /** Records a user-requested sync before WorkManager may be delayed by network constraints. */
    suspend fun markSyncQueued(judge: JudgeId, accountId: Long) {
        val current = database.syncStateDao().findByJudge(judge.id)
            ?: SyncStateEntity(judge = judge.id)
        database.syncStateDao().upsert(
            current.copy(
                accountId = accountId,
                state = SyncPhase.QUEUED.name,
                startedAt = null,
                finishedAt = null,
                currentStage = null,
            ),
        )
    }

    fun observeConnections(): Flow<JudgeConnectionSnapshot> = combine(
        database.judgeAccountDao().observeAll(),
        database.judgeProfileDao().observeAll(),
        database.syncStateDao().observeAll(),
    ) { accounts, profiles, states ->
        JudgeConnectionSnapshot(
            accounts = accounts.mapNotNull { account ->
                JudgeId.fromId(account.judge)?.let { it to account }
            }.toMap(),
            profiles = profiles.mapNotNull { profile ->
                JudgeId.fromId(profile.judge)?.let { it to profile }
            }.toMap(),
            syncStates = states.mapNotNull { state ->
                JudgeId.fromId(state.judge)?.let { it to state }
            }.toMap(),
        )
    }

    fun observeContests() = database.contestDao().observeAll()

    suspend fun searchRemoteProblems(
        judge: JudgeId,
        query: String,
        solvedFilter: Int,
        limit: Int,
        offset: Int,
    ): List<RemoteProblemEntity> {
        val normalizedQuery = query.trim()
        val local = searchLocal(judge, normalizedQuery, solvedFilter, limit, offset)
        val provider = remoteProblemProviders[judge]
        if (local.isNotEmpty() || normalizedQuery.isBlank() || provider == null) {
            return local
        }

        val fetched = provider.fetch(judge, normalizedQuery, limit, offset)
        if (fetched.isNotEmpty()) database.remoteProblemDao().upsertAll(fetched)
        return searchLocal(judge, normalizedQuery, solvedFilter, limit, offset)
    }

    private suspend fun searchLocal(
        judge: JudgeId,
        query: String,
        solvedFilter: Int,
        limit: Int,
        offset: Int,
    ): List<RemoteProblemEntity> = database.remoteProblemDao().search(
        judge = judge.id,
        query = query,
        minRating = null,
        maxRating = null,
        solvedFilter = solvedFilter,
        limit = limit,
        offset = offset,
    )
}
