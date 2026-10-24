# Phase 121 — Native baseline audit

Date: 2026-10-23 (Asia/Shanghai)
Status: VERIFIED for the source/evidence inventory; platform runtime gaps remain open.
Source baseline: main `11555095e2a66c8dfc4dd9918118ed81d97720da`.
Roadmap baseline: `5c81273451d1bde7b6f772ff024090582695666a` (PR #11, not merged at audit).

## Deliverable and limits

[The capability ledger](platform-capabilities.md) records each of the nine requested feature
areas separately for Android, Windows, Linux, macOS/iOS, with source, reachable entry, test
coverage entry, build result, runtime boundary and release status. Source absence means absent
from the inspected slice, not a claim about every future branch.

This phase is the audit itself. Its VERIFIED status does not mark 122–125 complete, certify
unrun tests, or approve an application release. Independent scoped platform work follows it.

## Checks and findings

- Read source route/model/store/adapter and test entries; compare implementation with README,
  old plan, ROADMAP and actual GitHub release assets.
- Inspect main/roadmap commit and tag identities live. `v1.0.3` resolves directly to `1155509`;
  the planning tag resolves to `5c81273`. Existing application release contains Android only.
- Read current GitHub CI records with exact commits/jobs. Android and Apple compilation evidence
  is separate from interactive/physical-device proof. Windows latest CI failure is retained.
- Read Linux worktree status/history without modifying it. HEAD equals merge-base, no unique
  committed implementation exists, and Linux source/workflow remain untracked. Five local
  tracked-file modifications are preserved. No reset, broad staging or reverse diff was used.
- Establish tag namespaces for four native platform families, with separate macOS/iOS assets,
  and retain historical Android tags plus docs-only planning tags.
- Reconcile stale Windows three-view/installer documentation with current source. The existing
  smoke still covers three views, the app has a fourth Problems view, and review is CLI/store only.
- Carry forward two new Android runtime defects discovered by the parallel validation lane:
  note draft state is not observed by the editor, and the snapshot ZIP export action still calls
  raw SQLite export. A green baseline suite did not cover either user-visible wiring failure.

## Environment evidence

| Environment | Verified availability | Unverified boundary |
| --- | --- | --- |
| Windows | .NET SDK 10.0.401 available, projects target .NET 8 | Fresh gates belong to 123; clean VM query failed and is unknown, not asserted absent |
| Android | SDK and acceleration available; Phase 122 owns new dedicated emulator and fresh gates | Emulator does not substitute for physical hardware, TalkBack or 24h battery evidence |
| Linux | Already-running WSL Ubuntu 24.04, CMake/Ninja/Qt6 6.4.2 available | Tool presence is not G-L or interactive Qt verification |
| Apple | Existing macOS CI runner built both native products at `5c81273` | No local macOS/Xcode host; application launch/interaction and signing remain unverified |

No stopped WSL distribution or VM was started by the audit. No user database or worktree was
cleaned. Tests listed in the matrix are coverage pointers; execution results are only those
with exact cited CI records or separate future phase reports.

## Acceptance audit

- [x] Capability/source and UI/CLI distinctions recorded for all four native platform families.
- [x] Commit, schema/build identity, exact existing CI results and release assets recorded.
- [x] Unknown/absent/runtime-blocked entries explicitly classified.
- [x] Old Apple 102/103 and physical-device performance debt carried forward.
- [x] Platform-specific tag naming decided without rewriting old releases.
- [x] Relative document links and source pointers checked; only intended files staged.
- [x] Scoped PR prepared for review; no merge or runtime completion inferred.

The audit used a read-only platform subagent plus main-agent verification. All substantive
application implementation remains delegated to implementation agents and independently reviewed.
