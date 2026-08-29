package com.ojnexus.feature.contests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.database.entity.ContestEntity
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.JudgeProfileEntity
import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.codeforces.CodeforcesSyncRepository
import com.ojnexus.judge.codeforces.mapper.ContestPhase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ContestRow(
    val contestId: Long,
    val name: String,
    val judge: JudgeId,
    val startTimeSeconds: Long?,
    val durationSeconds: Long,
    val phase: ContestPhase,
    val countdownSeconds: Long,
    val rawPhase: String,
)

data class ContestCenterUiState(
    val upcoming: List<ContestRow>,
    val live: List<ContestRow>,
    val recent: List<ContestRow>,
)

class ContestCenterViewModel(
    accountRepository: JudgeAccountRepository,
    syncRepository: CodeforcesSyncRepository,
    private val clock: java.time.Clock,
) : ViewModel() {

    data class ContestEnvelope(
        val account: JudgeAccountEntity?,
        val profile: JudgeProfileEntity?,
        val syncState: SyncStateEntity?,
        val contests: List<ContestEntity>,
    )

    val envelope: StateFlow<ContestEnvelope> = combine(
        accountRepository.observeActive(JudgeId.CODEFORCES),
        syncRepository.observeProfile(JudgeId.CODEFORCES),
        syncRepository.observeSyncStateFlow(JudgeId.CODEFORCES),
        syncRepository.observeContests(JudgeId.CODEFORCES),
    ) { account, profile, syncState, contests ->
        ContestEnvelope(account, profile, syncState, contests)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ContestEnvelope(null, null, null, emptyList()),
    )

    /** Contest rows derived at composition time from the stored raw phases. */
    fun rows(envelope: ContestEnvelope, nowSeconds: Long = currentEpochSecond()): ContestCenterUiState {
        val rows = envelope.contests
            .map { c ->
                val phase = ContestPhase.of(c.phase, c.startTimeSeconds, c.durationSeconds, nowSeconds)
                ContestRow(
                    contestId = c.externalContestId,
                    name = c.name,
                    judge = JudgeId.fromId(c.judge) ?: JudgeId.LOCAL,
                    startTimeSeconds = c.startTimeSeconds,
                    durationSeconds = c.durationSeconds,
                    phase = phase,
                    countdownSeconds = when (phase) {
                        ContestPhase.UPCOMING -> (c.startTimeSeconds?.minus(nowSeconds) ?: 0L)
                            .coerceAtLeast(0L)
                        ContestPhase.LIVE -> c.startTimeSeconds
                            ?.plus(c.durationSeconds)
                            ?.minus(nowSeconds)
                            ?.coerceAtLeast(0L)
                            ?: 0L
                        ContestPhase.ENDED -> 0L
                    },
                    rawPhase = c.phase,
                )
            }
        val upcoming = rows.filter { it.phase == ContestPhase.UPCOMING }.sortedBy { it.startTimeSeconds }
        val live = rows.filter { it.phase == ContestPhase.LIVE }.sortedBy { it.startTimeSeconds }
        val recent = rows.filter { it.phase == ContestPhase.ENDED }
            .sortedByDescending { it.startTimeSeconds }
            .take(20)
        return ContestCenterUiState(upcoming, live, recent)
    }

    fun currentEpochSecond(): Long = clock.instant().epochSecond

    fun syncPhase(account: JudgeAccountEntity?, syncState: SyncStateEntity?): SyncPhase? =
        syncState?.state?.let { raw -> SyncPhase.entries.firstOrNull { it.name == raw } }
            .takeIf { account != null }
}
