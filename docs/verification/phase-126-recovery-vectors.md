# Phase 126 — Recovery Fault Vectors

Status: ACTIVE. This is a contract and decision-consumer phase; it does not certify physical
crash recovery on any platform. Source base: `becc0e3` on `codex/phase-121-platform-baseline`.

## Scope and inputs

- [Design](../superpowers/specs/2026-10-23-phase-126-recovery-vectors-design.md)
- [Implementation plan](../superpowers/plans/2026-10-23-phase-126-recovery-vectors.md)
- [Canonical JSON](../../contracts/recovery/v1/vectors.json)
- [Contract and field semantics](../../contracts/recovery/v1/README.md)

The corpus contains 32 synthetic normative cases, RV001–RV032. Nine cases have Android
projections: RV010–RV014, RV016 and RV020–RV022. A projection checks existing production
routing or generation classification. It does not execute file replacement, rollback, a worker
write, ENOSPC or a process crash. Future platform consumers need concrete setup, fault injection
and an independent end-state oracle for each adopted ID. Structural validation alone proves none
of those storage outcomes.

## Adoption and atomic units

| Platform/source audited | Current storage boundary | Phase 126 adoption | Follow-up |
| --- | --- | --- | --- |
| Android `becc0e3`; reviewed Phase 122 composition `19f0bbc` remains unmerged | Restore journal, database/WAL/SHM rollback set and separate generation file | Nine pure production decisions planned; no physical fault consumer | Phase 127: generation repair, rollback failure, startup blocking, staging ENOSPC and worker writes |
| Windows source at `becc0e3` | Schema migration transaction; each module's ledger/payload update is transactional; an entire multi-module sync is not | No new native consumer | Phase 128: interrupted ledger reconciliation and database fault tests |
| Linux PR #16 source `bf2dbea`, not main | Each payload stage is transactional; ledger update is separate; fresh schema bootstrap is not one transaction | No new native consumer | Phase 129: bootstrap and payload/ledger crash windows; use actual configuration authority |
| Apple active LocalWorkspaceStore/NexusDashboardModel at `becc0e3` | Whole-workspace JSON saved with `Data.write(.atomic)` per operation; a full sync is not one transaction | No new native consumer | Phase 130: damaged-file preservation and interrupted workspace writes; macOS/Xcode runtime prerequisites remain |

Linux currently reports a config path without an implemented independent JSON configuration
writer. Phase 129 must inspect the actual SQLite settings authority before adding recovery logic;
this contract does not introduce another configuration store.

RV029 describes valid empty replacement; RV030 describes valid empty upsert. These intentionally
preserve existing platform policies. RV031 is missing/invalid payload and cannot be treated as
valid empty data. RV032 retains the committed payload while reconciling an incomplete ledger.

## Verification gates

Task 1 strict loader/corpus review and Task 2 production decision consumer are in progress.
Final focused IDs, fresh unit-test totals and Gradle build evidence will be recorded here after
they complete. No emulator, real-device fault, native desktop recovery, old SQLite engine or
power-loss result is claimed by this phase.
