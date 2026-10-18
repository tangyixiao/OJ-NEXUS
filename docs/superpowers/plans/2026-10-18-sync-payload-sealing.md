# Sync payload sealing — Plan

Date: 2026-10-18
Phase: 119 (reserved slot; RFC 0001 §4 storage side)
Spec: docs/superpowers/specs/2026-10-18-sync-payload-sealing.md

## Steps executed

1. `core/sync/SyncPayloadCodec.kt` — deterministic JSON codec over `SyncLogOpRow`
   (table/rowKey/kind/payloadJson), sorted by (table, rowKey) on encode; decode never throws.
2. `core/sync/SyncPayloadSealer.kt` — the crypto seam: `SyncPayloadSealer` interface (seal /
   open-with-AAD, open returns null on verification failure), `XorSealer` test double (named as
   test-only in its KDoc and pinned by a test asserting the name), and a
   `ProductionSealerNotSelectedException` stub — the production AEAD choice (Tink / libsodium
   binding) is the transport phase's first task; hand-rolled crypto stays forbidden.
3. `SyncLogRepository.sealBatch(opId, filesDir, sealer)` — encode → seal with AAD
   `deviceId|lamport` → write `filesDir/sync-payload/<opId>.bin` → markSealed;
   `openBatch(opId, sealer)` — read → open → decode, tamper/garbage → null.
4. Tests: SyncPayloadCodecTest 4 (encode determinism, round trip preserving sort order, garbage
   → null, XOR double round trip); SyncLogRepositoryTest +3 (seal→open round trip with 3 rows
   across 2 tables, wrong-pad + truncated-payload → null, unknown batch rejected by sealing).

## Result

- 773 tests / 0 failures; full gates `test assembleDebug assembleRelease` BUILD OK.
- The ledger now carries a complete sealed-batch lifecycle (PENDING → SEALED with payload file
  → openable) with zero network I/O and zero credential surface. The transport phase inherits
  the seam, the codec, and the AAD convention.

### Lessons

- The round-trip test first asserted the input order; the codec's documented sort order makes
  `attempts/77` precede `problems/1234A` — the test now asserts the sorted order explicitly
  (the sort is the feature).
- `XorSealer.open` is intentionally symmetric (no auth tag); real tamper detection is asserted
  at the repository level (wrong pad → undecodable JSON → null), which is the honest contract
  until the production AEAD lands.
