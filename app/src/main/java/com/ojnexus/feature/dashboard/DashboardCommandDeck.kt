package com.ojnexus.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ojnexus.R
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.database.entity.ContestEntity
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.model.ReviewQueueItem

data class DashboardSummary(
    val dueReviews: Int,
    val connectedJudges: Int,
    val solvedThisWeek: Int,
    val nextContestRemainingSeconds: Long?,
)

data class DashboardCountdown(
    val days: Long,
    val hours: Long,
    val minutes: Long,
)

fun deriveDashboardSummary(
    reviews: List<ReviewQueueItem>,
    todayEpochDay: Long,
    enabledJudgeCount: Int,
    solvedThisWeek: Int,
    contests: List<ContestEntity>,
    nowSeconds: Long,
): DashboardSummary {
    val nextContestStart = contests.asSequence()
        .mapNotNull { it.startTimeSeconds }
        .filter { it > nowSeconds }
        .minOrNull()

    return DashboardSummary(
        dueReviews = reviews.count { it.dueDayIndex <= todayEpochDay },
        connectedJudges = enabledJudgeCount.coerceAtLeast(0),
        solvedThisWeek = solvedThisWeek.coerceAtLeast(0),
        nextContestRemainingSeconds = nextContestStart?.minus(nowSeconds)?.coerceAtLeast(0L),
    )
}

fun dashboardCountdown(remainingSeconds: Long?): DashboardCountdown? {
    val seconds = remainingSeconds?.coerceAtLeast(0L) ?: return null
    return DashboardCountdown(
        days = seconds / SECONDS_PER_DAY,
        hours = (seconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR,
        minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE,
    )
}

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE
private const val SECONDS_PER_DAY = 24L * SECONDS_PER_HOUR

enum class DashboardSurfaceTarget {
    TRAINING,
    REVIEW,
    CONTESTS,
    SETTINGS,
    NONE,
}

enum class DashboardSurfaceMessage {
    NO_ACTIVE_COMMAND,
    NO_NEXT_COMMAND,
    LOCAL_READY,
    OJ_LINKED,
    SYNC_ATTENTION,
}

sealed interface DashboardSurfaceValue {
    data class Data(val text: String) : DashboardSurfaceValue
    data class Message(val message: DashboardSurfaceMessage) : DashboardSurfaceValue
}

data class DashboardSurfaceCell(
    val value: DashboardSurfaceValue,
    val target: DashboardSurfaceTarget,
)

data class DashboardCommandSurface(
    val now: DashboardSurfaceCell,
    val next: DashboardSurfaceCell,
    val signal: DashboardSurfaceCell,
)

fun deriveDashboardCommandSurface(state: DashboardUiState): DashboardCommandSurface {
    val currentTask = state.todayTasks.firstOrNull { !it.completed }
    val now = if (currentTask == null) {
        DashboardSurfaceCell(
            value = DashboardSurfaceValue.Message(DashboardSurfaceMessage.NO_ACTIVE_COMMAND),
            target = DashboardSurfaceTarget.TRAINING,
        )
    } else {
        DashboardSurfaceCell(
            value = DashboardSurfaceValue.Data(
                currentTask.problemTitle ?: currentTask.title ?: currentTask.type.name,
            ),
            target = DashboardSurfaceTarget.TRAINING,
        )
    }

    val next = when {
        state.nextReview != null -> DashboardSurfaceCell(
            value = DashboardSurfaceValue.Data(state.nextReview.problemTitle),
            target = DashboardSurfaceTarget.REVIEW,
        )
        state.nextContest != null -> DashboardSurfaceCell(
            value = DashboardSurfaceValue.Data(state.nextContest.name),
            target = DashboardSurfaceTarget.CONTESTS,
        )
        else -> DashboardSurfaceCell(
            value = DashboardSurfaceValue.Message(DashboardSurfaceMessage.NO_NEXT_COMMAND),
            target = DashboardSurfaceTarget.NONE,
        )
    }

    val hasSyncAttention = state.judgeConnections.any { connection ->
        connection.syncState?.state == SyncPhase.PARTIAL.name ||
            connection.syncState?.state == SyncPhase.ERROR.name
    }
    val signal = when {
        hasSyncAttention -> DashboardSurfaceCell(
            value = DashboardSurfaceValue.Message(DashboardSurfaceMessage.SYNC_ATTENTION),
            target = DashboardSurfaceTarget.SETTINGS,
        )
        state.judgeConnections.isNotEmpty() -> DashboardSurfaceCell(
            value = DashboardSurfaceValue.Message(DashboardSurfaceMessage.OJ_LINKED),
            target = DashboardSurfaceTarget.SETTINGS,
        )
        else -> DashboardSurfaceCell(
            value = DashboardSurfaceValue.Message(DashboardSurfaceMessage.LOCAL_READY),
            target = DashboardSurfaceTarget.SETTINGS,
        )
    }
    return DashboardCommandSurface(now = now, next = next, signal = signal)
}

@Composable
fun DashboardCommandSurfaceSection(
    surface: DashboardCommandSurface,
    onTarget: (DashboardSurfaceTarget) -> Unit,
) {
    NexusSection(label = stringResource(R.string.dash_surface_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
        ) {
            DashboardSurfaceCell(
                slot = stringResource(R.string.dash_surface_now),
                cell = surface.now,
                modifier = Modifier.weight(1f),
                onTarget = onTarget,
            )
            DashboardSurfaceCell(
                slot = stringResource(R.string.dash_surface_next),
                cell = surface.next,
                modifier = Modifier.weight(1f),
                onTarget = onTarget,
            )
            DashboardSurfaceCell(
                slot = stringResource(R.string.dash_surface_signal),
                cell = surface.signal,
                modifier = Modifier.weight(1f),
                onTarget = onTarget,
            )
        }
    }
}

@Composable
private fun DashboardSurfaceCell(
    slot: String,
    cell: DashboardSurfaceCell,
    modifier: Modifier,
    onTarget: (DashboardSurfaceTarget) -> Unit,
) {
    val colors = NexusTheme.colors
    val value = dashboardSurfaceValue(cell.value)
    val actionable = cell.target != DashboardSurfaceTarget.NONE
    val cellDescription = stringResource(R.string.dash_surface_cell_cd, slot, value)
    Column(
        modifier = modifier
            .height(SurfaceCellHeight)
            .background(colors.surface, NexusRadius.sm)
            .border(
                NexusSize.dividerThickness,
                if (actionable) colors.borderStrong else colors.border,
                NexusRadius.sm,
            )
            .clickable(
                enabled = actionable,
                role = Role.Button,
                onClick = { onTarget(cell.target) },
            )
            .semantics {
                contentDescription = cellDescription
            }
            .padding(horizontal = NexusSpacing.xs, vertical = NexusSpacing.xs),
    ) {
        Text(slot, style = NexusTheme.typography.sectionLabel, color = colors.textTertiary)
        Text(
            text = value,
            style = NexusTheme.typography.dataSmall,
            color = if (actionable) colors.accent else colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun dashboardSurfaceValue(value: DashboardSurfaceValue): String = when (value) {
    is DashboardSurfaceValue.Data -> value.text
    is DashboardSurfaceValue.Message -> when (value.message) {
        DashboardSurfaceMessage.NO_ACTIVE_COMMAND -> stringResource(R.string.dash_surface_no_active)
        DashboardSurfaceMessage.NO_NEXT_COMMAND -> stringResource(R.string.dash_surface_no_next)
        DashboardSurfaceMessage.LOCAL_READY -> stringResource(R.string.dash_surface_local_ready)
        DashboardSurfaceMessage.OJ_LINKED -> stringResource(R.string.dash_surface_oj_linked)
        DashboardSurfaceMessage.SYNC_ATTENTION -> stringResource(R.string.dash_surface_sync_attention)
    }
}

private val SurfaceCellHeight = 84.dp
