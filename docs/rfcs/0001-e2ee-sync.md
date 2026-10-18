# RFC 0001 — End-to-End-Encrypted, Server-Agnostic Sync (ARC G)

Status: PROPOSED (Phase 116, design-gated; cut allowed)
Date: 2026-10-18
Scope: Android first; Windows CLI/WPF optional follow-on.

## 0. Non-negotiables (read this section first — the RFC dies if any is violated)

1. **Credentials and OpenApp secrets NEVER sync.** This means: judge passwords (there are
   none — the app never holds them), the Luogu open-platform credential
   (`luogu-openapp-credential.bin` in `noBackupFilesDir`), and any future token/cookie. The
   sync layer has no API surface that can even name these objects. The snapshot exporter's
   no-secret scan (Phase 86) remains the last line of defense for exports; the sync layer adds
   its own equivalent denylist test before encrypting anything.
2. **No new first-party server.** The endpoint is a URL the user provides (WebDAV, S3-compatible
   bucket, or any dumb blob store). OJ NEXUS ships no backend, no accounts, no telemetry. The
   server is treated as actively hostile.
3. **Local stays canonical.** Sync is an *optional* mirror of the local database, not a source
   of truth. Removing the endpoint must restore the app to exactly today's behavior (offline
   first holds).
4. **No schema change is required to ship sync.** Sync rides on the existing snapshot/manifest
   vocabulary (Phase 86) plus an append-only op-log (new tables, additive migrations only).

## 1. Threat model

| Adversary | Capability | Defense |
| --- | --- | --- |
| Passive network observer | Sees traffic to the endpoint | Payload is encrypted client-side before upload; server never sees plaintext or key material |
| Malicious/compromised endpoint server | Reads, reorders, drops, replays blobs; may serve old versions | AEAD (XChaCha20-Poly1305) with per-op monotonically increasing counters + device id in the AAD; replay/reorder detected by op-log sequence verification; stale snapshots are rejected the same way a stale dataGeneration is today |
| Device thief | Has the unlocked-at-rest device and the synced blob | Key material derived from a user-supplied passphrase via Argon2id; nothing key-shaped is stored unencrypted on disk |
| Another app on the device | Reads app-private files | Same posture as today (Android app-private storage); key kept in memory only during sync operations, then zeroed |
| Coin-flip: user loses passphrase | — | Data on the endpoint is unrecoverable. Documented, not mitigated. No key escrow, ever. |

Out of scope: protecting against a compromised OS, malicious input during an active session on
a shared device, or the user exporting/syncing and then handing the passphrase away.

## 2. Data classification (what may sync)

Allowed: everything the snapshot exporter already carries — problems, attempts, notes, reviews,
training sessions, analytics day rows, remote metadata, contests. This is exactly
`DatabaseRestoreCoordinator.REQUIRED_TABLES` (22 tables, schema v14).

Never allowed: `judge_accounts` (handles stay device-local by choice — handles are not secrets
but they identify; syncing them would couple identity across devices), the Luogu credential
file (not in the db at all), `sync_operations` (device-local operational ledger), WorkManager
state, DataStore preferences (device-specific settings like theme and backup cadence).

## 3. Merge rules

The sync unit is the **op-log entry**, not the whole database:

- Each entry: `{op_id: ULID, device_id, lamport, schema_version, table_ops: [INSERT/UPSERT/
  DELETE by primary key], aad: {device_id, lamport}}`, AEAD-sealed.
- **Conflicts resolve deterministically, never interactively**: for row conflicts, the higher
  `(lamport, device_id)` wins (last-writer-wins with a total order). Deletes win over inserts
  at equal order only when the delete's lamport is greater. Every resolved conflict appends to
  a local `sync_conflict_log` row so the resolution is inspectable, not silent.
- Deterministic-only rule (STANDING DECISIONS) applies: merge outcomes are computed by pure
  functions, unit-tested, and explainable via reason codes in the conflict log — same
  discipline as recommendations.
- Whole-snapshot sync (Phase 86 export) is the bootstrap path: first device uploads one
  snapshot op; later devices restore it, then exchange op-logs. The snapshot path reuses the
  existing manifest/conflict-preview code verbatim.

## 4. Crypto sketch

- KDF: Argon2id(passphrase, per-user random salt stored on the endpoint as plaintext — salt is
  not secret), 64 MiB / t=3 / p=4.
- Root key never stored; per-operation keys = HKDF-SHA256(root, "op" || lamport || device_id).
- Primitive: XChaCha20-Poly1305 (libsodium-binding or Tink; final choice at Phase 117 spec —
  must be a maintained Android library, no hand-rolled crypto).
- The endpoint stores only: salt blob, snapshot ops, op-log entries. It cannot list handles,
  reconstruct the db, or verify passphrases.

## 5. Why this can be cut

The local-first posture already delivers the core value; sync is additive convenience. If the
Phase 117 POC shows the crypto+merge surface growing past ~2 k LOC or any pressure toward a
first-party server, the RFC dies and Phases 118–120 are repurposed for ARC C/D leftovers per
the plan. A rejected RFC is a normal outcome, not a failure.
