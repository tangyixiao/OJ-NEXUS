# OJ NEXUS Phase 77 Implementation Plan — Note Index

**Goal:** make locally saved problem notes readable, searchable and reviewable offline by adding a
`NOTE INDEX` scope to the problem library.

**Architecture:** keep the single-module, local-first architecture. Add one flat DAO projection, one
domain model with pure text helpers, one pure filter, and one Compose surface that renders a plain
state object. Nothing is written; nothing new is persisted.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room, Coroutines/Flow, JUnit, Robolectric,
AndroidX Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-09-20-note-index-design.md`

## Global Constraints

- Start from Phase 76 / `versionName=0.3.74`, `versionCode=74`; finish at `0.3.75` / `75`.
- Keep the standalone `org.jetbrains.kotlin.android` plugin unapplied.
- No Room version bump, no migration, no schema change, no new table.
- UI remains English/Chinese localized, uppercase and telemetry-style, with design-system tokens only.
- Never add network access, credentials, background work, a compiler, or automatic submission.
- Each phase ends with the repository Definition of Done; report only evidence actually observed.

---

### Task 1: Define the note-index domain model

**Files:**
- Create: `app/src/main/java/com/ojnexus/core/model/ProblemNoteIndex.kt`
- Create: `app/src/test/java/com/ojnexus/core/model/ProblemNoteIndexTest.kt`

`NoteField`, `ProblemNotes.hasContent()`, `ProblemNotes.textFor(field)`, `ProblemNotes.searchScope(field)`
and `ProblemNoteEntry` (with a derived `status`). Blank input degrades to `null` / excluded text, never
to an empty string shown to the user.

### Task 2: Add the flat index projection and repository flow

**Files:**
- Modify: `app/src/main/java/com/ojnexus/core/database/dao/NoteDao.kt`
- Modify: `app/src/main/java/com/ojnexus/core/database/mapper/Mappers.kt`
- Modify: `app/src/main/java/com/ojnexus/core/data/repository/ProblemRepository.kt`
- Create: `app/src/test/java/com/ojnexus/core/data/repository/ProblemNoteIndexRepositoryTest.kt`

`observeIndex()` joins `problem_notes` with `problems`, aliases every column, resolves the review flag
with `EXISTS(...)`, and orders by note update time desc. The repository drops rows whose four fields are
all blank, so the empty rule is testable without SQL.

### Task 3: Add the pure filter and the ViewModel state

**Files:**
- Create: `app/src/main/java/com/ojnexus/feature/problems/ProblemNoteFilter.kt`
- Create: `app/src/test/java/com/ojnexus/feature/problems/ProblemNoteFilterTest.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/problems/ProblemsViewModel.kt`

`applyNoteFilter` preserves repository order and matches title, external id and the scoped note text.
`noteState` is a `Loadable<NoteIndexUiState>` beside the existing library and remote-catalog states.

### Task 4: Render the NOTE INDEX scope

**Files:**
- Create: `app/src/main/java/com/ojnexus/feature/problems/ProblemNoteIndex.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/problems/ProblemsScreen.kt`
- Modify: `app/src/main/java/com/ojnexus/core/ui/Labels.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rCN/strings.xml`

`ProblemScope` gains `NOTES`; `ScopeSwitcher` becomes three-way. Shared library composables (`EmptyHint`,
`SearchField`, `FilterChip`, pulse metric) become `internal` and are reused instead of duplicated.
Every UI string goes through both string resources.

### Task 5: Device-level coverage and gates

**Files:**
- Create: `app/src/androidTest/java/com/ojnexus/feature/problems/ProblemNoteIndexComposeTest.kt`

Renders the surface from a plain state object: pulse counts, row identity and preview, status text,
scoped-field fallback, row tap, field chip, judge chips, clear-filters visibility, both empty states.
Then run `tools\gradlew-local.bat test assembleDebug assembleRelease lintDebug` and
`connectedDebugAndroidTest`.

### Task 6: Identity and documentation

**Files:**
- Modify: `app/build.gradle.kts`, `README.md`, `docs/ROADMAP.md`, `docs/WORKBUDDY_HANDOFF.md`

Bump the package identity, add the bilingual `PHASE 77` roadmap entry, and record the round in the
handoff. Do not commit or push without explicit authorization, and do not describe install/signing
verification that was not performed.

## Plan self-review record

- The phase adds no persistence, so there is nothing to migrate and nothing to roll back.
- The one risk worth pinning is the blank-notes rule: it is tested at both the model level (pure) and
  the repository level (join + filter + cascade).
- Compose assertions avoid text that is shared between a chip, a pulse label and a row tag; row
  identity is asserted through unique text, and chips are scrolled to before interaction.
