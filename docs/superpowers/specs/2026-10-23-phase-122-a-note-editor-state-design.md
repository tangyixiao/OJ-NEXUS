# Phase 122-a: Android note editor state design

## Problem

`ProblemDetailViewModel.state` currently combines problem detail, knowledge relations, and the note template, but it reads the private `notesDraft` and `notesSaving` flows with `.value`. Updating a note field therefore does not emit a new UI state. The controlled editor can be reset to its previous value while typing, and the saved status does not reflect the actual save operation.

## Scope

- Make `state` emit the latest note draft and save status when either flow changes.
- Keep the draft seeded from the first available stored detail and preserve user edits across later detail, knowledge, and template emissions.
- Retain the existing 600 ms debounce and repository persistence behavior.
- Exercise the real ViewModel and Room-backed repositories in Robolectric tests.

Out of scope: note conflict resolution, process restoration, search, UI redesign, schema changes, backup/settings fixes, and version changes.

## Acceptance

- A collector subscribed to `state` observes a field edit immediately, before the repository's detail flow emits again.
- Consecutive field updates preserve the complete draft.
- After the 600 ms debounce, Room contains the full draft and the observed state reports saving transitions.
- A Room/detail/template/knowledge emission during editing does not replace the user-owned draft.
- Existing note seeding, auto-save, and scope lifecycle behavior remains intact.
