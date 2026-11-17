package com.ojnexus.feature.portable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.portable.PortableContractException
import com.ojnexus.core.portable.PortablePreview
import com.ojnexus.core.portable.PortableReject
import com.ojnexus.core.portable.PortableWorkspaceRepository
import com.ojnexus.core.portable.PortableWorkspaceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Transport phase of the SAF document. The payload itself never crosses into the
 * repository layer: the screen reads and writes documents, the repository validates,
 * previews and applies bytes.
 */
enum class PortableTransferPhase { IDLE, READING, REBUILDING, WRITING }

/**
 * Transport failures owned by the UI layer. Package rejections are not listed here —
 * they arrive as [PortableReject] straight from the frozen contract.
 */
enum class PortableTransferFailure {
    DOCUMENT_UNREADABLE,
    DOCUMENT_EMPTY,
    DOCUMENT_TOO_LARGE,
    DOCUMENT_NOT_WRITTEN,
    EXPORT_FAILED,
    PREVIEW_FAILED,
    APPLY_FAILED,
}

/**
 * Everything the screen renders: the frozen repository state plus the document
 * transport the repository does not model, merged into one flow so the screen has a
 * single source of truth.
 *
 * [testSource] is derived, not read: [PortablePreview] carries no synthetic flag, so
 * the screen cannot ask the package directly. See [PortableWorkspaceViewModel.isTestSource].
 */
data class PortableWorkspaceUiState(
    val workspace: PortableWorkspaceState = PortableWorkspaceState(),
    val transfer: PortableTransferPhase = PortableTransferPhase.IDLE,
    val failure: PortableTransferFailure? = null,
    val reject: PortableReject? = null,
    val previewOpen: Boolean = false,
    val exportWritten: Boolean = false,
    val testSource: Boolean = false,
)

/**
 * Drives the portable workspace screen.
 *
 * Intents are split by owner: reading and writing the picked document is a UI concern
 * (see [beginRead], [failRead], [rebuildExport], [finishExport]); validation [preview],
 * the confirmed [apply] and [cancel] delegate to [PortableWorkspaceRepository].
 *
 * Nothing here writes on its own — [apply] only runs after the user confirms the
 * preview, and [cancel] clears the pending bytes without touching the store.
 */
class PortableWorkspaceViewModel(
    private val repository: PortableWorkspaceRepository,
) : ViewModel() {

    private data class LocalState(
        val transfer: PortableTransferPhase = PortableTransferPhase.IDLE,
        val failure: PortableTransferFailure? = null,
        val reject: PortableReject? = null,
        val previewOpen: Boolean = false,
        val exportWritten: Boolean = false,
        val testSource: Boolean = false,
    )

    private val local = MutableStateFlow(LocalState())

    /** Bytes of the package currently offered for confirmation. */
    private var pendingBytes: ByteArray? = null

    /**
     * In-flight preview launch. A superseded or cancelled preview must never
     * surface its dialog, so every superseding intent cancels this job first.
     */
    private var previewJob: Job? = null

    val state: StateFlow<PortableWorkspaceUiState> =
        combine(repository.state, local) { workspace, mine ->
            PortableWorkspaceUiState(
                workspace = workspace,
                transfer = mine.transfer,
                failure = mine.failure,
                reject = mine.reject,
                previewOpen = mine.previewOpen,
                exportWritten = mine.exportWritten,
                testSource = mine.testSource,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PortableWorkspaceUiState(workspace = repository.state.value),
        )

    /** The screen has a document URI and is about to read it. */
    fun beginRead() {
        pendingBytes = null
        previewJob?.cancel()
        previewJob = null
        local.update {
            it.copy(
                transfer = PortableTransferPhase.READING,
                failure = null,
                reject = null,
                previewOpen = false,
                exportWritten = false,
                testSource = false,
            )
        }
    }

    /** Reading the picked document failed; nothing was validated or written. */
    fun failRead(failure: PortableTransferFailure) = local.update {
        it.copy(transfer = PortableTransferPhase.IDLE, failure = failure)
    }

    /**
     * Rebuilds the canonical package so the screen can write it to the picked document.
     * Returns null on failure; the failure is already reflected in [state].
     */
    suspend fun rebuildExport(): ByteArray? {
        local.update {
            it.copy(
                transfer = PortableTransferPhase.REBUILDING,
                failure = null,
                exportWritten = false,
            )
        }
        return try {
            val bytes = repository.export()
            local.update { it.copy(transfer = PortableTransferPhase.WRITING) }
            bytes
        } catch (e: CancellationException) {
            local.update { it.copy(transfer = PortableTransferPhase.IDLE) }
            throw e
        } catch (e: Exception) {
            local.update {
                it.copy(
                    transfer = PortableTransferPhase.IDLE,
                    failure = PortableTransferFailure.EXPORT_FAILED,
                    exportWritten = false,
                )
            }
            null
        }
    }

    /** The picked document was written (or not). Only [written] surfaces a success line. */
    fun finishExport(written: Boolean) = local.update {
        it.copy(
            transfer = PortableTransferPhase.IDLE,
            exportWritten = written,
            failure = if (written) null else PortableTransferFailure.DOCUMENT_NOT_WRITTEN,
        )
    }

    /** Validates the package read from the document. Read-only — writes nothing. */
    fun preview(bytes: ByteArray) {
        previewJob?.cancel()
        previewJob = null
        pendingBytes = bytes
        local.update {
            it.copy(
                transfer = PortableTransferPhase.IDLE,
                failure = null,
                reject = null,
                previewOpen = false,
                exportWritten = false,
                testSource = false,
            )
        }
        previewJob = viewModelScope.launch {
            try {
                val preview = repository.preview(bytes)
                val testSource = isTestSource(preview, repository.state.value.message)
                local.update { it.copy(previewOpen = true, testSource = testSource) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PortableContractException) {
                pendingBytes = null
                local.update {
                    it.copy(previewOpen = false, reject = e.reject, testSource = false)
                }
            } catch (e: Exception) {
                pendingBytes = null
                local.update {
                    it.copy(
                        previewOpen = false,
                        failure = PortableTransferFailure.PREVIEW_FAILED,
                        testSource = false,
                    )
                }
            }
        }
    }

    /** Applies the confirmed package. Transactional: the repository owns the write. */
    fun apply() {
        val bytes = pendingBytes ?: return
        viewModelScope.launch {
            try {
                repository.apply(bytes)
                pendingBytes = null
                local.update { it.copy(previewOpen = false, reject = null, failure = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PortableContractException) {
                pendingBytes = null
                local.update { it.copy(previewOpen = false, reject = e.reject) }
            } catch (e: Exception) {
                pendingBytes = null
                local.update {
                    it.copy(
                        previewOpen = false,
                        failure = PortableTransferFailure.APPLY_FAILED,
                        testSource = false,
                    )
                }
            }
        }
    }

    /** Aborts the pending import at any step. Never writes. */
    fun cancel() {
        pendingBytes = null
        previewJob?.cancel()
        previewJob = null
        repository.cancel()
        local.update {
            it.copy(
                transfer = PortableTransferPhase.IDLE,
                failure = null,
                reject = null,
                previewOpen = false,
                testSource = false,
            )
        }
    }

    /** Acknowledges a terminal notice without touching a pending package. */
    fun dismiss() = local.update {
        it.copy(failure = null, reject = null, exportWritten = false)
    }

    /**
     * The frozen [PortablePreview] exposes no synthetic flag; only the codec's
     * `ValidatedPackage` carries one. A synthetic package is produced by the
     * `synthetic-fixture-gen` generator, whose name is embedded in
     * [PortablePreview.generator] as `"<name> <version>"`, and its source tag is
     * `SYNTHETIC-TEST-FIXTURE`, which the repository may repeat on the state message.
     * Those two derived signals are the whole surface the UI can see, so both are checked.
     *
     * Known limits, both label-only (they never change what the archive stores):
     * - The prefix match is deliberately broader than the codec's exact-name rule
     *   (`genName == "synthetic-fixture-gen"`), so a prefix or case variant of the
     *   generator name may show the TEST SOURCE label while the archive keeps
     *   `PORTABLE-V1` — a conservative false positive.
     * - A package generated by a normal generator but containing individual records
     *   tagged `synthetic` in their extra block is indistinguishable at this layer.
     * - The message branch is a defensive fallback: on the current happy path the
     *   repository clears the message on every PREVIEW state write, so the generator
     *   prefix is the operative signal against the live repository contract.
     */
    private fun isTestSource(preview: PortablePreview, message: String?): Boolean {
        if (preview.generator.startsWith(SYNTHETIC_GENERATOR, ignoreCase = true)) return true
        return message?.contains(SYNTHETIC_SOURCE_TAG, ignoreCase = true) == true
    }

    private companion object {
        /** Generator name used by the codec for synthetic fixtures. */
        const val SYNTHETIC_GENERATOR = "synthetic-fixture-gen"

        /** Source tag the codec stamps on synthetic packages. */
        const val SYNTHETIC_SOURCE_TAG = "SYNTHETIC-TEST-FIXTURE"
    }
}
