package com.ojnexus.core.sync

/**
 * Phase 120 — transport seam for the sync POC (RFC 0001). The remote is an abstract byte
 * store; real WebDAV/S3 clients are a later decision and would implement [SyncRemoteStore].
 * Every entry point is gated: when [BuildConfig.SYNC_POC] is false (release builds) the
 * transport refuses to run, keeping the compile-out promise reachable from code review.
 */
interface SyncRemoteStore {
    fun put(opId: String, bytes: ByteArray)
    fun get(opId: String): ByteArray?
    fun list(): List<String>
}

class SyncPocDisabledException :
    IllegalStateException("sync POC is disabled in this build (BuildConfig.SYNC_POC=false)")

class SyncTransport(
    private val store: SyncRemoteStore,
    private val gateEnabled: Boolean,
) {
    init {
        if (!gateEnabled) throw SyncPocDisabledException()
    }

    fun upload(opId: String, payloadPath: String) {
        val payload = java.io.File(payloadPath)
        if (!payload.exists()) {
            throw IllegalArgumentException("payload missing for $opId: $payloadPath")
        }
        store.put(opId, payload.readBytes())
    }

    /** Returns the local file path of the downloaded payload, or null when the store lacks it. */
    fun download(opId: String, filesDir: java.io.File): String? {
        val bytes = store.get(opId) ?: return null
        val dir = java.io.File(filesDir, "sync-payload")
        if (!dir.exists()) dir.mkdirs()
        val target = java.io.File(dir, "$opId.bin")
        target.writeBytes(bytes)
        return target.absolutePath
    }
}
