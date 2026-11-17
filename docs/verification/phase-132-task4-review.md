# Phase 132 — Task-4 independent review (Portable Workspace screen, ViewModel, SAF)

- **Reviewer role:** independent, read-only with respect to the implementation. This review
  writes only this report; no implementation file was created, edited or fixed.
- **Date:** 2026-11-18
- **Worktree / branch:** `D:/AndroidAppCoding/.worktrees/p132-review`, branch
  `codex/phase-132-task-6-review`, fast-forwarded in two steps: `89ba7c0` (merge of PR #49, the
  task-4 integration tip) and then `4b2ceb8` (merge of PR #51, the zh-rCN string-mirror fix), the
  final integration tip.
- **Subject diff:** `git diff aa90880..4b2ceb8 -- app` (7 files, +1349/−1):
  - `app/src/main/java/com/ojnexus/feature/portable/PortableWorkspaceScreen.kt` (new, 552 lines)
  - `app/src/main/java/com/ojnexus/feature/portable/PortableWorkspaceViewModel.kt` (new, 283 lines)
  - `app/src/test/java/com/ojnexus/feature/portable/PortableWorkspaceViewModelTest.kt` (new, 397 lines)
  - `app/src/main/res/values/strings.xml` (+45 appended lines: 1 blank, 1 comment, 43 strings)
  - `app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt` (+12 lines)
  - `app/src/main/java/com/ojnexus/app/NexusApp.kt` (+17/−1 lines)
  - `app/src/main/res/values-zh-rCN/strings.xml` (+43 appended strings, PR #51 commit `16ac7c2`)
- **Delta note:** the only change between `89ba7c0` and `4b2ceb8` is the zh-rCN mirror file; the
  ViewModel, Screen, tests, default strings.xml, SettingsScreen and NexusApp are byte-identical
  across the two tips, so the findings below apply to both.
- **Dependency files skimmed where task 4 depends on them** (merged in earlier PRs #41–#44 and
  controller wiring, outside the task-4 diff): `PortableModels.kt`, `PortableWorkspaceRepository.kt`,
  `PortableRepository.kt`, `PortableCodec.kt` (synthetic/sourceTag block), `PortableArchiveDao.kt`,
  `RoomPortableArchiveStore.kt`, `ContainerViewModelFactory` (`LocalAppContainer.kt`), and the
  AppContainer wiring in `OjNexusApplication.kt` (commit `4009a35`, already inside the `aa90880`
  base — the 5-line `portableWorkspaceRepository` val is therefore NOT part of the task-4 diff,
  which corrects the task statement that listed `OjNexusApplication.kt` as changed).
- **Method:** static read of every line of the subject files at `4b2ceb8`; line-by-line arity
  check of all 43 appended default strings and the 43 appended zh-rCN mirror strings against their
  `stringResource` call sites; fixed-string greps for `Color(0xFF`, `RoundedCornerShape`,
  `.dp`/`.sp` literals, `TODO` in the new feature code; deletion checks on both strings.xml diffs.
  **No Gradle build and no test execution were performed** — the controller's G-A gate
  (`tools/gradlew-local.bat test assembleDebug`) runs concurrently and owns measured results.
  Compile/robot correctness is asserted only by static reading.
- **Scope:** task-4 transport/UI layer only. The codec, the Room store/migration, the repository
  internals and the golden round-trip were reviewed in their own PRs/tasks and are cited here only
  where task 4's contract depends on them.

## Verdict

**PASS-WITH-OBSERVATIONS** — no blocking defect found. The preview/cancel/apply state machine is
correct and honest: preview and cancel perform zero store writes, apply is a no-op without pending
bytes and a single transactional store write when confirmed, SAF cancellation (null URI) is a clean
no-op, the Portable Workspace entry is genuinely separate from whole-DB backup, the dialog reports
every required count, and all 86 new strings (43 default + 43 zh-rCN mirrors) pass the AGENTS.md
gate (positionally correct, uppercase telemetry tone, tokens only). Six P3 observations are recorded
below; none corrupts data or admits an out-of-contract write. The controller's post-takeover
hardening items (in-flight preview cancellation, pending-offer clearing on apply failures,
testSource reset on cancel) are verified complete and correct in Q4.

---

## Q1 — Preview/cancel zero writes; apply single transactional apply; SAF cancellation no-op

**Conclusion: PASS.**

- **Import picker cancel (null URI):** the `OpenDocument` callback returns early before touching any
  state — `if (uri == null) return@rememberLauncherForActivityResult`
  (`PortableWorkspaceScreen.kt:134-135`). No ViewModel call, no flow emission, no write.
- **Export picker cancel (null URI or lost bytes):** resets via `viewModel.cancel()`
  (`PortableWorkspaceScreen.kt:156-160`); `cancel()` clears pending bytes, cancels the preview job
  and calls `repository.cancel()`, which the repository documents and implements as zero store
  writes (`PortableWorkspaceViewModel.kt:239-253`; `PortableRepository.kt:181-185`).
- **Preview is read-only:** the ViewModel's `preview()` delegates to `repository.preview(bytes)`
  (`PortableWorkspaceViewModel.kt:173-210`); the repository's preview is `codec.preview` + `store.load()`
  plus an in-memory conflict enrichment, with the only writes being `mutableState.value` UI-state
  assignments (`PortableRepository.kt:46-58`). The Room write path is exclusively
  `PortableArchiveDao.replaceAll`, annotated `@Transaction` (`PortableArchiveDao.kt:55-56`).
- **Apply is user-confirmed and single-transaction:** the confirm button is the only caller of
  `viewModel.apply` (`PortableWorkspaceScreen.kt:503-511`, wired at `:241`); `apply()` is a no-op
  when `pendingBytes` is null (`PortableWorkspaceViewModel.kt:213-214`), and the repository
  revalidates from bytes and performs exactly one `store.replaceAll` for a non-identical identity
  (`PortableRepository.kt:74-116`) — one Room `@Transaction`, all-or-nothing.
- **Dialog dismissal cannot apply:** `onDismissRequest = onCancel` (`PortableWorkspaceScreen.kt:352`)
  and the CANCEL button both route to `viewModel.cancel` (`:513-522`); the dialog renders only when
  `uiState.previewOpen && preview != null` (`:237-244`).
- **Document transport bounding:** `readDocument` stops at `PortableSpec.MAX_PACKAGE_BYTES` and maps
  empty/unreadable/oversized to explicit failures before any validation
  (`PortableWorkspaceScreen.kt:70-95`, `:144-146`); `writeDocument` reports false on any exception
  and `finishExport(false)` surfaces `DOCUMENT_NOT_WRITTEN` (`:98-107`, `:163-166`;
  `PortableWorkspaceViewModel.kt:164-170`).

## Q2 — Genuinely separate from whole-DB backup

**Conclusion: PASS.**

- The `SettingsScreen.kt` diff is exactly 12 added lines: one `settings_portable_hint` Text and one
  `SettingsAction` row navigating via the new `onOpenPortableWorkspace` parameter
  (`SettingsScreen.kt:90`, `:311-321`). No backup launcher, dialog, state, `SettingsViewModel` or
  `ScheduledBackupSection` line is touched (verified against the diff; `BackupRepository.kt` is absent
  from the whole `app` diff).
- The portable screen owns its own launchers, `pendingImportUri` (`rememberSaveable`), dialog, status
  region and ViewModel (`PortableWorkspaceScreen.kt:120-167`), backed by the separate
  `container.portableWorkspaceRepository` — `PortableRepository(RoomPortableArchiveStore(...))`
  (`OjNexusApplication.kt:114-115`, controller wiring commit `4009a35`) — while whole-DB backup keeps
  `backupRepository`.

## Q3 — Confirmed-import dialog completeness; status states; never color-only

**Conclusion: PASS.**

The `PreviewDialog` (`PortableWorkspaceScreen.kt:344-524`) renders, in order: TEST SOURCE label when
derived (`:364-371`), identity prefix (`:373`), scope/platform (`:378`), generator (`:383`), created
epoch (`:388`), per-collection COUNTS rows for all five `PortableSpec.COLLECTIONS` (`:398-408`),
SOURCES rows with a NONE fallback (`:410-433`), the EDITABLE / STORED-ONLY / REJECTED capability line
(`:435-444`), the SAVED state line for stored-only records (`:445-449`), SKIPPED rows with NONE
fallback (`:451-474`), and CONFLICTS rows with collection/stableId/kind (`:476-500`).

The status region (`:249-312`) handles all five phases honestly, every line carrying text (never
color-only, `:314-340`): Loading as READING / REBUILDING / WRITING (`:256-263`), Error as failure +
reject + a generic fallback for a repository ERROR phase with no local notice (`:265-275`,
`:299-303`), Empty (`:304-310`), Success as EXPORT WRITTEN / APPLIED n upserted, m deleted /
IDENTICAL (`:279-297`). All values come from the preview or apply result — nothing invented client-side.

## Q4 — ViewModel correctness (supersede/cancel races; apply no-op; testSource derivation)

**Conclusion: PASS with observations (F1, F2).**

- **Superseded/cancelled preview cannot surface a stale dialog:** every superseding intent
  (`beginRead`, `preview`, `cancel`) first cancels `previewJob` (`PortableWorkspaceViewModel.kt:91`,
  `:113-114`, `:175-176`, `:240-242`). The `previewOpen = true` update happens only after
  `repository.preview` returns inside the job; on cancellation the `CancellationException` is
  rethrown before any update (`:192-193`). Since `viewModelScope` runs on `Dispatchers.Main.immediate`
  and all intents run on the main thread, a completed-but-unobserved `previewOpen = true` is always
  overwritten by the superseding intent's `local.update` reset (`:180-186`, `:117-124`, `:244-252`).
  The repository state a cancelled job may have left behind is overwritten by the superseding preview
  or reset by `repository.cancel()`.
- **Apply without pending bytes is a no-op:** `val bytes = pendingBytes ?: return`
  (`:213-214`); pinned by tests for the no-preview, post-rejection and post-`beginRead` cases.
- **Cancel never writes:** pending bytes are dropped and `repository.cancel()` only resets state
  (`:239-253`; `PortableRepository.kt:181-185`).
- **testSource derivation (F1, F2):** `isTestSource` checks the generator prefix (case-insensitive)
  or a `SYNTHETIC-TEST-FIXTURE` state message (`:271-282`). The codec's authoritative rule is an
  exact, case-sensitive `genName == "synthetic-fixture-gen"` OR any record's `extra.synthetic == true`
  (`PortableCodec.kt:773`, `:814-816`), so the golden package (`"generator":{"name":"synthetic-fixture-gen",
  "version":"1.0"}`) is detected by the prefix branch, and the KDoc documents the per-record-synthetic
  false-negative limit (`PortableWorkspaceViewModel.kt:268-270`). Divergences are F1/F2 below.
- **Controller hardening items verified (task-4a takeover):** (1) *in-flight preview cancellation* —
  every superseding intent cancels `previewJob` first (`beginRead` `:113-114`, `preview` `:175-176`,
  `cancel` `:240-242`) and the job rethrows `CancellationException` before any `previewOpen = true`
  update (`:192-193`), pinned by tests 8 and 10; (2) *pending-offer clearing on apply failures* —
  `pendingBytes = null` in the `PortableContractException` and generic catch branches of both
  `preview()` (`:195`, `:200`) and `apply()` (`:223`, `:226`), pinned by tests 4, 5 and 12
  (`applyCalls == 0` after each failure); (3) *testSource reset on cancel* — `cancel()` sets
  `testSource = false` (`:250`), as do `preview()`/`beginRead()` (`:184`, `:122`), pinned by test 9
  (`:211`). All three are complete and correct; the one deliberate gap is that `apply()`'s
  `CancellationException` branch leaves pending bytes in place — reachable only via viewModelScope
  death (the explicit `cancel()` owns the reset and clears everything), so it is coherent.

## Q5 — AGENTS.md compliance

**Conclusion: PASS.**

- **Strings:** all 45 appended strings live in `res/values/strings.xml`; the diff is append-only
  (45 additions, zero content deletions, verified by scanning for `^-[^-]`).
- **Arity:** each formatted string matches its call site exactly — `portable_state_source` %1$s ←
  1 string (Screen `:217`); `portable_state_applied` %1$d/%2$d ← 2 ints (`:288-293`);
  `portable_reject_prefix` %1$s/%2$s ← 2 strings (`:270`); `portable_preview_identity` %1$s ← 1 string
  (`:373`); `portable_preview_meta` %1$s/%2$s ← 2 strings (`:378`); `portable_preview_generator` %1$s
  ← 1 string (`:383`); `portable_preview_created` %1$d ← Long for `%d` is valid (`:388`);
  `portable_preview_kv_row` %1$s/%2$d ← string+int at both call sites (`:398-407`, `:422-432`);
  `portable_preview_capability` %1$d/%2$d/%3$d ← 3 ints (`:436-441`); `portable_preview_pair_row`
  %1$s/%2$s ← 2 strings (`:463-472`); `portable_preview_conflict_row` %1$s/%2$s/%3$s ← 3 strings
  (`:488-497`). No-arg strings (`portable_none`, headers, states, failures) are called without args.
- **Design tokens:** greps for `Color(0xFF`, `RoundedCornerShape` and `.dp`/`.sp` literals in
  `feature/portable/` return zero hits; the screen uses `NexusSpacing.*`, `NexusRadius.sm`,
  `NexusSize.dividerThickness`, `NexusTheme.colors/typography` exclusively (the `Color` import at
  `PortableWorkspaceScreen.kt:31` is the parameter type of `StatusLine`, filled from theme colors).
  Raw hex colors exist only in `core/designsystem` where they are allowed.
- **Tone:** every new string is uppercase telemetry style; no emoji, no marketing copy; judge names,
  scope IDs, URIs and wire categories are data, not copy.
- **No TODO stubs, no secrets:** greps return zero hits in the new files.
- **zh-rCN mirror (PR #51, `16ac7c2`):** 43 strings appended to `values-zh-rCN/strings.xml`,
  append-only, name-for-name and order-matching the default file; every positional index is
  preserved exactly (`%1$s`/`%1$d`, `%2$s`/`%2$d`, `%3$d`) — required for translated format
  strings; the zh-rCN resource file is the repository's pre-existing localization channel (the
  `submissions_status_*` mirrors precede this phase), so the AGENTS.md default-file rule is
  satisfied.

## Q6 — Test quality (`PortableWorkspaceViewModelTest`, 18 tests)

**Conclusion: PASS with observations (F5).**

- All 18 `@Test` methods pin real contract behavior; none is a tautology. Highlights: apply forwards
  the exact pending bytes (`:166` with `lastApplyBytes` captured in the fake, `:48`); apply-after-reject
  and apply-after-`beginRead` both prove `applyCalls == 0`; the supersede race proves the stale
  preview started (`previews == 1`) yet `previewOpen` stays false (`:219-232`); dismissal touches
  neither repository method (`:349-350`).
- The idle-loop (`awaitState`, `:353-364`) pumps the paused Robolectric main looper with
  `shadowOf(Looper.getMainLooper()).idle()` and yields with `delay(1)` under a `withTimeout(2_000)`
  bound — no `Thread.sleep`, no wall-clock expectations, and the fake's 60 000 ms delays are always
  cancelled before elapsing (`:184`, `:221`), so no test depends on real timing. This matches the
  established house pattern (`ProblemDetailViewModelTest`, `SessionViewModelTest`,
  `SubmissionCenterViewModelTest` all use the same idle/withTimeout shape).
- The fake mirrors the real repository's PREVIEW state write behind a flag (`previewWritesState`,
  `:50-62`), which is what makes the F2 scenario below expressible.

## Q7 — Navigation

**Conclusion: PASS.**

`NexusRoutes.PORTABLE_WORKSPACE = "settings/portable-workspace"` (`NexusApp.kt:68`); a distinct
composable destination hosts `PortableWorkspaceScreen` (`:311-313`); `onOpenPortableWorkspace` is
passed at all three Settings call sites (`:306-309`, `:318-321`, `:327-330`) and consumed by the new
`SettingsAction` row (`SettingsScreen.kt:90`, `:319`). `DashboardCommand` is absent from the diff.

---

## Findings

Severity scale: P1 blocker / P2 major / P3 minor-observation. No P1 or P2.

| ID | Severity | Location | Description | Recommended action |
|---|---|---|---|---|
| F1 | P3 | `PortableWorkspaceViewModel.kt:272` vs `PortableCodec.kt:773` | The UI test-source heuristic is `generator.startsWith("synthetic-fixture-gen", ignoreCase = true)` while the codec marks a package synthetic only on exact, case-sensitive `genName == "synthetic-fixture-gen"` (plus per-record `extra.synthetic`). A generator like `synthetic-fixture-generator` or a case variant would show the TEST SOURCE label while the archive stores `sourceTag = "PORTABLE-V1"`. Conservative false positive only; the golden package is unaffected; the KDoc documents the false-negative limit (`:268-270`) but not the over-match. | Narrow the check to the exact name (mirroring the codec), or extend the KDoc to state that the label may fire on prefix/case variants. |
| F2 | P3 | `PortableRepository.kt:54-58`; `PortableWorkspaceViewModel.kt:190`, `:273`; `PortableWorkspaceViewModelTest.kt:97-107` | The state-message branch of `isTestSource` is unreachable on the real happy path: `repository.preview` always sets the PREVIEW state with `message = null` before returning, so the branch only fires against a repository contract that differs from the current one. The test exercises it by setting `previewWritesState = false` and seeding the message manually — a legitimate defensive-branch pin, but worth recording as such. | Keep as defense; add a KDoc note that the message branch is a fallback for repository contract evolution. |
| F3 | P3 | `PortableWorkspaceScreen.kt:225-231`; `strings.xml` (`portable_state_saved`) | The ARCHIVE STATE section unconditionally renders "SAVED ARCHIVE PRESENT. EXPORT REBUILDS THE CANONICAL PACKAGE FROM IT." even on a fresh install with an empty archive; the frozen `PortableWorkspaceState` exposes no archive-present flag, so the screen cannot gate it. Export from an empty archive fails honestly (`PortableRepository.kt:148-151`), so nothing is broken — but the standing claim is inaccurate until the first apply. | Reword to a neutral description (e.g. "ARCHIVE HOLDS THE LAST CONFIRMED PACKAGE"), or gate the line on `workspace.phase == APPLIED`, or add an archive-present flag to the frozen state (controller change). |
| F4 | P3 | `PortableWorkspaceScreen.kt:249-312`; `PortableWorkspaceState.Phase.APPLYING` | No explicit UI line exists for two in-flight windows: (a) repository validation between the end of READING and the dialog opening (status region briefly shows the EMPTY line), and (b) `Phase.APPLYING` while the confirmed transaction runs (the dialog stays open, no progress text). Both windows are short and no state is corrupted; all terminal states are handled. | Optionally render a VALIDATING line while a preview is in flight and an APPLYING line for the repository phase. Cosmetic. |
| F5 | P3 | `PortableWorkspaceViewModelTest.kt:183-232` | The "superseded preview" race is pinned via `beginRead` (test 10) and `cancel` (test 8); a literal second `preview()` call as the superseding intent is not directly tested, although the mechanism (`previewJob?.cancel()` first in `preview()`) is identical. | Optional: add a two-`preview()` variant of test 10 if the controller wants belt-and-braces coverage. |
| F6 | P3 (informational) | `PortableWorkspaceScreen.kt:156-160` | A cancelled export picker calls `viewModel.cancel()`, which also aborts any pending import offer. With the confirm dialog open the export button is unreachable, so this is observable only in the narrow window between preview completion and the dialog being composed; the resulting "abort pending work" behavior is coherent and write-free, but the coupling is implicit in a reset-to-idle call. | Accept as documented behavior, or split a lighter `resetTransfer()` intent if the two flows are ever decoupled. |

---

## Claims vs non-claims

**Claims (verified in this review):**
- Import/export SAF cancellations are clean no-ops; preview and cancel perform zero store writes
  through the full chain (screen → ViewModel → repository → store).
- Apply runs only after the confirmed preview dialog and results in at most one `@Transaction`
  `replaceAll` (plus the idempotent no-op path), delegated entirely to the repository.
- The Portable Workspace entry is separate from whole-DB backup in state, dialog, launchers and
  repository; the SettingsScreen change is additive only.
- The dialog shows counts, sources, skips, conflicts, editable/storedOnly/rejected, the TEST SOURCE
  label and the SAVED state line; status region handles Loading/Empty/Error/Success; nothing is
  color-only.
- All 86 new strings (43 default + 43 zh-rCN mirrors) are append-only, positionally correct;
  the default strings carry uppercase telemetry tone, the mirrors are name-for-name.
- The controller's three hardening items (in-flight preview cancellation, pending-offer clearing
  on apply failures, testSource reset on cancel) are present, correct and test-pinned (Q4).
- No raw colors, dp/sp literals, `RoundedCornerShape`, TODO stubs, emoji or secrets in the new code.
- Navigation: route constant, destination and three wired call sites; DashboardCommand untouched.

**Non-claims (explicitly out of scope):**
- This review ran **no builds and no tests**; `BUILD SUCCESSFUL`, Robolectric green results and the
  golden round-trip identity are owned by the controller's G-A gate and the verification record.
  Statements about test behavior are static analyses of the test code, not executions.
- The codec, Room store/migration, repository internals and golden corpus were reviewed in their own
  tasks/PRs and are cited here only at the seams task 4 depends on.
- No fix was applied to any implementation file; findings are reported only.

---

## Closing note

This review covers the task-4 diff at `89ba7c0` plus its frozen-contract seams, against the plan's
review focus and the controller's task-4 additions. The single most actionable item is F3 (the
unconditional SAVED ARCHIVE PRESENT line); F1 is a documented-limits gap worth a one-line KDoc fix.
Both are reported to the controller for routing; neither was fixed in this review. The only artifact
produced here is this report.
