# PHASE 82 — SESSION DEBRIEF EXPORT (MARKDOWN)

## Goal

One tap copies the debrief of a finished training session to the clipboard and offers the same
text through the Android share sheet. The exported document is plain Markdown, English
telemetry style, and derived entirely from data already on screen.

## Scope

- `feature/training/SessionDebrief.kt` — the Markdown builder plus the debrief export payload.
- `feature/training/SessionScreen.kt` — an EXPORT action in the debrief panel header row.
- `core/ui/LocalAppContainer.kt` — expose the application `Context` so the screen can reach the
  clipboard and the share sheet.
- Strings in `res/values/strings.xml` + the zh-rCN mirror.

Out of scope: exporting history lists, exporting notes, images, PDF, or any new permission.

## Constraints

- Generated text is English telemetry style (uppercase section labels, no marketing copy, no
  emoji). Problem titles and handles are data, not copy.
- No new permissions: clipboard writes and `ACTION_SEND` need none.
- The builder is pure and takes every value it needs as an argument — no `Context`, no clock, no
  repository. Dates and durations arrive pre-formatted so the tests stay deterministic.
- A clipboard write must never crash the screen when no clipboard service exists.

## Design

### Payload

```kotlin
data class SessionDebriefExport(
    val title: String,
    val typeLabel: String,
    val stateLabel: String,
    val startedAtLabel: String,
    val finishedAtLabel: String,
    val durationLabel: String,
    val targetLabel: String?,
    val noteLabel: String?,
    val solvedCount: Int,
    val attemptCount: Int,
    val reviewCount: Int,
    val rows: List<SessionDebriefExportRow>,
)

data class SessionDebriefExportRow(
    val judge: String,
    val externalId: String,
    val title: String,
    val difficulty: Int?,
    val attempts: Int,
    val lane: SessionDebriefLane,
    val verdictLabel: String?,
    val reviewLabel: String?,
)
```

Labels that come from resources (`typeLabel`, `stateLabel`, `verdictLabel`, `reviewLabel`) are
resolved in the composable, so the builder never touches Android.

### Builder

```kotlin
fun buildSessionDebriefMarkdown(export: SessionDebriefExport): String
```

Sections, in order:

```
# SESSION DEBRIEF
- TYPE / STATE / STARTED / FINISHED / DURATION / TARGET / NOTE
## SUMMARY
- SOLVED: n
- ATTEMPTS: n
- IN REVIEW: n
## PROBLEMS
| JUDGE | ID | TITLE | RATING | ATTEMPTS | STATUS |
```

- Optional lines (`TARGET`, `NOTE`, `FINISHED`, `DIFFICULTY`, verdict and review cells) are
  omitted when their value is null, never rendered as empty.
- An empty problem list renders `NO PROBLEMS LOGGED IN THIS SESSION.` instead of a header-only
  table.
- Values containing `|` or newlines are escaped (`\|`, a space) so a title can never break the
  table.
- The document ends with exactly one trailing newline.

### UI

`SessionSummaryView` passes an `onExport: () -> Unit` into `SessionDebriefPanel`. The panel gains
a trailing row beside the filter chips with an `EXPORT` chip and a one-line hint. The action
writes the Markdown to the clipboard and fires an `ACTION_SEND` chooser with
`text/plain`. Both failures are swallowed — the screen must stay usable.

## Tests

`SessionDebriefExportTest` (pure JVM):

1. full session → every line present, one row per problem, table header once;
2. partial session (no target, no note, unfinished) → optional lines absent;
3. empty session → `NO PROBLEMS LOGGED IN THIS SESSION.`, no table;
4. `|` and newline in a title are escaped;
5. trailing newline is exactly one.

## Boundary

The feature adds no network request, no Room migration and no credential flow; it only formats
text that is already local into Markdown.
