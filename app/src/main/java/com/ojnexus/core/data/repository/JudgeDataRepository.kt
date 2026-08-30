package com.ojnexus.core.data.repository

import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.JudgeProfileEntity
import com.ojnexus.core.database.entity.RemoteProblemEntity
import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.model.JudgeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class JudgeConnectionSnapshot(
    val accounts: Map<JudgeId, JudgeAccountEntity>,
    val profiles: Map<JudgeId, JudgeProfileEntity>,
    val syncStates: Map<JudgeId, SyncStateEntity>,
)

/** Judge-agnostic Room read facade used by features; it never initiates network work. */
class JudgeDataRepository(
    private val database: OjNexusDatabase,
) {
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
    ): List<RemoteProblemEntity> = database.remoteProblemDao().search(
        judge = judge.id,
        query = query.trim(),
        minRating = null,
        maxRating = null,
        solvedFilter = solvedFilter,
        limit = limit,
        offset = offset,
    )
}
