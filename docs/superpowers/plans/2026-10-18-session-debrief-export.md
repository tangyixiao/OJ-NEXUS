# PHASE 82 — SESSION DEBRIEF EXPORT (MARKDOWN) — PLAN

Spec: `docs/superpowers/specs/2026-10-18-session-debrief-export-design.md`.

1. **Model** — add `SessionDebriefExport` + `SessionDebriefExportRow` and
   `buildSessionDebriefMarkdown()` to `feature/training/SessionDebrief.kt`. Escape `|` and
   newlines; omit null optionals; empty list → the `NO PROBLEMS LOGGED` line.
2. **Tests first-run** — create `app/src/test/java/com/ojnexus/feature/training/SessionDebriefExportTest.kt`
   with the five cases from the spec; expect failures until step 1 lands.
3. **Context** — add `val appContext: Context` to `AppContainer` (set from the application
   context) and expose it through `LocalAppContainer`-based access as `container.appContext`.
4. **Share helper** — add `object SessionDebriefShare` in `core/ui` with
   `fun share(context: Context, markdown: String)`: `ACTION_SEND` chooser, `text/plain`,
   `FLAG_GRANT_READ_URI_PERMISSION` not needed, `ActivityNotFoundException` swallowed.
5. **Clipboard** — in the composable, use
   `context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(...))`
   inside `runCatching`.
6. **Screen** — `SessionSummaryView` gains `onExport: () -> Unit`; `SessionScreen` builds the
   payload from `summary` / `problems` / `session`, calls the builder, copies and shares, and
   passes `onExport` down to `SessionDebriefPanel`, which renders the `EXPORT` chip + hint row.
7. **Strings** — `session_debrief_export`, `session_debrief_export_cd`, `session_debrief_export_hint`,
   plus zh-rCN mirrors.
8. **Gate** — `tools/gradlew-local.bat assembleDebug` + `tools/gradlew-local.bat test`.
9. **Version** — `versionCode = 80`, `versionName = "0.3.80"`.
10. **Docs** — prepend the Phase 82 entry to `docs/ROADMAP.md`; mark Phase 82 ✅ in
    `docs/LONG_RANGE_PLAN.md`.
11. **Commit** — `feat(android): export a finished session debrief as Markdown`.
