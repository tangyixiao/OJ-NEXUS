package com.ojnexus.core.data.repository

import com.ojnexus.core.database.dao.WorkspaceDraftDao
import com.ojnexus.core.database.entity.WorkspaceDraftEntity
import com.ojnexus.core.model.JudgeId
import java.time.Clock

data class WorkspaceDraft(
    val code: String,
    val input: String,
    val language: String,
    val o2: Boolean,
)

interface WorkspaceDraftRepository {
    suspend fun find(judge: JudgeId, pid: String): WorkspaceDraft?

    suspend fun save(judge: JudgeId, pid: String, draft: WorkspaceDraft)
}

class RoomWorkspaceDraftRepository(
    private val dao: WorkspaceDraftDao,
    private val clock: Clock,
) : WorkspaceDraftRepository {
    override suspend fun find(judge: JudgeId, pid: String): WorkspaceDraft? =
        dao.findByKey(judge.id, pid)?.let { entity ->
            WorkspaceDraft(
                code = entity.code,
                input = entity.input,
                language = entity.language,
                o2 = entity.o2,
            )
        }

    override suspend fun save(judge: JudgeId, pid: String, draft: WorkspaceDraft) {
        dao.upsert(
            WorkspaceDraftEntity(
                judge = judge.id,
                pid = pid,
                code = draft.code,
                input = draft.input,
                language = draft.language,
                o2 = draft.o2,
                updatedAt = clock.millis(),
            ),
        )
    }
}
