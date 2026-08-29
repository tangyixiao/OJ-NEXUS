package com.ojnexus.core.data.repository

import androidx.room.withTransaction
import com.ojnexus.core.data.DataError
import com.ojnexus.core.data.DataResult
import com.ojnexus.core.data.dataResult
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.dao.SessionDao
import com.ojnexus.core.database.dao.TaskDao
import com.ojnexus.core.database.entity.TrainingSessionEntity
import com.ojnexus.core.database.entity.TrainingSessionProblemEntity
import com.ojnexus.core.database.entity.TrainingTaskEntity
import com.ojnexus.core.database.mapper.toDomain
import com.ojnexus.core.domain.SessionClock
import com.ojnexus.core.domain.SessionStateMachine
import com.ojnexus.core.model.SessionEvent
import com.ojnexus.core.model.SessionState
import com.ojnexus.core.model.SessionProblem
import com.ojnexus.core.model.TrainingSession
import com.ojnexus.core.model.TrainingTask
import com.ojnexus.core.model.TrainingType
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * TODAY tasks and training sessions. Session lifecycle changes must go through
 * [applySessionEvent] — the pure [SessionStateMachine] decides legality, this class
 * persists timestamps. Elapsed time is always derived from persisted wall-clock
 * snapshots (never ticked), so sessions survive backgrounding and process death.
 */
class TrainingRepository(
    private val database: OjNexusDatabase,
    private val clock: Clock,
) {
    private val taskDao: TaskDao = database.taskDao()
    private val sessionDao: SessionDao = database.sessionDao()

    // --- Today tasks ---

    fun observeTasks(dateEpochDay: Long): Flow<List<TrainingTask>> =
        taskDao.observeByDate(dateEpochDay).map { rows ->
            rows.map { it.task.toDomain(problemTitle = it.problem?.title) }
        }

    suspend fun addTask(
        dateEpochDay: Long,
        type: com.ojnexus.core.model.TaskType,
        problemId: Long?,
        title: String?,
        priority: Int,
    ): DataResult<Long> {
        if (problemId != null && database.problemDao().findById(problemId) == null) {
            return DataResult.Failure(DataError.NotFound("problem $problemId"))
        }
        return dataResult {
            taskDao.insert(
                TrainingTaskEntity(
                    dateEpochDay = dateEpochDay,
                    type = type.name,
                    problemId = problemId,
                    title = title,
                    priority = priority,
                    createdAt = clock.millis(),
                ),
            )
        }
    }

    suspend fun setTaskCompleted(taskId: Long, completed: Boolean) {
        taskDao.updateCompleted(taskId, completed)
    }

    suspend fun deleteTask(taskId: Long) {
        taskDao.delete(taskId)
    }

    suspend fun clearCompletedTasks(dateEpochDay: Long) {
        taskDao.deleteCompleted(dateEpochDay)
    }

    // --- Sessions ---

    fun observeActiveSession(): Flow<TrainingSession?> =
        sessionDao.observeActive().map { rows -> rows.firstOrNull()?.toDomain() }

    fun observeSessionProblemCount(sessionId: Long): Flow<Int> =
        sessionDao.observeSessionProblemCount(sessionId)

    fun observeSession(id: Long): Flow<TrainingSession?> =
        sessionDao.observeById(id).map { it?.toDomain() }

    fun observeHistory(limit: Int = 20): Flow<List<TrainingSession>> =
        sessionDao.observeHistory(limit).map { rows -> rows.map { it.session.toDomain() } }

    /**
     * Creates a session, attaches problems and starts it. Returns the new session id.
     * Only one live session is allowed at a time.
     */
    suspend fun createAndStartSession(
        type: TrainingType,
        targetDurationMin: Int?,
        targetTag: String?,
        problemIds: List<Long>,
    ): DataResult<Long> {
        if (sessionDao.countActive() > 0) {
            return DataResult.Failure(DataError.Storage("A session is already active"))
        }
        for (problemId in problemIds) {
            if (database.problemDao().findById(problemId) == null) {
                return DataResult.Failure(DataError.NotFound("problem $problemId"))
            }
        }
        return dataResult {
            database.withTransaction {
                val sessionId = sessionDao.insert(
                    TrainingSessionEntity(
                        type = type.name,
                        state = SessionState.PLANNED.name,
                        startedAt = clock.millis(),
                        targetDurationMin = targetDurationMin,
                        targetTag = targetTag?.takeIf { it.isNotBlank() },
                        dayIndex = clock.dayIndex(),
                    ),
                )
                problemIds.forEachIndexed { index, problemId ->
                    sessionDao.insertSessionProblem(
                        TrainingSessionProblemEntity(sessionId = sessionId, problemId = problemId),
                    )
                }
                applyEventLocked(sessionId, SessionEvent.START)
                sessionId
            }
        }
    }

    suspend fun pauseSession(sessionId: Long): DataResult<Unit> = applyEvent(sessionId, SessionEvent.PAUSE)

    suspend fun resumeSession(sessionId: Long): DataResult<Unit> = applyEvent(sessionId, SessionEvent.RESUME)

    suspend fun finishSession(sessionId: Long): DataResult<Unit> = applyEvent(sessionId, SessionEvent.FINISH)

    suspend fun cancelSession(sessionId: Long): DataResult<Unit> = applyEvent(sessionId, SessionEvent.CANCEL)

    /** In-session problem list with live solved/attempt counts for a finished summary. */
    suspend fun sessionProblems(sessionId: Long): List<SessionProblem> {
        val session = sessionDao.findById(sessionId) ?: return emptyList()
        val links = sessionDao.sessionProblems(sessionId)
        val end = session.finishedAt ?: clock.millis()
        return links.map { link ->
            val problem = database.problemDao().findById(link.problemId)
            val attempts = database.attemptDao().findByProblem(link.problemId)
                .filter { it.timestamp in session.startedAt..end }
            SessionProblem(
                problemId = link.problemId,
                title = problem?.title ?: "?",
                difficulty = problem?.difficulty,
                solved = attempts.any { it.verdict == "AC" },
                attempts = attempts.size,
            )
        }
    }

    /** Elapsed active time in ms for a session, derived from persisted snapshots. */
    fun elapsedMs(session: TrainingSession, now: Long): Long = SessionClock.elapsedMs(
        startedAt = session.startedAt,
        totalPausedMs = session.totalPausedMs,
        pausedAt = session.pausedAt,
        finishedAt = session.finishedAt,
        now = now,
    )

    private suspend fun applyEvent(sessionId: Long, event: SessionEvent): DataResult<Unit> {
        val session = sessionDao.findById(sessionId)
            ?: return DataResult.Failure(DataError.NotFound("session $sessionId"))
        return dataResult {
            database.withTransaction {
                applyEventLocked(sessionId, event)
            }
        }
    }

    /** Caller must hold a database transaction. */
    private suspend fun applyEventLocked(sessionId: Long, event: SessionEvent) {
        val session = sessionDao.findById(sessionId) ?: return
        val nextState = SessionStateMachine.transition(
            current = SessionState.entries.firstOrNull { it.name == session.state } ?: return,
            event = event,
        )
        val now = clock.millis()
        sessionDao.update(
            when (event) {
                SessionEvent.START -> session.copy(state = nextState.name, startedAt = now)
                SessionEvent.PAUSE -> session.copy(state = nextState.name, pausedAt = now)
                SessionEvent.RESUME -> session.copy(
                    state = nextState.name,
                    pausedAt = null,
                    totalPausedMs = session.totalPausedMs + (now - (session.pausedAt ?: now)),
                )
                SessionEvent.FINISH -> {
                    // Finish while paused: close the open pause window first.
                    val pausedExtra = session.pausedAt?.let { now - it } ?: 0
                    session.copy(
                        state = nextState.name,
                        pausedAt = null,
                        totalPausedMs = session.totalPausedMs + pausedExtra,
                        finishedAt = now,
                    )
                }
                SessionEvent.CANCEL -> session.copy(state = nextState.name, pausedAt = null)
            },
        )
    }
}
