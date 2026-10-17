# Phase 78 — Note Field Templates (execution plan)

Date: 2026-10-17. Spec: `docs/superpowers/specs/2026-10-17-note-field-templates-design.md`.

Ordered steps. Do not batch unrelated edits; each step ends compilable.

1. **`core/model/NoteTemplate.kt`** (new): `NoteSlot`, `NoteTemplateField`, `NoteTemplate`
   (`DEFAULT`, `PRESET_ROOT_CAUSE`, `MAX_FIELDS`, `labelFor`, `hintFor`, `contains`,
   `validate`, `withField`, `without`, `reorder`, `sanitize`), `NoteEditorField`,
   `NoteTemplate.editorFields(saved)`, and `NoteTemplateCodec` (`encode`, `decode`).
   No Android imports — pure JVM.
2. **`core/data/preferences/UserPreferencesRepository.kt`**: add `noteTemplate: Flow<...>`
   and `setNoteTemplate(...): Boolean` on key `note_template`, decode-failure → DEFAULT.
3. **`app/src/main/res/values/strings.xml`**: template editor strings
   (`notes_template_*`) and note hint rendering.
4. **`feature/problems/ProblemDetailViewModel.kt`**: accept `userPreferencesRepository`,
   expose `noteTemplate` in state, add `setNoteTemplate`. Keep debounced notes save.
5. **`feature/problems/ProblemDetailScreen.kt`**: pass the repository in the factory; render
   NOTE fields from `noteTemplate.editorFields(saved)`; add the TEMPLATE action and the
   template editor sheet.
6. **Tests**: `app/src/test/java/com/ojnexus/core/model/NoteTemplateTest.kt`,
   `.../NoteTemplateCodecTest.kt`, and
   `app/src/test/java/com/ojnexus/core/data/preferences/NoteTemplatePreferencesTest.kt`.
7. **Gate**: `.\gradlew.bat assembleDebug`, `.\gradlew.bat test`.
8. **Version**: `versionCode = 76`, `versionName = "0.3.76"` in `app/build.gradle.kts`.
9. **Docs**: prepend `## PHASE 78 — NOTE FIELD TEMPLATES` to `docs/ROADMAP.md`; mark
   Phase 78 done in `docs/LONG_RANGE_PLAN.md`.
10. **Commit**: `feat(android): note field templates`.

## Constraints

- No new Gradle dependencies; no Room schema change; no network; no credentials.
- Strings via `res/values/strings.xml`; no `Color(0xFF...)` in feature code; dark-first
  NEXUS BLUE; no emoji; motion 120–300ms and reduce-motion safe.
- Saved note text is never hidden or rewritten by the template.
