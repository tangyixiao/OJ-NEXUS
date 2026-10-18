package com.ojnexus.core.sync

/**
 * Phase 117 — sync POC feature flag (RFC 0001, ARC G).
 *
 * The flag lives ONLY in BuildConfig: debug builds compile with SYNC_POC=true, release builds
 * with SYNC_POC=false, so no release APK carries the POC surface. No runtime preference exists
 * on purpose — a flag a user could flip in a release build would violate the phase constraint.
 *
 * All sync POC code must guard its entry points with `if (BuildConfig.SYNC_POC)` and must be
 * reachable from nothing else. This keeps the compile-out promise auditable by grepping for the
 * flag name.
 */
object SyncPocGate {
    const val PROPERTY_NAME = "OJ_NEXUS_SYNC_POC"

    fun enabledFrom(raw: String?): Boolean = raw == "true"
}
