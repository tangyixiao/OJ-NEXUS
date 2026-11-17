package com.ojnexus.core.portable

import kotlinx.coroutines.flow.StateFlow

/**
 * Frozen repository/UI contract for phase 132. Controller-owned.
 *
 * The UI observes [state]; a SAF import calls [preview] (read-only), then the
 * user confirms with [apply] (transactional). [cancel] clears the pending
 * preview without any write. [export] rebuilds canonical bytes from the
 * bounded archive and verifies the identity.
 */
data class PortableWorkspaceState(
    val phase: Phase = Phase.IDLE,
    val preview: PortablePreview? = null,
    val applyResult: PortableApplyResult? = null,
    val reject: PortableReject? = null,
    val exportBytes: ByteArray? = null,
    val exportIdentity: String? = null,
    val message: String? = null,
) {
    enum class Phase { IDLE, PREVIEW, APPLYING, APPLIED, EXPORTED, ERROR }
}

interface PortableWorkspaceRepository {
    val state: StateFlow<PortableWorkspaceState>

    /** Read-only validation + capability negotiation. MUST NOT write to the store. */
    suspend fun preview(bytes: ByteArray): PortablePreview

    /** User-confirmed transactional apply of a previously previewed package. */
    suspend fun apply(bytes: ByteArray): PortableApplyResult

    /** Rebuild the canonical package from the archive; throws on identity mismatch. */
    suspend fun export(): ByteArray

    /** Clear pending preview/error state. MUST NOT write to the store. */
    fun cancel()
}
