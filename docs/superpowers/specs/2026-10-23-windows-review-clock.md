# Windows review query clock specification

## Behavior

- `SqliteSyncStore.GetDueReviewsAsync` evaluates its due-day cutoff from an injectable `IClock.UtcNow`.
- Existing construction with only `SqliteConnectionFactory` remains source-compatible and uses the real UTC clock.
- Due-day comparisons continue to use `ReviewIntervalLadder.DayIndexFor`, preserving UTC-to-local day-key behavior.
- `MarkReviewAsync` keeps its explicit `now` argument and existing scheduling semantics.
- Fixed-clock coverage verifies an item is not due before its stored local day, becomes due on that local day, and remains due after the day boundary. The clock and explicit mark instant share the same baseline.

## Out of scope

No changes to review ladder semantics, duplicate-mark behavior, workflows, versions, or other Windows features.
