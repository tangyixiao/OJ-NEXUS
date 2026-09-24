package com.ojnexus.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.ojnexus.R
import com.ojnexus.core.data.sync.SyncRetryRequest
import com.ojnexus.core.data.sync.SyncStage
import com.ojnexus.core.database.dao.SyncOperationWithModules
import com.ojnexus.core.database.entity.SyncModuleStatus
import com.ojnexus.core.database.entity.SyncOperationStatus
import com.ojnexus.core.database.entity.SyncOperationModuleEntity
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.JudgeCapability

data class SyncOperationHistoryEntry(
    val operationId: Long,
    val judge: String,
    val accountId: Long,
    val dataGeneration: String,
    val status: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val currentStage: String?,
    val lastErrorType: String?,
    val modules: List<SyncOperationModuleEntity>,
)

enum class SyncRetryKind {
    FULL_SYNC,
    FAILED_STAGE,
}

data class SyncRetryAction(
    val kind: SyncRetryKind,
    val request: SyncRetryRequest,
)

fun projectSyncOperationHistory(
    rows: List<SyncOperationWithModules>,
    limit: Int = 5,
): List<SyncOperationHistoryEntry> = rows
    .sortedWith(compareByDescending<SyncOperationWithModules> { it.operation.startedAt }.thenByDescending { it.operation.id })
    .take(limit.coerceAtLeast(0))
    .map { row ->
        SyncOperationHistoryEntry(
            operationId = row.operation.id,
            judge = row.operation.judge,
            accountId = row.operation.accountId,
            dataGeneration = row.operation.dataGeneration,
            status = row.operation.status,
            startedAt = row.operation.startedAt,
            finishedAt = row.operation.finishedAt,
            currentStage = row.operation.currentStage,
            lastErrorType = row.operation.lastErrorType,
            modules = row.modules.sortedBy { it.id },
        )
    }

fun syncRetryAction(
    entry: SyncOperationHistoryEntry,
    module: SyncOperationModuleEntity,
    judge: JudgeId,
    capabilities: Set<JudgeCapability>,
): SyncRetryAction? {
    if (entry.judge != judge.id || entry.status !in setOf(
            SyncOperationStatus.PARTIAL.name,
            SyncOperationStatus.ERROR.name,
            SyncOperationStatus.CANCELLED.name,
        )
    ) return null
    if (module.status == SyncModuleStatus.SUCCESS.name || module.status == SyncModuleStatus.SKIPPED.name) return null
    val stage = SyncStage.entries.firstOrNull { it.name == module.stage } ?: return null
    return SyncRetryAction(
        kind = if (JudgeCapability.SAFE_STAGE_RETRY in capabilities) {
            SyncRetryKind.FAILED_STAGE
        } else {
            SyncRetryKind.FULL_SYNC
        },
        request = SyncRetryRequest(
            judge = judge,
            accountId = entry.accountId,
            operationId = entry.operationId,
            stage = stage,
            dataGeneration = entry.dataGeneration,
        ),
    )
}

@Composable
internal fun SyncOperationHistorySection(
    judge: JudgeId,
    operations: List<SyncOperationWithModules>,
    capabilities: Set<JudgeCapability>,
    retryingOperationId: Long?,
    onRetry: (SyncRetryRequest) -> Unit,
) {
    val entries = projectSyncOperationHistory(operations)
    NexusSection(label = stringResource(R.string.settings_sync_history_title)) {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_sync_history_empty),
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.textTertiary,
            )
        } else {
            entries.forEachIndexed { index, entry ->
                if (index > 0) Spacer(Modifier.height(NexusSpacing.xs))
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_sync_history_run, entry.operationId),
                                style = NexusTheme.typography.data,
                                color = NexusTheme.colors.textPrimary,
                            )
                            entry.modules.forEach { module ->
                                Text(
                                    text = stringResource(
                                        R.string.settings_sync_history_module,
                                        module.stage,
                                        module.status,
                                        module.attemptedCount,
                                        module.importedCount,
                                        module.updatedCount,
                                    ),
                                    style = NexusTheme.typography.dataSmall,
                                    color = NexusTheme.colors.textTertiary,
                                )
                            }
                        }
                        NexusTag(
                            text = entry.status,
                            tone = if (entry.status == SyncOperationStatus.SUCCESS.name) {
                                NexusTone.Success
                            } else {
                                NexusTone.Warning
                            },
                            selected = true,
                        )
                    }
                    entry.modules.forEach { module ->
                        val action = syncRetryAction(entry, module, judge, capabilities)
                        if (action != null) {
                            val label = if (action.kind == SyncRetryKind.FAILED_STAGE) {
                                stringResource(R.string.settings_sync_history_retry_stage)
                            } else {
                                stringResource(R.string.settings_sync_history_retry_full)
                            }
                            Text(
                                text = label,
                                style = NexusTheme.typography.sectionLabel,
                                color = if (retryingOperationId == entry.operationId) {
                                    NexusTheme.colors.textTertiary
                                } else {
                                    NexusTheme.colors.accent
                                },
                                modifier = Modifier
                                    .clickable(
                                        enabled = retryingOperationId == null,
                                        role = Role.Button,
                                    ) { onRetry(action.request) }
                                    .padding(top = NexusSpacing.xxs),
                            )
                        }
                    }
                }
            }
        }
    }
}
