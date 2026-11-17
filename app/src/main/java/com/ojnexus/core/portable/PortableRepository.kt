package com.ojnexus.core.portable

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive

/**
 * Phase 132 Portable Workspace repository.
 *
 * Orchestrates the frozen codec, the bounded archive store and the UI state:
 *
 * - [preview] is a pure read. It validates the package, negotiates capability
 *   tiers and reports conflicts against existing archived rows, but performs
 *   zero writes.
 * - [apply] is the user-confirmed, transactional step. It revalidates from the
 *   bytes (never trusting a prior preview), no-ops on an identical identity and
 *   otherwise replaces the whole archive in the store's single transaction.
 * - [export] rebuilds canonical bytes from archived rows and asserts the rebuilt
 *   SHA-256 identity equals the stored manifest identity, so corruption cannot
 *   be silently exported.
 * - [cancel] clears pending preview state and aborts an in-flight apply before
 *   its first write. It never touches the store.
 *
 * Pure Kotlin: no Android, Room or Compose dependency. The store is an interface
 * so unit tests use a fake with no database.
 */
class PortableRepository(
    private val store: PortableArchiveStore,
    private val codec: PortableCodec = PortableCodec(),
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) : PortableWorkspaceRepository {

    private val mutableState = MutableStateFlow(PortableWorkspaceState())
    override val state: StateFlow<PortableWorkspaceState> = mutableState.asStateFlow()

    /**
     * Non-null while [apply] is in flight. [cancel] clears it; [apply] checks it
     * at every pre-write checkpoint so a cancelled apply cannot reach the store.
     */
    private val operationToken = AtomicReference<Any?>(null)

    override suspend fun preview(bytes: ByteArray): PortablePreview {
        return try {
            // validate + classify: the codec is pure and performs no writes.
            val base = codec.preview(bytes)
            // A load is a pure read; the codec cannot see the store, so the
            // repository enriches the preview with real archive conflicts.
            val existing = store.load()
            val enriched = base.copy(conflicts = conflictsAgainst(base, existing))
            mutableState.value = PortableWorkspaceState(
                phase = PortableWorkspaceState.Phase.PREVIEW,
                preview = enriched,
            )
            enriched
        } catch (e: PortableContractException) {
            mutableState.value = PortableWorkspaceState(
                phase = PortableWorkspaceState.Phase.ERROR,
                reject = e.reject,
            )
            throw e
        } catch (e: Exception) {
            mutableState.value = PortableWorkspaceState(
                phase = PortableWorkspaceState.Phase.ERROR,
                message = e.message,
            )
            throw e
        }
    }

    override suspend fun apply(bytes: ByteArray): PortableApplyResult {
        val token = Any()
        operationToken.set(token)
        try {
            // Revalidate from the bytes: the confirm step never trusts a preview.
            // Any contract error rejects the WHOLE package before any write.
            val vp = codec.validate(bytes)
            val decisions = codec.classify(vp)
            val editable = decisions.count { it.tier == PortableTier.EDITABLE }
            val storedOnly = decisions.count { it.tier == PortableTier.STORED_ONLY }
            val rejected = decisions.count { it.tier == PortableTier.REJECTED }

            ensureNotCancelled(token)

            val result: PortableApplyResult
            if (store.currentIdentity() == vp.identity) {
                // Idempotent reimport: the archive already holds exactly this
                // package, so there is no second replaceAll and no row churn.
                result = PortableApplyResult(
                    identity = vp.identity,
                    editableCount = editable,
                    storedOnlyCount = storedOnly,
                    rejectedCount = rejected,
                    rowsUpserted = 0,
                    rowsDeleted = 0,
                    identical = true,
                )
            } else {
                mutableState.value = PortableWorkspaceState(
                    phase = PortableWorkspaceState.Phase.APPLYING,
                )
                ensureNotCancelled(token)
                // One @Transaction replaceAll: all-or-nothing, measured counts.
                val stats = store.replaceAll(codec.toArchive(vp, nowEpochMs()))
                result = PortableApplyResult(
                    identity = vp.identity,
                    editableCount = editable,
                    storedOnlyCount = storedOnly,
                    rejectedCount = rejected,
                    rowsUpserted = stats.rowsUpserted,
                    rowsDeleted = stats.rowsDeleted,
                )
            }
            mutableState.value = PortableWorkspaceState(
                phase = PortableWorkspaceState.Phase.APPLIED,
                applyResult = result,
                // Data tag, not UI copy: the ViewModel reads it for TEST SOURCE.
                message = vp.sourceTag,
            )
            return result
        } catch (e: PortableContractException) {
            mutableState.value = PortableWorkspaceState(
                phase = PortableWorkspaceState.Phase.ERROR,
                reject = e.reject,
            )
            throw e
        } catch (e: CancellationException) {
            mutableState.value = PortableWorkspaceState()
            throw e
        } catch (e: Exception) {
            // No store failure may leave the UI stuck in APPLYING.
            mutableState.value = PortableWorkspaceState(
                phase = PortableWorkspaceState.Phase.ERROR,
                message = e.message,
            )
            throw e
        } finally {
            operationToken.compareAndSet(token, null)
        }
    }

    override suspend fun export(): ByteArray {
        return try {
            val archive = store.load()
            // rebuild() throws on an empty archive; never fabricate bytes.
            val rebuilt = codec.rebuild(archive)
            val manifest = archive.manifest
                ?: throw IllegalStateException("portable archive is empty; nothing to export")
            val rebuiltIdentity = codec.identity(rebuilt)
            if (rebuiltIdentity != manifest.identity) {
                throw IllegalStateException(
                    "portable archive identity mismatch: rebuilt $rebuiltIdentity " +
                        "but manifest records ${manifest.identity}; refusing to export corrupt bytes",
                )
            }
            mutableState.value = PortableWorkspaceState(
                phase = PortableWorkspaceState.Phase.EXPORTED,
                exportBytes = rebuilt,
                exportIdentity = manifest.identity,
                message = manifest.sourceTag,
            )
            rebuilt
        } catch (e: IllegalStateException) {
            mutableState.value = PortableWorkspaceState(
                phase = PortableWorkspaceState.Phase.ERROR,
                message = e.message,
            )
            throw e
        } catch (e: Exception) {
            mutableState.value = PortableWorkspaceState(
                phase = PortableWorkspaceState.Phase.ERROR,
                message = e.message,
            )
            throw e
        }
    }

    override fun cancel() {
        operationToken.set(null)
        // IDLE, no preview, no error, no export bytes. Zero store writes.
        mutableState.value = PortableWorkspaceState()
    }

    /**
     * Read-only conflict report: incoming (collection, stableId) pairs that
     * already exist in the stored archive under a DIFFERENT package identity.
     * An identical identity means apply is a no-op, so nothing is reported as a
     * conflict. Conflicts are surfaced, never auto-resolved.
     */
    private fun conflictsAgainst(
        preview: PortablePreview,
        existing: PortableArchive,
    ): List<PortableConflict> {
        val manifest = existing.manifest ?: return emptyList()
        if (manifest.identity == preview.identity) return emptyList()
        val existingKeys = existing.records.mapTo(HashSet()) { it.collection to it.stableId }
        return preview.decisions.mapNotNull { d ->
            if ((d.collection to d.stableId) in existingKeys) {
                PortableConflict(
                    collection = d.collection,
                    stableId = d.stableId,
                    kind = "overwrite",
                    detail = "replaces archived row from package " +
                        "${manifest.identity.take(12)} (${manifest.sourceTag}) in a full-archive swap",
                )
            } else {
                null
            }
        }
    }

    /** Honours both [cancel] and ordinary coroutine cancellation before a write. */
    private suspend fun ensureNotCancelled(token: Any) {
        if (operationToken.get() !== token || !currentCoroutineContext().isActive) {
            throw CancellationException("portable apply cancelled before any store write")
        }
    }
}
