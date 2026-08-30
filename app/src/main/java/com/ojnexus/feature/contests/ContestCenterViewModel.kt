package com.ojnexus.feature.contests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.repository.JudgeConnectionSnapshot
import com.ojnexus.core.data.repository.JudgeDataRepository
import com.ojnexus.core.database.entity.ContestEntity
import com.ojnexus.core.domain.ContestTimeState
import com.ojnexus.core.domain.ContestTimeStateCalculator
import com.ojnexus.core.model.JudgeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ContestRow(
    val contestId: String,
    val name: String,
    val judge: JudgeId,
    val startTimeSeconds: Long?,
    val durationSeconds: Long,
    val phase: ContestTimeState,
    val countdownSeconds: Long,
    val rawPhase: String,
)

data class ContestCenterUiState(
    val upcoming: List<ContestRow>,
    val live: List<ContestRow>,
    val recent: List<ContestRow>,
)

class ContestCenterViewModel(
    dataRepository: JudgeDataRepository,
    private val clock: java.time.Clock,
) : ViewModel() {
    private val judgeFilter = MutableStateFlow<JudgeId?>(null)

    data class ContestEnvelope(
        val connections: JudgeConnectionSnapshot,
        val contests: List<ContestEntity>,
        val selectedJudge: JudgeId?,
    )

    val envelope: StateFlow<ContestEnvelope> = combine(
        dataRepository.observeConnections(),
        dataRepository.observeContests(),
        judgeFilter,
    ) { connections, contests, selected ->
        ContestEnvelope(connections, contests, selected)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ContestEnvelope(JudgeConnectionSnapshot(emptyMap(), emptyMap(), emptyMap()), emptyList(), null),
    )

    fun setJudgeFilter(judge: JudgeId?) {
        judgeFilter.value = judge
    }

    fun rows(envelope: ContestEnvelope, nowSeconds: Long = currentEpochSecond()): ContestCenterUiState {
        val rows = envelope.contests.asSequence()
            .filter { envelope.selectedJudge == null || it.judge == envelope.selectedJudge.id }
            .map { contest -> contest.toRow(nowSeconds) }
            .toList()
        return ContestCenterUiState(
            upcoming = rows.filter { it.phase == ContestTimeState.UPCOMING }.sortedBy { it.startTimeSeconds },
            live = rows.filter { it.phase == ContestTimeState.LIVE }.sortedBy { it.startTimeSeconds },
            recent = rows.filter { it.phase == ContestTimeState.ENDED }
                .sortedByDescending { it.startTimeSeconds }
                .take(20),
        )
    }

    fun currentEpochSecond(): Long = clock.instant().epochSecond

    private fun ContestEntity.toRow(nowSeconds: Long): ContestRow {
        val start = startTimeSeconds
        val phase = if (start == null) {
            ContestTimeState.ENDED
        } else {
            ContestTimeStateCalculator.calculate(start, durationSeconds, nowSeconds)
        }
        return ContestRow(
            contestId = externalContestId,
            name = name,
            judge = JudgeId.fromId(judge) ?: JudgeId.LOCAL,
            startTimeSeconds = start,
            durationSeconds = durationSeconds,
            phase = phase,
            countdownSeconds = when (phase) {
                ContestTimeState.UPCOMING -> (start?.minus(nowSeconds) ?: 0).coerceAtLeast(0)
                ContestTimeState.LIVE -> start?.plus(durationSeconds)?.minus(nowSeconds)?.coerceAtLeast(0) ?: 0
                ContestTimeState.ENDED -> 0
            },
            rawPhase = this.phase,
        )
    }
}
