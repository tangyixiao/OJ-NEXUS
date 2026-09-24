# Phase 67 — Command Palette Direct Jump Design

## Goal

Turn the existing command palette from a static route list into a useful local jump surface.
Users can type a judge-prefixed problem query such as `cf 1029e`, `atcoder abc 242g`, or
`luogu p4551`, or use `search segment tree`, and open the existing Problems screen with the
corresponding local query already applied.

## Current context

`CommandPalette` already filters nine static commands and `NexusApp` executes their stable IDs.
`ProblemsScreen` and `ProblemsViewModel` already support local text and judge filters, but the
screen has no one-shot initial-query contract. The command palette is the right entry point for
the product-spec command syntax; the implementation should reuse the existing problem filter
and navigation instead of creating a second search system.

## User-visible behavior

1. The palette continues to show the existing route commands when the query is blank or ordinary.
2. A recognized direct query shows a highlighted `DIRECT QUERY` row above the ordinary command
   results. Its visible copy includes the normalized judge and query, and selecting it opens the
   local Problems library with both values prefilled.
3. Supported forms are `cf <query>`, `codeforces <query>`, `atcoder <query>`, `ac <query>`,
   `luogu <query>`, `lg <query>`, and `search <query>`. The query may contain spaces so contest
   problem IDs and title searches remain usable.
4. Unknown or blank queries preserve the current `NO MATCHING COMMANDS` behavior. No remote
   catalog is opened and no data is fabricated when the local library has no match.
5. A direct query is one-shot: after Problems consumes it, later visits use the user's normal
   current filter state.

## Architecture and data flow

Add a pure `PaletteQuery` parser in `CommandPalette.kt` (or a focused sibling file):

- `PaletteQuery.SearchProblems(judge: JudgeId?, query: String)` is the only new action.
- `parsePaletteQuery(raw: String)` trims whitespace, matches the first token case-insensitively,
  maps judge aliases, and rejects empty payloads. A bare `search` has no action.
- Existing static commands remain stable IDs and continue to use `filterCommands`.

`NexusApp` stores a screen-local pending search request, passes it into `ProblemsScreen`, and
clears it through an explicit `onInitialSearchConsumed` callback after `ProblemsViewModel` calls
the existing `setJudge` and `setQuery`. The normal `problems` route and bottom-bar behavior stay
unchanged; only the command execution path supplies the optional context.

## UI direction

Keep the dark telemetry surface and single NEXUS BLUE accent. Add one signature treatment: the
recognized direct query is a compact blue-rail row labeled `DIRECT QUERY`, making the typed
command feel like an executable instrument readout rather than another menu item. Preserve the
existing restrained radius, named spacing, touch targets, and 120–300ms reduced-motion policy.
All new labels and content descriptions are localized in the default and Simplified Chinese
resource files.

## Error, accessibility, and scope

The parser is deterministic and local. It never calls the network, opens a WebView, changes Room,
or invents search results. Direct-query and ordinary command rows expose button semantics and
visible labels. Problems retains its existing loading, empty, and error states; a no-match local
query simply uses the existing no-match state.

## Testing and release identity

Pure tests cover aliases, case/whitespace normalization, multi-word payloads, blank/unknown input,
and judge-less search. Screen/source tests verify the one-shot initial-query contract and the
direct-query UI path. The phase release is `versionName=0.3.65`, `versionCode=65`.
