# Native platform capability and evidence ledger

Execution follow-up: [Android 122 final API 37 acceptance](phase-122-android.md),
[Windows 123 partial acceptance](phase-123-windows.md),
[Linux integration PR #16](https://github.com/tangyixiao/OJ-NEXUS/pull/16), and
[Apple 125 runtime gate](phase-125-apple.md). The original audit snapshot below is retained;
later tests, fixes and preview releases do not silently rewrite its historical evidence.

Audit date: 2026-10-23 (Asia/Shanghai). This is the Phase 121 source/evidence inventory.
It separates implemented code, reachable UI/CLI, build/test results, runtime checks and published assets.
It is not a claim that the four platforms have identical features or release readiness.

## Source identity

| Snapshot | Identity | Evidence boundary |
| --- | --- | --- |
| Main | `11555095e2a66c8dfc4dd9918118ed81d97720da` | Current application source audited here |
| Roadmap PR #11 | `5c81273451d1bde7b6f772ff024090582695666a` | Documentation-only continuation; open/draft at audit |
| Android | versionName `1.0.3`, versionCode `105`, Room schema `15` | Build identity read from source, not a new release |
| Windows | SQLite schema `7`, .NET 8 project targets | SDK availability does not establish successful tests |
| Linux local snapshot | Worktree `codex/linux-client`, HEAD `5c42103839dabc9390aced748083493ff2385450` | Implementation is untracked local work, not contained in that commit or main |
| Apple | Swift Package Core/UI plus macOS and iOS app entry points | Source supports public profile/rating/submission workflows; runtime acceptance is separate |

Each source path below is repository-relative. Linux paths are relative to the existing Linux
worktree until its integration PR is accepted. Listed tests are coverage entry points, not a
claim that a fresh run of every test occurred during this source audit.

## Android

Source root: `app/src/main/java/com/ojnexus/`. Tests: `app/src/test/java/com/ojnexus/` and
`app/src/androidTest/java/com/ojnexus/`. Main code at `1155509`; `5c81273` changes documentation only.

| Capability | Implementation and reachable entry | Coverage entry / remaining boundary |
| --- | --- | --- |
| Notes | `feature/problems/ProblemDetailScreen.kt`, `ProblemDetailViewModel.kt`; `core/model/NoteTemplate.kt`; note scope in `ProblemsScreen.kt` | `ProblemNoteIndexRepositoryTest`, `ProblemNoteIndexTest`, `NoteTemplateTest`, `ProblemNoteIndexComposeTest`; process-loss/conflicting-edit acceptance still belongs to 142 |
| Review | `core/domain/ReviewScheduler.kt`, `core/data/repository/ReviewRepository.kt`; `feature/training/ReviewRunScreen.kt`, `ReviewSessionScreen.kt`, `ReviewDeck.kt`; routes in `app/NexusApp.kt` | `ReviewSchedulerTest`, ReviewRun/ReviewDeck tests; full interruption recovery is not established by a pure scheduling test |
| Sessions | `core/data/repository/TrainingRepository.kt`, session state/clock domain; `feature/training/SessionScreen.kt`, `SessionViewModel.kt`, `SessionDebrief.kt` | `TrainingRepositorySessionTest`, `SessionStateMachineTest`, `SessionMomentumComposeTest`, `SessionCommandDeckComposeTest` |
| Library | Local and remote scopes in `feature/problems/ProblemsScreen.kt` and `ProblemsViewModel.kt`; `core/data/repository/ProblemRepository.kt`; `LibraryWindow.kt` | Filter/window/repository and library handoff tests; current windowed display does not itself prove bounded SQL reads or 60fps on hardware |
| Contests | `feature/contests/ContestCenterScreen.kt`, `ContestFocusScreen.kt`; `core/data/repository/ContestFocusRepository.kt`; routes in `NexusApp.kt` | Contest/Arena model and repository tests; real API availability and race/time changes need phase-specific acceptance |
| Submissions | `feature/submissions/SubmissionCenterScreen.kt`, detail mapping and `core/ui/submission/`; public imports behind `judge/`; explicit Luogu OpenApp jobs separate | Judge mapper/coordinator tests and submission detail tests; public Luogu history remains capability-gated, no login fallback |
| Backup/restore | `core/data/repository/BackupRepository.kt`, `core/data/restore/DatabaseRestoreCoordinator.kt`, `core/data/backup/`; Settings actions | `BackupSnapshotRepositoryTest`, restore protocol tests, `JudgeWipeRepositoryTest`; full local backup is not the future portable cross-platform format |
| Connectors | `judge/` adapters/coordinators/worker; `feature/settings/ConnectorCenter.kt`; `core/data/repository/JudgeAccountRepository.kt` | `JudgeSyncRoutingTest`, `SyncOperationRecordingTest`, `ConnectorCenterComposeTest`, `SyncOperationHistoryComposeTest`; public-data capability boundaries remain binding |
| Analytics | `core/data/repository/AnalyticsRepository.kt`, `core/domain/MasteryEngine.kt`; `feature/analytics/AnalyticsScreen.kt` | `AnalyticsDaoTest`, `MasteryEngineTest`, `AnalyticsFocusLensTest`; deterministic score is not proof of independent learning ability |

The extra sync code in `core/sync/` is a PoC: abstract transport, test crypto and no selected
production AEAD. `MultiHandlePrototype.kt` is explicitly not production wiring; `sync_states`
still has judge-level cursor identity. Neither should be counted as delivered production sync
or isolated multi-handle support.

New Phase 122 runtime findings on the same baseline: the note editor does not observe its
draft/saving state flows, so text input is reset; Settings advertises a snapshot ZIP but still
calls raw SQLite `exportTo`, bypassing the expected archive preview on reimport. Both are
reproducible defects, not supported workflows proved by the green unit/instrumentation suite.
Their scoped fixes and repeat device acceptance must complete before 122 can be VERIFIED.

## Windows

Source root: `windows/src/OjNexus.Windows.{Core,Cli,Desktop}/`.
Test roots: `windows/tests/OjNexus.Windows.{Core,Cli,Desktop}.Tests/`.

| Capability | Implementation and reachable entry | Coverage entry / remaining boundary |
| --- | --- | --- |
| Notes | No note model, editor or notes command in inspected source | Planned; do not infer support from a problem title or SQLite persistence |
| Review | `Core/Domain/ReviewModels.cs`, `Core/Storage/SqliteSyncStore.cs`; `Cli/Program.cs` implements `review due` / `review mark` | `SqliteSyncStoreTests`, `ProgramTests`; **CLI/store only**, no WPF review navigation or controls |
| Sessions | No session lifecycle/store/command in inspected source | Planned |
| Library | `Core/Domain/ProblemModels.cs`, `GetProblemsAsync`; WPF `MainWindow.xaml` PROBLEMS and `DesktopViewModel`; CLI `problems list` | Core/CLI/ViewModel tests; derived from local submission payload, not a full remote catalog/editor; existing UI smoke does not name this view |
| Contests | Rating records contain contest metadata | No standalone calendar, contest detail or Arena UI/CLI; rating metadata is not that feature |
| Submissions | `Core/Network/*Adapter.cs`, `SyncPayloadModels.cs`, SQLite payload persistence; CLI `data`, connector summaries | `CodeforcesPayloadPersistenceTests`, `AtCoderPayloadPersistenceTests`, `LuoguPayloadPersistenceTests`; no dedicated WPF timeline/detail navigation |
| Backup/restore | No user-facing backup/import/restore operation in inspected source | SQLite durability is not backup support |
| Connectors | `Core/Sync/SyncService.cs`, public adapters; WPF dashboard/connectors/history; CLI config/sync/history | Adapter/service/transport/identity/CLI contract tests; per-handle configuration, cancellation and typed failures present |
| Analytics | Dashboard/profile/rating/count summaries | No training/mastery analytics workflow; summary projection tests do not establish one |

`MainWindow.xaml` currently has Dashboard, Connectors, Sync History and Problems. The existing
`ui-smoke.ps1` visits only the first three; Phase 123 must add or execute actual Problems
coverage. A nonexistent Review page must not be marked as visited.

Distribution source exists: `scripts/package.ps1`, `verify-package.ps1`,
`build-installer.ps1`, `sign-binaries.ps1` and installer metadata. The Windows CI installs
Inno Setup and attempts the installer build. This establishes a build path, not a trustworthy
signature, clean-VM installation result or published Windows release asset.

## Linux

Source inspected in the separate `codex/linux-client` worktree; **not in main**.

| Capability | Local source and entry | Coverage entry / remaining boundary |
| --- | --- | --- |
| Notes | No note domain/store/editor found | Planned |
| Review | No review domain/store/UI/CLI found | Planned |
| Sessions | No session lifecycle/store/UI/CLI found | Planned |
| Library | `linux/src/core/include/ojnexus/core/store.h`, `src/core/store.cpp`: problem writes/counts | `tests/core/SqliteStoreTest.cpp`, adapter tests; no standalone QML library or CLI problem-list workflow |
| Contests | Core payload/store contest writes and counts | Core adapter/store tests; no native calendar or contest detail workflow |
| Submissions | Core recent-submission query, `src/gui/viewmodel.cpp`, `src/gui/qml/Main.qml` connector rows | Core and GUI-model tests; limited inline list, no separate timeline/detail workflow |
| Backup/restore | No user-facing backup/restore command or Qt page | Storage recovery tests are not a backup product entry |
| Connectors | Core adapters/HTTP/sync/store; CLI runner/parser; Qt dashboard/connectors/history | `AdapterTest`, `DomainTest`, `PathsTest`, `SqliteStoreTest`, `SyncEngineTest`, `CommandParserTest`, `MainViewModelTest`, QML smoke |
| Analytics | Native dashboard profile/rating/count summaries | No training/mastery analytics workflow |

Integration finding: the Linux HEAD is an ancestor of main, and `main..linux-HEAD` has no
commits. Existing implementation is untracked `linux/` and `.github/workflows/linux.yml`,
plus local edits to `.gitignore`, `AGENTS.md` and three docs. There is nothing to cherry-pick.
The old-HEAD reverse diff removes newer work and must never be used as an integration patch.
Phase 124 must form a finite source/test/packaging inventory on a fresh main-based branch,
preserve hashes/provenance, exclude build products and leave the source worktree untouched.
Do not copy stale AGENTS.md wholesale: its security replacement would lose current global and
Apple rules. Linux-specific docs must be reconciled additively.

## Apple: macOS and iOS

Source: `apple/Sources/OJNexusCore/`, `OJNexusUI/`, `apple/Apps/`, `apple/Package.swift`.
Existing tests are in `apple/Tests/OJNexusCoreTests/DomainTests.swift` (Core and UI-model tests).
Both app targets exist; neither is an empty project. Both require their own runtime evidence.

| Capability | Implementation and reachable source entry | Coverage entry / remaining boundary |
| --- | --- | --- |
| Notes | No note domain/store/editor found | Planned |
| Review | No review scheduler/store/UI found | Planned |
| Sessions | No session lifecycle/store/UI found | Planned |
| Library | Submission records have problem metadata only | No independent problem library model/store/tab; metadata is not library support |
| Contests | RatingChange contains contest metadata | No standalone calendar/detail workflow |
| Submissions | `CodeforcesAdapter.swift`, `AtCoderAdapter.swift`, `Domain.swift`, `LocalWorkspaceStore.swift`; `NexusDashboardModel.swift`, connector inline list in `NexusRootView.swift` | Decode/roundtrip/sync/cache/bounded-list tests; CF/AC supported, Luogu unsupported; no separate full timeline |
| Backup/restore | Active `LocalWorkspaceStore` actor persists workspace state with `.atomic`; legacy `LocalLedgerStore` exists | Workspace decoding/roundtrip tests; no user backup/export/import entry; verify the active store before changing persistence |
| Connectors | `Adapter.swift`, judge adapters, `NexusDashboardModel.swift`, `NexusRootView.swift` | Public profile for three OJs; CF/AC rating+submissions; configuration/disable/remove/sync/cancel/retry/history implemented; Core/UI-model tests do not prove app interaction |
| Analytics | Cached profile/rating and dashboard summaries | No training/mastery analytics workflow |

## Build, runtime and release evidence

These rows identify exact existing records inspected on the audit date. Later repairs and local
execution are recorded in their own phase reports; they do not rewrite this baseline snapshot.

| Platform / source | Build and tests | Runtime/device | Published binary |
| --- | --- | --- | --- |
| Android main `1155509` | [Oct 18 CI](https://github.com/tangyixiao/OJ-NEXUS/actions/runs/39125622520) passed | CI emulator passed; no physical-device proof inferred | `v1.0.3` APK + SHA256 only |
| Android roadmap `5c81273` | [Oct 23 CI](https://github.com/tangyixiao/OJ-NEXUS/actions/runs/39954220991): build/release/emulator passed; previous local baseline 779/0 | Emulator evidence, not real hardware; new Phase 122 execution tracked separately | Roadmap prerelease contains documents only |
| Windows main `1155509` | [Oct 18 job](https://github.com/tangyixiao/OJ-NEXUS/actions/runs/39125622549/job/117670010060) passed tests/build/package/installer | No fresh interactive or clean-VM acceptance from that job | No Windows binary in `v1.0.3` |
| Windows roadmap `5c81273` | [Oct 23 job](https://github.com/tangyixiao/OJ-NEXUS/actions/runs/39954221098/job/120415908630): 165 pass / 1 fail; subsequent build/package/installer skipped | Not established by failed CI | No new Windows release |
| Linux local working snapshot | Test sources/build products exist; no fresh result accepted by this audit | WSL tool availability only; native Qt interaction not proven | No Linux release or main integration at audit |
| Apple roadmap `5c81273` | [Oct 23 job](https://github.com/tangyixiao/OJ-NEXUS/actions/runs/39954508475/job/120416864497): Core/UI-model tests, macOS build and iOS Simulator compilation passed | Workflow does not launch either app or operate UI | No macOS/iOS binary in `v1.0.3` |

Confirmed Windows failure: the test fixes mark time at `2026-10-18`, but the due query reads
the real system date. A FAIL record due `2026-10-19` is due when the CI runs on `2026-10-23`.
Phase 123-a must introduce a controllable query clock and deterministic day-boundary tests,
not weaken `Assert.Empty` or replace the fixed date with a moving wall-clock assertion.

## Remaining acceptance debt

- **Legacy 102/103 → 125:** existing Apple source/build evidence remains; macOS/iOS launch,
  offline restart, interaction, distribution/signing each still need their own evidence.
- **Android → 122/182/187/212:** physical-device startup/first interactive frame, long navigation
  memory walk, actual TalkBack, narrow-screen large text, battery and query timings remain
  separate from pure tests or helpers. A 1500ms verdict function does not measure startup.
- **Windows → 123/183/213:** fix date-dependent CI, exercise Problems and CLI review across
  restart, expand smoke truthfully, verify clean-VM install and signing independently.
- **Linux → 124/184/214:** integrate uncommitted source safely, run native build/CTest plus
  public-adapter and offline behavior, then establish actual Qt/desktop/package evidence.
- **All platforms → 131–135:** portable workspace exchange is planned; no current full database
  backup may be advertised as compatible cross-platform transport.

## Platform tag and release naming

| Platform | Tag namespace from this cycle | Asset identity and release rule |
| --- | --- | --- |
| Android | Preserve existing `v<SemVer>` | APK/AAB names include Android app version; versionCode strictly increases above 105 for a new app build |
| Windows | `windows-v<SemVer>` | Win-x64 ZIP/installer with numeric package version; RC suffix belongs to release tag if current packaging script accepts only three numeric components |
| Linux | `linux-v<SemVer>` | Tested distro/architecture-native package; no generic claim of all Linux distributions |
| macOS | `macos-v<SemVer>` | Separate native artifact with actual signing/notarization status |
| iOS | `ios-v<SemVer>` | Channel-appropriate artifact; unsigned simulator build is not an installable device release |
| Planning | `roadmap-121-220-v<N>` | Docs-only prerelease, `latest=false`, no app version bump |

These namespaces are fixed; future numeric versions, SDK requirements and release dates are
not preassigned. Versioned assets, tag and exact source commit must agree. An RC on a reviewed
feature branch can state its unmerged status; a stable release needs every applicable gate.
No tags are rewritten and no PR merge is inferred from release authorization.
