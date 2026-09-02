# Command Palette Direct Jump Implementation Plan

> **For agentic workers:** execute this plan task by task with tests before implementation.

**Goal:** Add deterministic judge-prefixed and free-text local problem search to the existing
command palette, with a polished direct-query result and one-shot Problems prefill.

**Architecture:** Keep static route commands unchanged. Add a pure parser returning a
`PaletteQuery.SearchProblems` action, pass an optional pending request through `NexusApp` into
`ProblemsScreen`, and consume it once through the existing `ProblemsViewModel` filter methods.

## Global constraints

- Native Kotlin + Compose Material 3 only; no network, WebView, Room migration, or new data.
- Preserve dark-first NEXUS BLUE tokens, localized strings, restrained shapes, and reduced motion.
- Keep the existing command IDs and top-level navigation behavior stable.
- Update both default and Simplified Chinese strings.
- Release identity: `versionName=0.3.65`, `versionCode=65`.

## Task 1 — Parser model and tests

Files:

- Create `app/src/test/java/com/ojnexus/app/PaletteQueryTest.kt`.
- Modify or create the focused parser source under `app/src/main/java/com/ojnexus/app/`.

Write failing tests first for:

- `cf 1029e` and `codeforces 1029e` → `JudgeId.CODEFORCES`.
- `atcoder abc 242g` and `ac abc 242g` → `JudgeId.ATCODER` with the full payload.
- `luogu p4551` and `lg p4551` → `JudgeId.LUOGU`.
- `search segment tree` → null judge and the complete payload.
- case/whitespace normalization, blank payload, and unknown prefix.

Implement the smallest pure parser and run the focused test until green, then commit:
`feat: add command palette query parser`.

## Task 2 — Palette direct-query surface

Files:

- Modify `app/src/main/java/com/ojnexus/app/CommandPalette.kt`.
- Modify both string resource files.
- Add a focused UI/source test if the project’s existing test style cannot host Compose tests.

Keep static filtering intact. Parse the current query, render a blue-rail `DIRECT QUERY` row
above ordinary results when recognized, and execute the parsed action. Add visible localized
copy and content descriptions. Use existing `NexusSpacing`, `NexusRadius`, `NexusSize`,
`NexusMotion`, and `NexusTheme`; do not add raw feature colors or looping effects. Ensure the
direct row has `Role.Button` semantics and remains readable under font scaling.

Run parser tests plus `compileDebugKotlin`, then commit:
`feat: add direct query palette surface`.

## Task 3 — One-shot Problems prefill

Files:

- Modify `app/src/main/java/com/ojnexus/app/NexusApp.kt`.
- Modify `app/src/main/java/com/ojnexus/feature/problems/ProblemsScreen.kt`.
- Add a focused source/unit test for consumption behavior.

Change palette execution to accept the parsed action. Store the pending query/judge in app-local
state, navigate through the existing top-level Problems route, and pass optional initial values.
In `ProblemsScreen`, use a `LaunchedEffect` to call `setJudge` and `setQuery`, then invoke
`onInitialSearchConsumed`. Do not overwrite the user's filter on unrelated recompositions, and
do not alter the remote catalog state. Keep the bottom bar selected state correct.

Run focused tests and compile, then commit:
`feat: wire palette searches into problem library`.

## Task 4 — Version and release documentation

Files:

- Modify `app/build.gradle.kts`, `README.md`, and `docs/ROADMAP.md`.
- Create `docs/releases/v0.3.65.md`.

Document Phase 67 in English and Chinese, including supported syntax, local-only behavior, and
the one-shot prefill. Set version code/name to 65/0.3.65. Keep earlier history intact. Commit:
`release: prepare command palette direct jump v0.3.65`.

## Task 5 — Full verification

Run serially:

```powershell
.\tools\gradlew-local.bat test assembleDebug lintDebug --no-daemon --console=plain
```

Install the final APK on `emulator-5554`, clear logcat, launch, open COMMANDS, type a direct
query, and verify the blue-rail direct row and Problems prefill. Capture
`app/build/reports/ojnexus-command-palette-v065.png` and record the final package identity,
stable APK SHA-256, runtime PID, and clean fatal-exception result in the release note. Run a
fresh code review, fix all Critical/Important findings, and leave the worktree clean.
