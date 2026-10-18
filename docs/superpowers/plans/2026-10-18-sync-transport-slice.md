# Sync transport slice — Plan

Date: 2026-10-18
Phase: 120 (reserved slot filled)
Spec: docs/superpowers/specs/2026-10-18-sync-transport-slice.md

## Steps executed

1. `core/sync/SyncTransport.kt` — `SyncRemoteStore` interface (put/get/list over opaque bytes;
   a real WebDAV/S3 client would implement this later) + `SyncTransport` whose **constructor
   throws `SyncPocDisabledException` when the gate is off** — a release-shaped transport cannot
   even be constructed, which is stronger than per-call guards. `upload` requires an existing
   payload file; `download` writes `sync-payload/<opId>.bin` and returns its path, null when
   the store lacks the op.
2. `SyncLogRepository.pushBatch(opId, transport, sealer, filesDir)` — seals first when still
   PENDING, then uploads; `pullBatch(...)` — downloads, marks SEALED against the local file,
   returns decoded rows (null on tamper). Both reject unknown batches.
3. Tests: SyncTransportTest 5 (gate-off construction refused before I/O; upload writes bytes;
   download round trip lands in sync-payload/; unknown op → null; missing payload throws);
   SyncLogRepositoryTest +1 (push→pull round trip across two transports sharing one fake store
   preserves rows 1234A/1234B).

## Result

- 779 tests / 0 failures; full gates `test assembleDebug assembleRelease` BUILD OK.
- The POC slice is now end-to-end local: ledger → seal → store → pull → open → conflict
  resolution, all deterministic, all behind BuildConfig.SYNC_POC (debug-only by build graph).
- Honest scope note repeated: no HTTP client, no key derivation, no device pairing — the real
  transport remains unproven and the RFC kill condition (~2 k LOC) stays in force. Total sync
  surface so far is well under that line (~450 LOC including tests).

## Bookkeeping

- versionCode 104 → 105, versionName 1.0.2 → 1.0.3.
- ROADMAP "SYNC TRANSPORT SLICE" bilingual section prepended before SYNC PAYLOAD SEALING.
- LONG_RANGE_PLAN PHASE 120 marked done — **the numbered plan (78–120) is now complete**;
  Phases 102/103 (Apple) remain the only deferred work, blocked on a macOS host by policy.
