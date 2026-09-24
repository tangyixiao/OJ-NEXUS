# Arena

Arena is the local focus surface for a cached contest. It is entered from the contest center and
keeps the contest clock, cached problem catalog, local submission progress, and personal problem
markers in one screen.

## Data boundary

- `contests` and `remote_problems` are synchronized judge data.
- `problems` and `attempts` are local training history. Arena joins them to cached problems by
  `(judge, external_id)` and shows solved state, attempt count, and the latest verdict.
- `contest_problem_markers` is local-only. Its key is `(judge, contest_id, problem_external_id)`;
  sync never overwrites it.

The marker cycle is `UNMARKED → WORKING → SOLVED → SKIPPED → UNMARKED`. Returning to
`UNMARKED` removes the row instead of storing a meaningless default. Marker writes use the
application clock and remain available offline.

## External actions

Arena opens contest and problem pages through the existing Custom Tabs browser boundary. It does
not embed a web view, scrape pages, submit code, or request passwords/cookies.

## Empty and failure states

An uncached contest is reported as missing local data. A contest without synced remote problems
shows an explicit sync hint. Room-flow failures render the Arena unavailable state; the rest of
the local application remains usable.
