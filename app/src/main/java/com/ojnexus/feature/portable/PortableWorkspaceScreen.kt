package com.ojnexus.feature.portable

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.portable.PortablePreview
import com.ojnexus.core.portable.PortableReject
import com.ojnexus.core.portable.PortableSpec
import com.ojnexus.core.portable.PortableWorkspaceState
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Default filename offered by the system document picker on export. */
private const val EXPORT_DOCUMENT_NAME = "oj-nexus-portable-workspace.json"

/** Result of reading a picked document off the UI thread. */
private sealed interface DocumentRead {
    data class Ok(val bytes: ByteArray) : DocumentRead
    data object Empty : DocumentRead
    data object TooLarge : DocumentRead
    data object Unreadable : DocumentRead
}

/**
 * Reads the whole document, bounding it at [PortableSpec.MAX_PACKAGE_BYTES]. Nothing is
 * validated here — the bytes go straight to the repository for a read-only preview.
 */
private fun readDocument(resolver: ContentResolver, uri: Uri): DocumentRead = try {
    val stream = resolver.openInputStream(uri) ?: return DocumentRead.Unreadable
    stream.use { input ->
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        var tooLarge = false
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > PortableSpec.MAX_PACKAGE_BYTES) {
                tooLarge = true
                break
            }
            buffer.write(chunk, 0, read)
        }
        when {
            tooLarge -> DocumentRead.TooLarge
            total == 0 -> DocumentRead.Empty
            else -> DocumentRead.Ok(buffer.toByteArray())
        }
    }
} catch (e: Exception) {
    DocumentRead.Unreadable
}

/** Writes canonical bytes to a picked document; false means no bytes reached the stream. */
private fun writeDocument(resolver: ContentResolver, uri: Uri, bytes: ByteArray): Boolean = try {
    val stream = resolver.openOutputStream(uri) ?: return false
    stream.use {
        it.write(bytes)
        it.flush()
    }
    true
} catch (e: Exception) {
    false
}

/**
 * Portable Workspace — a settings entry separate from whole-database backup.
 *
 * The screen owns SAF document transport (pick, read, write); the repository owns
 * validation, the read-only preview and the confirmed transactional apply. Import is
 * two-step: picking a document produces a preview, and only the CONFIRM button applies
 * it. A cancelled picker (null URI) is a clean no-op — no state change, no DB write.
 */
@Composable
fun PortableWorkspaceScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<PortableWorkspaceViewModel>(
        factory = ContainerViewModelFactory(container) {
            PortableWorkspaceViewModel(it.portableWorkspaceRepository)
        },
    )
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingImportUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // A null URI is the user backing out of the system picker. Leave everything as-is.
        if (uri == null) return@rememberLauncherForActivityResult
        pendingImportUri = uri.toString()
        viewModel.beginRead()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                readDocument(context.contentResolver, uri)
            }
            when (result) {
                is DocumentRead.Ok -> viewModel.preview(result.bytes)
                DocumentRead.Empty -> viewModel.failRead(PortableTransferFailure.DOCUMENT_EMPTY)
                DocumentRead.TooLarge -> viewModel.failRead(PortableTransferFailure.DOCUMENT_TOO_LARGE)
                DocumentRead.Unreadable -> viewModel.failRead(PortableTransferFailure.DOCUMENT_UNREADABLE)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val bytes = pendingExportBytes
        pendingExportBytes = null
        if (uri == null || bytes == null) {
            // Cancelled export: reset to idle with no error and no DB write.
            viewModel.cancel()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                writeDocument(context.contentResolver, uri, bytes)
            }
            viewModel.finishExport(written)
        }
    }

    Column(Modifier.fillMaxSize().background(NexusTheme.colors.background)) {
        NexusTopBar(
            title = stringResource(R.string.portable_workspace_title),
            trailing = {
                Text(
                    text = stringResource(R.string.action_back),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.accent,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onBack),
                )
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexusSpacing.screenHorizontal),
        ) {
            Spacer(Modifier.height(NexusSpacing.md))
            Text(
                text = stringResource(R.string.portable_workspace_hint),
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.textTertiary,
            )
            Spacer(Modifier.height(NexusSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs)) {
                ActionButton(
                    label = stringResource(R.string.portable_action_import),
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "application/octet-stream"))
                    },
                )
                ActionButton(
                    label = stringResource(R.string.portable_action_export),
                    onClick = {
                        scope.launch {
                            val bytes = viewModel.rebuildExport()
                            if (bytes != null) {
                                pendingExportBytes = bytes
                                exportLauncher.launch(EXPORT_DOCUMENT_NAME)
                            }
                        }
                    },
                )
            }
            pendingImportUri?.let { source ->
                Spacer(Modifier.height(NexusSpacing.xs))
                Text(
                    text = stringResource(R.string.portable_state_source, source),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                )
            }
            Spacer(Modifier.height(NexusSpacing.md))
            StatusRegion(uiState = uiState, onDismiss = viewModel::dismiss)
            Spacer(Modifier.height(NexusSpacing.md))
            NexusSection(label = stringResource(R.string.portable_section_archive)) {
                Text(
                    text = stringResource(R.string.portable_state_saved),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textSecondary,
                )
            }
            Spacer(Modifier.height(NexusSpacing.xl))
        }
    }

    val preview = uiState.workspace.preview
    if (uiState.previewOpen && preview != null) {
        PreviewDialog(
            preview = preview,
            testSource = uiState.testSource,
            onConfirm = viewModel::apply,
            onCancel = viewModel::cancel,
        )
    }
}

/** One-line status notice: Loading / Empty / Error / Success, never color-only. */
@Composable
private fun StatusRegion(
    uiState: PortableWorkspaceUiState,
    onDismiss: () -> Unit,
) {
    val colors = NexusTheme.colors
    val workspace = uiState.workspace
    Column {
        when (uiState.transfer) {
            PortableTransferPhase.IDLE -> Unit
            PortableTransferPhase.READING ->
                StatusLine(stringResource(R.string.portable_state_reading), colors.accent, false, onDismiss)
            PortableTransferPhase.REBUILDING ->
                StatusLine(stringResource(R.string.portable_state_rebuilding), colors.accent, false, onDismiss)
            PortableTransferPhase.WRITING ->
                StatusLine(stringResource(R.string.portable_state_writing), colors.accent, false, onDismiss)
        }
        uiState.failure?.let { failure ->
            StatusLine(stringResource(failureLabel(failure)), colors.danger, true, onDismiss)
        }
        uiState.reject?.let { reject ->
            StatusLine(
                stringResource(R.string.portable_reject_prefix, reject.wire, reject.detail),
                colors.danger,
                true,
                onDismiss,
            )
        }
        if (uiState.testSource) {
            StatusLine(stringResource(R.string.portable_state_test_source), colors.warning, false, onDismiss)
        }
        if (uiState.exportWritten) {
            StatusLine(stringResource(R.string.portable_state_exported), colors.success, false, onDismiss)
        }
        val result = workspace.applyResult
        if (workspace.phase == PortableWorkspaceState.Phase.APPLIED && result != null) {
            if (result.identical) {
                StatusLine(stringResource(R.string.portable_state_identical), colors.success, false, onDismiss)
            } else {
                StatusLine(
                    stringResource(
                        R.string.portable_state_applied,
                        result.rowsUpserted,
                        result.rowsDeleted,
                    ),
                    colors.success,
                    false,
                    onDismiss,
                )
            }
        }
        if (workspace.phase == PortableWorkspaceState.Phase.ERROR &&
            uiState.failure == null && uiState.reject == null
        ) {
            StatusLine(stringResource(R.string.portable_failure_generic), colors.danger, true, onDismiss)
        }
        val idle = uiState.transfer == PortableTransferPhase.IDLE &&
            uiState.failure == null && uiState.reject == null &&
            !uiState.exportWritten && result == null &&
            workspace.phase == PortableWorkspaceState.Phase.IDLE
        if (idle) {
            StatusLine(stringResource(R.string.portable_state_empty), colors.textTertiary, false, onDismiss)
        }
    }
}

@Composable
private fun StatusLine(
    text: String,
    color: Color,
    showDismiss: Boolean,
    onDismiss: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = NexusTheme.typography.dataSmall,
            color = color,
            modifier = Modifier.weight(1f),
        )
        if (showDismiss) {
            Text(
                text = stringResource(R.string.portable_action_dismiss),
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.accent,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(NexusSpacing.xs),
            )
        }
    }
    Spacer(Modifier.height(NexusSpacing.xs))
}

/** Confirmation gate. Every number the user is asked to approve comes from the preview. */
@Composable
private fun PreviewDialog(
    preview: PortablePreview,
    testSource: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = NexusTheme.colors
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = {
            Text(
                stringResource(R.string.portable_preview_title),
                style = NexusTheme.typography.title,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (testSource) {
                    Text(
                        stringResource(R.string.portable_state_test_source),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.warning,
                    )
                    Spacer(Modifier.height(NexusSpacing.xs))
                }
                Text(
                    stringResource(R.string.portable_preview_identity, preview.identity.take(12)),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                )
                Text(
                    stringResource(R.string.portable_preview_meta, preview.scopeId, preview.sourcePlatform),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                )
                Text(
                    stringResource(R.string.portable_preview_generator, preview.generator),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                )
                Text(
                    stringResource(R.string.portable_preview_created, preview.createdAtEpochMs),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(NexusSpacing.sm))
                Text(
                    stringResource(R.string.portable_preview_counts_header),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textSecondary,
                )
                PortableSpec.COLLECTIONS.forEach { collection ->
                    Text(
                        stringResource(
                            R.string.portable_preview_kv_row,
                            collection,
                            preview.counts[collection] ?: 0,
                        ),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textSecondary,
                    )
                }
                Spacer(Modifier.height(NexusSpacing.sm))
                Text(
                    stringResource(R.string.portable_preview_sources_header),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textSecondary,
                )
                if (preview.sources.isEmpty()) {
                    Text(
                        stringResource(R.string.portable_none),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textTertiary,
                    )
                } else {
                    preview.sources.forEach { source ->
                        Text(
                            stringResource(
                                R.string.portable_preview_kv_row,
                                source.sourceScopeId,
                                source.recordCount,
                            ),
                            style = NexusTheme.typography.dataSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(NexusSpacing.sm))
                Text(
                    stringResource(
                        R.string.portable_preview_capability,
                        preview.editableCount,
                        preview.storedOnlyCount,
                        preview.rejectedCount,
                    ),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.accent,
                )
                Text(
                    stringResource(R.string.portable_preview_stored_saved),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(NexusSpacing.sm))
                Text(
                    stringResource(R.string.portable_preview_skipped_header),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textSecondary,
                )
                if (preview.skipped.isEmpty()) {
                    Text(
                        stringResource(R.string.portable_none),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textTertiary,
                    )
                } else {
                    preview.skipped.forEach { decision ->
                        Text(
                            stringResource(
                                R.string.portable_preview_pair_row,
                                decision.collection,
                                decision.stableId,
                            ),
                            style = NexusTheme.typography.dataSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(NexusSpacing.sm))
                Text(
                    stringResource(R.string.portable_preview_conflicts_header),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textSecondary,
                )
                if (preview.conflicts.isEmpty()) {
                    Text(
                        stringResource(R.string.portable_none),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textTertiary,
                    )
                } else {
                    preview.conflicts.forEach { conflict ->
                        Text(
                            stringResource(
                                R.string.portable_preview_conflict_row,
                                conflict.collection,
                                conflict.stableId,
                                conflict.kind,
                            ),
                            style = NexusTheme.typography.dataSmall,
                            color = colors.warning,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Text(
                stringResource(R.string.portable_preview_confirm),
                style = NexusTheme.typography.data,
                color = colors.accent,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onConfirm)
                    .padding(NexusSpacing.xs),
            )
        },
        dismissButton = {
            Text(
                stringResource(R.string.action_cancel),
                style = NexusTheme.typography.data,
                color = colors.textSecondary,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onCancel)
                    .padding(NexusSpacing.xs),
            )
        },
    )
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = NexusTheme.colors
    Box(
        Modifier
            .background(colors.surface, NexusRadius.sm)
            .border(NexusSize.dividerThickness, colors.accent, NexusRadius.sm)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = NexusSpacing.sm, vertical = NexusSpacing.xs),
    ) {
        Text(label, style = NexusTheme.typography.dataSmall, color = colors.accent)
    }
}

private fun failureLabel(failure: PortableTransferFailure): Int = when (failure) {
    PortableTransferFailure.DOCUMENT_UNREADABLE -> R.string.portable_failure_unreadable
    PortableTransferFailure.DOCUMENT_EMPTY -> R.string.portable_failure_empty
    PortableTransferFailure.DOCUMENT_TOO_LARGE -> R.string.portable_failure_too_large
    PortableTransferFailure.DOCUMENT_NOT_WRITTEN -> R.string.portable_failure_not_written
    PortableTransferFailure.EXPORT_FAILED -> R.string.portable_failure_export
    PortableTransferFailure.PREVIEW_FAILED -> R.string.portable_failure_preview
    PortableTransferFailure.APPLY_FAILED -> R.string.portable_failure_apply
}
