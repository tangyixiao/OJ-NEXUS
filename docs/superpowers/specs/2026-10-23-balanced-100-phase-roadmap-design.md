# OJ NEXUS — Phase 121–220 Planning Design

Date: 2026-10-23 (Asia/Shanghai)
Status: planning snapshot for review; implementation has not started.

## Requested outcome

The user requested a development plan covering at least the next 100 phases, selected balanced
progress across platforms, and requested frequent meaningful PRs, Releases, and help progressing
GitHub achievements through real contributions. Deliver exactly 100 numbered phases, 121–220,
with dependencies, write scopes, concrete outcomes, and acceptance criteria.

## Chosen structure

Use 20 waves of five phases: shared contract/evidence, Android, Windows, Linux, Apple. Each lane
receives 20 phases. Apple means macOS and iOS, with separate build and device evidence inside
the same lane. Shared phases produce usable contracts, fixtures, audit reports, or release
evidence; they are not permission to create a cross-platform UI shell or shared database.

Alternatives considered:

- Android-first followed by desktop catch-up: quickest Android improvement, but does not meet
  the user's balanced-platform preference.
- Four independent 25-phase backlogs: numerically balanced, but allows incompatible import,
  review, and data-identity rules to diverge.
- Chosen: a shared deliverable plus four native slices per wave. Cross-platform contracts land
  before consumers; a host-blocked platform does not stop unrelated lanes.

The roadmap is a rolling backlog, not a promise to execute 100 phases without reassessment.
Each phase receives a small implementation spec/plan at execution time. Freeze only the next
wave's interfaces; do not pre-select future API endpoints, crypto libraries, schema versions,
SDK versions, release dates, or signing identities.

## Verified baseline and limits

- GitHub main: `11555095e2a66c8dfc4dd9918118ed81d97720da`, merge of PR #10 on 2026-10-18;
  its head was `f7dd035eec9fd0fbedb9f84ef433334601b3c9b4`.
- Android: `app/build.gradle.kts` says 1.0.3 / 105; Room schema constant is 15. Existing
  features include notes, review, sessions, judge catalogs, backups, and sync PoC seams.
- GitHub has a published `v1.0.3` release. Its existence does not prove every device gate.
- Windows has Core, CLI, WPF, problems, review persistence, packaging and installer/signing
  scripts. It must not be planned as an empty client or assumed fully equivalent to Android.
- Linux Qt/C++ implementation is in the separate `codex/linux-client` worktree at `5c42103`
  when inspected; `linux/` is not part of the inspected main tree. Integrate through a reviewed
  PR before treating main-tree Linux paths as available. Do not edit that worktree in this task.
- Apple has native Core/UI/app targets and an existing macOS CI workflow; no attached local
  macOS/iOS runtime is available. CI compilation and interactive acceptance are distinct.
- `adb devices -l` returned no attached Android device during planning.
- Historical release checklists leave real-device startup, battery, memory and accessibility
  work open. A pure test or timing helper does not close those items.
- The sync PoC has abstract transport, a caller-supplied Boolean gate, an XOR test double in
  main sources, and no selected production AEAD. Review of these files supports a new safety
  gate, not a claim that secure production sync is delivered.

## Product and architecture constraints

- Keep the native stacks and `JudgeAdapter` boundaries. No Flutter, Electron, React Native,
  WebView shell, AI/chat/social layer, or first-party sync service.
- Android follows AGENTS.md tokens, English uppercase UI with existing localization, Room,
  StateFlow, lifecycle-safe collection, and local-first data access. No standalone Kotlin
  Android plugin and no machine paths in repository Gradle properties.
- Desktop/Apple stores remain independent. Portable exchange contains explicit domain records,
  not a copied Android/Windows SQLite file. Existing full local backups remain separate.
- Portable v1 defines complete minimum records before native editing UIs exist: schema-validated
  archival preservation is allowed for known record types without local UI, with honest counts.
  Every platform must pass the same mixed-record lossless round trip. Unknown critical semantics
  are rejected. Preserve anonymous source ownership; never attach unknown history to the active
  handle. Later contract phases must update codecs, schema versions and upgrade/downgrade vectors.
- No OJ passwords, cookies, sessions, browser authentication, automatic submission, or automatic
  POST retry. Luogu OpenApp stays within the existing Android explicit flow; desktop parity
  does not authorize moving credentials or source/input across devices.
- Every platform handles loading, success, empty, error and offline states where applicable;
  edits and imports must survive cancellation, interruption, restore and account changes.
- Native platform APIs, new dependencies, external endpoints and distribution rules are checked
  against primary documentation at the phase that needs them. No future availability is assumed.

## Completion and governance

- Store per-phase status as PLANNED / ACTIVE / BLOCKED / VERIFIED / CUT; initially all 100 are
  PLANNED. VERIFIED requires the phase's own evidence. Separate code, build, runtime and release
  columns prevent a green unit test from masquerading as device acceptance.
- One phase is normally one coherent PR; split only when independent review is useful. Never
  manufacture 100 PRs merely to match numbering. Track phase IDs in PRs and release notes.
- A completed wave is a release-candidate opportunity; independently usable platform changes
  may release earlier. Publish stable binaries only after their platform gates. An explicitly
  labeled docs-only planning prerelease may contain this plan without changing app versions.
- GitHub badges are an outcome of real contributions. Never invent coauthors, self-award accepted
  answers, bypass review, or create empty PRs/releases for badges. Creation of PRs and publishing
  releases are authorized; merging a PR is not inferred from that authorization.
- Pause optional production sync at a written go/no-go decision. If cut, keep the same phase
  numbers and deliver specified offline-integrity alternatives; a cut is recorded, not hidden.

## Review focus and owning phases

1. A supposedly complete legacy phase lacks runtime evidence: 121–125 and 216–220.
2. Cross-platform import loses identity or runs work from an old generation: 126–135, 191–195.
3. Time zones, retries and restart cause duplicate review/session outcomes: 146–155, 171–175.
4. Production code can reach test crypto or an untrusted endpoint: 201–210; network gate closed.
5. Release assets mismatch their tag, signing status or platform readiness: 211–220.

## Deliverable

`docs/LONG_RANGE_PLAN_121_220.md` is the authoritative continuation. Keep the older 78–120 plan
as historical context and link forward from it. Add a concise entry in ROADMAP and README so
the next agent can discover the continuation. This task changes documentation only.
