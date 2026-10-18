# Sync transport slice — Spec

Date: 2026-10-18
Phase: 120 (reserved slot filled)
RFC: docs/rfcs/0001-e2ee-sync.md (§0–§4)
Constraint: compiled out of release unless explicitly enabled — all entry points guarded by
`BuildConfig.SYNC_POC` (debug=true, release=false).

## Goal

Complete the sync slice behind the POC gate with a **local-loopback transport simulation**:
an in-process `SyncTransport` seam that uploads/downloads sealed batches through a pluggable
remote *store* interface. No HTTP client, no real endpoint, no credential — the DoD is the
seam contract plus a round-trip through a fake store proving the full lifecycle:

```
appendLocalBatch → sealBatch → transport.upload(opId)
                → (other device) transport.download(opId) → openBatch → resolveRemoteBatch
```

## Deliverables

1. `core/sync/SyncTransport.kt` — `interface SyncRemoteStore { put(opId, bytes); get(opId): ByteArray?; list(): List<String> }`
   + `class SyncTransport(private val store: SyncRemoteStore, private val gateEnabled: Boolean)`
   with `upload(opId, payloadPath)`, `download(opId, filesDir)`, each method first checking the
   gate and throwing `SyncPocDisabledException` when off (release builds can never reach the
   code path through the app; the guard also protects direct callers).
2. `core/data/sync/SyncLogRepository.pushBatch(opId, transport)` — seal (if not yet sealed) →
   upload; `pullBatch(opId, transport)` — download into filesDir → markSealed with the local
   path → return rows via openBatch.
3. Tests: `SyncTransportTest` (gate-off rejects before touching the store; upload writes bytes;
   download round-trips bytes; unknown opId download → null) and `SyncLogRepositoryTest`
   extensions (push→pull round trip across two repositories sharing one fake store preserves
   rows and conflict resolution still applies).

## Out of scope (honest)

Real HTTP/WebDAV/S3 clients, key derivation, device pairing. The RFC's kill condition stays in
force: if a real transport phase ever exceeds ~2 k LOC, the RFC dies. This slice only proves
that the ledger's sealed payloads can move through an abstract store without leaking plaintext
or credentials.
