package com.ojnexus.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ojnexus.R
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusStatus
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.JudgeCapability

data class ConnectorCenterSummary(
    val connectedCount: Int,
    val supportedCount: Int,
    val eligibleSyncCount: Int,
    val rows: List<ConnectorCenterRow>,
)

data class ConnectorCenterRow(
    val judge: JudgeId,
    val handle: String?,
    val connected: Boolean,
    val phase: SyncPhase?,
    val currentStage: String?,
    val completedReceiptCount: Int,
    val totalReceiptCount: Int,
    val lastSuccessfulSyncAt: Long?,
    val canSync: Boolean,
) {
    val isComplete: Boolean
        get() = connected && totalReceiptCount > 0 && completedReceiptCount == totalReceiptCount
}

fun deriveConnectorCenter(connections: List<JudgeConnectionUi>): ConnectorCenterSummary {
    val rows = connections
        .sortedBy { it.judge.ordinal }
        .map { connection ->
            val account = connection.account
            val sync = connection.syncState
            val receipts = syncReceiptItems(connection.capabilities, sync)
            ConnectorCenterRow(
                judge = connection.judge,
                handle = account?.canonicalHandle,
                connected = account != null,
                phase = sync?.state?.let { state -> SyncPhase.entries.firstOrNull { it.name == state } },
                currentStage = sync?.currentStage,
                completedReceiptCount = receipts.count { it.syncedAt != null },
                totalReceiptCount = receipts.size,
                lastSuccessfulSyncAt = sync?.lastSuccessfulSyncAt,
                canSync = account != null &&
                    account.enabled &&
                    JudgeCapability.BACKGROUND_SYNC in connection.capabilities,
            )
        }
    return ConnectorCenterSummary(
        connectedCount = rows.count { it.connected },
        supportedCount = rows.size,
        eligibleSyncCount = rows.count { it.canSync },
        rows = rows,
    )
}

internal fun eligibleConnectorSyncRows(
    connections: List<JudgeConnectionUi>,
): List<ConnectorCenterRow> = deriveConnectorCenter(connections).rows.filter { it.canSync }

@Composable
internal fun ConnectorCenterSection(
    summary: ConnectorCenterSummary,
    syncAllInFlight: Boolean,
    onSyncAll: () -> Unit,
) {
    val colors = NexusTheme.colors
    NexusSection(
        label = stringResource(R.string.settings_connector_center_title),
        trailing = {
            NexusTag(
                text = stringResource(
                    R.string.settings_connector_count,
                    summary.connectedCount,
                    summary.supportedCount,
                ),
                tone = if (summary.connectedCount > 0) NexusTone.Accent else NexusTone.Neutral,
                selected = true,
            )
        },
    ) {
        if (summary.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_connector_empty),
                style = NexusTheme.typography.dataSmall,
                color = colors.textTertiary,
            )
        } else {
            summary.rows.forEachIndexed { index, row ->
                if (index > 0) Spacer(Modifier.height(NexusSpacing.xs))
                ConnectorCenterRow(row)
            }
        }
        Spacer(Modifier.height(NexusSpacing.sm))
        ConnectorCenterAction(
            label = stringResource(
                if (syncAllInFlight) {
                    R.string.settings_connector_syncing_all
                } else {
                    R.string.settings_connector_sync_all
                },
            ),
            enabled = summary.eligibleSyncCount > 0 && !syncAllInFlight,
            onClick = onSyncAll,
        )
    }
}

@Composable
private fun ConnectorCenterRow(row: ConnectorCenterRow) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.judge.displayName, style = NexusTheme.typography.data, color = colors.textPrimary)
            Text(
                text = row.handle ?: stringResource(R.string.settings_connector_disconnected),
                style = NexusTheme.typography.dataSmall,
                color = if (row.connected) colors.accent else colors.textTertiary,
            )
            Text(
                text = stringResource(
                    R.string.settings_connector_receipts,
                    row.completedReceiptCount,
                    row.totalReceiptCount,
                ),
                style = NexusTheme.typography.dataSmall,
                color = colors.textTertiary,
            )
            Text(
                text = stringResource(
                    R.string.settings_connector_last_sync,
                    connectorSyncAge(row.lastSuccessfulSyncAt),
                ),
                style = NexusTheme.typography.dataSmall,
                color = colors.textTertiary,
            )
            row.currentStage?.let { stage ->
                Text(
                    text = stringResource(R.string.settings_connector_stage, stage),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.accent,
                )
            }
        }
        NexusStatus(
            label = connectorStatusLabel(row),
            tone = connectorStatusTone(row),
        )
    }
}

@Composable
private fun connectorStatusLabel(row: ConnectorCenterRow): String = when {
    !row.connected -> stringResource(R.string.settings_connector_disconnected)
    row.phase == SyncPhase.QUEUED -> stringResource(R.string.settings_connector_queued)
    row.phase == SyncPhase.SYNCING -> stringResource(R.string.settings_connector_syncing)
    row.phase == SyncPhase.PARTIAL -> stringResource(R.string.settings_connector_partial)
    row.phase == SyncPhase.ERROR -> stringResource(R.string.settings_connector_failed)
    else -> stringResource(R.string.settings_connector_connected)
}

private fun connectorStatusTone(row: ConnectorCenterRow): NexusTone = when {
    !row.connected -> NexusTone.Neutral
    row.phase == SyncPhase.PARTIAL || row.phase == SyncPhase.ERROR -> NexusTone.Warning
    row.phase == SyncPhase.QUEUED || row.phase == SyncPhase.SYNCING -> NexusTone.Accent
    else -> NexusTone.Success
}

@Composable
private fun connectorSyncAge(timestamp: Long?): String = when (
    val age = formatSyncAge(System.currentTimeMillis(), timestamp)
) {
    SyncAge.NEVER -> stringResource(R.string.settings_sync_never)
    SyncAge.JUST_NOW -> stringResource(R.string.settings_sync_just_now)
    is SyncAge.MINUTES_AGO -> stringResource(R.string.settings_sync_minutes_ago, age.value)
    is SyncAge.HOURS_AGO -> stringResource(R.string.settings_sync_hours_ago, age.value)
    is SyncAge.DAYS_AGO -> stringResource(R.string.settings_sync_days_ago, age.value)
}

@Composable
private fun ConnectorCenterAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = NexusTheme.colors
    val contentDescription = stringResource(R.string.settings_connector_sync_all_cd)
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(colors.accentContainer, NexusRadius.sm)
            .border(NexusSize.dividerThickness, colors.accent, NexusRadius.sm)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = NexusSpacing.sm, vertical = NexusSpacing.xs),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, style = NexusTheme.typography.data, color = colors.accent)
    }
}
