# Phase 78 — Note Field Templates (design spec)

Date: 2026-10-17. Phase 78 of `docs/LONG_RANGE_PLAN.md`.

## Goal

Structured notes per problem (ROOT CAUSE / KEY INSIGHT / PITFALL) with a per-user editable
template applied to new notes. Existing saved notes must stay byte-identical — the phase
adds no Room migration.

## What already exists

- `core/model/TrainingModels.kt:78` — `ProblemNotes(problemId, keyInsight, implementationNotes,
  complexity, general, updatedAt)`; four free-text columns, one row per problem.
- `core/model/ProblemNoteIndex.kt:7` — `enum NoteField { ALL, KEY_INSIGHT, IMPLEMENTATION,
  COMPLEXITY, GENERAL }` used by the note index for filtering and row previews.
- `core/data/preferences/UserPreferencesRepository.kt:25` — DataStore-backed user preferences
  (reduce motion, haptics, theme slot, per-judge training targets).
- `feature/problems/ProblemDetailViewModel.kt:28` — `NotesDraft` seeded once from storage,
  debounced auto-save, flushed on dispose.
- `feature/problems/ProblemDetailScreen.kt:390` — the NOTE section renders four hard-coded
  `NotesField` labels from `R.string.notes_*`.

## Design

### 1. `NoteSlot` — the four storage columns (new, `core/model`)

`enum class NoteSlot { KEY_INSIGHT, IMPLEMENTATION, COMPLEXITY, GENERAL }`. This is the
storage identity of a note field. `NoteField` (the index filter enum, which also carries
`ALL`) stays exactly as it is — the note index is a storage-level surface and is out of
scope for this phase. Mapping helper `fun NoteField.toSlot(): NoteSlot?` is provided for
callers that need it, but no index code changes.

### 2. `NoteTemplateField` / `NoteTemplate` (new, `core/model`)

```kotlin
data class NoteTemplateField(val slot: NoteSlot, val label: String, val hint: String)
data class NoteTemplate(val fields: List<NoteTemplateField>)
```

Rules, all enforced by pure functions and unit-tested:

- `NoteTemplate.DEFAULT` — the four existing fields in reading order with today's labels
  (`KEY INSIGHT` / `IMPLEMENTATION` / `COMPLEXITY` / `GENERAL NOTES`) and empty hints.
  Defaulting to today's labels is deliberate: shipping ROOT CAUSE / PITFALL as defaults
  would silently relabel text that users already saved.
- `MAX_FIELDS = 4` — one template field per storage slot; the slots are the columns.
- `NoteTemplate.PRESET_ROOT_CAUSE` — a one-tap preset named in the phase goal
  (ROOT CAUSE / KEY INSIGHT / PITFALL), selectable but not default.
- `labelFor(slot)` / `hintFor(slot)` / `contains(slot)` — lookups used by the editor.
- `validate()` — blank labels, blank-after-trim labels, duplicate slots, or more than
  `MAX_FIELDS` fields make the template invalid; the UI refuses to save an invalid template.
- `withField(field)` — add or replace by slot (replace keeps list order).
- `without(slot)` — remove; a template may shrink, but never below one field so the editor
  is never empty.
- `reorder(slots)` — reorder to the given slot order; unknown or missing slots are dropped,
  duplicates ignored. Ordering is what the editor renders, so it is user-visible state.
- `sanitize()` — trim labels/hints, drop duplicate slots keeping the first, cap at
  `MAX_FIELDS`. Used on load: a corrupt template degrades instead of breaking the editor.

### 3. Persistence (DataStore, no migration)

`UserPreferencesRepository` gains:

- `val noteTemplate: Flow<NoteTemplate>`
- `suspend fun setNoteTemplate(template: NoteTemplate): Boolean` — validates first; returns
  `false` (and writes nothing) for an invalid template.

Encoding is a compact string in one `stringPreferencesKey("note_template")`:
`"slot:label:hint;slot:label:hint;…"` with `\` as the escape character for `:` and `;`.
Why a hand-rolled format instead of kotlinx JSON: the payload is four short rows, the
decoder must never throw on user-editable garbage, and the escape rules are trivial to test
round-trip. `NoteTemplateCodec` (in `core/model`) owns `encode`/`decode`; `decode` returns
`null` on anything malformed, and the repository maps that to `NoteTemplate.DEFAULT`.
`sanitize()` also runs after decode, so a hand-edited file can only ever shrink the template,
never break it.

### 4. Editor rendering (`feature/problems`)

`ProblemDetailUiState` gains `noteTemplate: NoteTemplate`. `ProblemDetailViewModel` takes
`userPreferencesRepository`, combines `noteTemplate` into its state, and gains
`setNoteTemplate(template)`.

`ProblemDetailScreen` renders the NOTE section from
`NoteTemplateEditorFields.forRendering(template, savedNotes)`:

```kotlin
fun NoteTemplate.editorFields(saved: ProblemNotes?): List<NoteEditorField>
```

- Template fields in template order, each carrying its label, hint, and the saved text for
  its slot.
- A slot carrying saved text is always rendered, even when the template no longer lists it —
  hiding saved text would lose data, which Local First forbids. Such fields are rendered
  after the template fields, in slot ordinal order, labelled with the slot's default label.
- An empty template renders nothing (the UI then shows an empty state with a reset action).

The editor UI is unchanged in look: same `NotesField` composable, now with the template's
label and an optional hint line under it. A "TEMPLATE" action in the NOTE section header
opens the template editor.

### 5. Template editor UI (`feature/problems`)

A bottom sheet (`NexusBottomSheet`-style `ModalBottomSheet` from Material 3, themed with the
existing design tokens) listing template fields in order with, per row:

- label text field (required),
- hint text field (optional),
- move up / move down buttons (disabled at the ends),
- remove button (disabled when only one field remains),
- an "ADD FIELD" button offering the slots not yet used.

Footer actions: RESET (back to `DEFAULT`), PRESET (ROOT CAUSE / KEY INSIGHT / PITFALL),
SAVE (disabled while the draft is invalid). A live validation line explains why SAVE is
disabled.

All strings go to `res/values/strings.xml`. Labels and hints are user data (like problem
titles), so they are stored and displayed verbatim.

### 6. Tests

- `NoteTemplateTest` (pure JVM): default has four fields in slot order; `validate` rejects
  blank/duplicate/over-max; `withField` replaces in place; `without` never empties;
  `reorder` drops unknown/duplicate; `sanitize` trims and de-dupes; `editorFields` ordering,
  the always-render-saved rule, and the empty-template case.
- `NoteTemplateCodecTest` (pure JVM): round-trip for default and preset; escape of `:`/`;`
  inside labels and hints; malformed payloads (`""`, `"garbage"`, `"KEY_INSIGHT"` alone,
  unknown slot name, truncated row, trailing separator) decode to `null`.
- `NoteTemplatePreferencesTest` (Robolectric, mirrors `TrainingTargetPreferencesTest`):
  round-trip through DataStore; corrupt stored value falls back to `DEFAULT`; an invalid
  template is refused by `setNoteTemplate` and leaves the previous value untouched.

## Out of scope

Note index filter labels (storage-level surface), Room schema, network, backup/export
payloads, Apple clients.

## Definition of Done

Template applies to new notes; existing notes untouched; codec, template, and DataStore
tests green; `assembleDebug` + `test` green; ROADMAP entry appended; version bumped to 76.
