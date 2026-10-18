# Sync payload sealing — Spec

Date: 2026-10-18
Phase: 119 (reserved slot; RFC 0001 §4 storage side)

## Goal

The op-log ledger (Phase 118) stores plaintext row ops. Phase 119 adds the *sealing* step from
RFC 0001 §4: a batch's rows are serialized and sealed into an encrypted payload blob before the
operation is marked SEALED. This phase is storage-side only — **no network I/O** (transport is a
later reserved slot, and everything stays behind `BuildConfig.SYNC_POC` at the call-site level).

## Design

- `core/sync/SyncPayloadCodec.kt` — pure codec: `encode(ops: List<SyncLogOpRow>): String`
  (stable JSON: table/rowKey/kind/payloadJson sorted by (table,rowKey) for determinism) and
  `decode(raw): List<SyncLogOpRow>?` (never throws; null on garbage). SyncLogOpRow mirrors the
  DB row minus the auto id.
- `core/sync/SyncPayloadSealer.kt` — the crypto seam. Interface `Sealer { seal(plaintext,
  aad): ByteArray; open(sealedBytes, aad): ByteArray? }` with a `XorSealer` test double (NOT
  real crypto — clearly named, test-only) and a documented production stub that throws
  "production AEAD primitive not selected yet (RFC §4: Tink or libsodium binding)". The phase
  delivers the seam + lifecycle wiring, not a hand-rolled cipher — RFC §4 forbids hand-rolled
  crypto, and choosing the library is a Phase 120/transport decision.
- `SyncLogRepository.sealBatch(opId, sealer)` — loads ops, encodes, seals with AAD
  `deviceId|lamport` (RFC: per-op counters + device id in AAD), writes
  `filesDir/sync-payload/<opId>.bin`, markSealed(opId, path). Transactional; unknown batch →
  IllegalArgumentException (same contract as markSealed).
- `SyncLogRepository.openBatch(opId, sealer)` — reads payload, opens with same AAD, decodes;
  tampered/wrong-AAD → null (surface as verify-failure, never crash).

## Constraints honored

- No network I/O anywhere in this phase.
- No credential surface: the codec/sealer operate only on the op-log rows the repository
  already owns.
- The POC gate: callers that touch sealing must guard with `BuildConfig.SYNC_POC`; the classes
  themselves stay framework-free so release R8 keeps them harmless if unreferenced.
- DoD: round-trip unit tests (encode→seal→open→decode identity), tamper detection (wrong AAD /
  flipped byte → null), repository lifecycle test (PENDING → SEALED with payload file exists,
  content re-opens to identical ops), and a source-level audit that no network class appears in
  the three new files.
