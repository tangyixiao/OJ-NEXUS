# Phase 123-a: Windows review clock verification

## Scope and implementation

`SqliteSyncStore.GetDueReviewsAsync` now derives its cutoff from an optional `IClock.UtcNow`; the existing one-argument constructor call remains valid and falls back to `DateTimeOffset.UtcNow`. The CLI and WPF runtime pass the same system clock instance used by their `SyncService`. The existing `ReviewIntervalLadder.DayIndexFor` conversion preserves machine-local date keys. `MarkReviewAsync` still uses its explicit `now` parameter.

The shared-ladder test now injects a fixed clock at its recorded mark time. A new fixed-clock test verifies not-due, due-on-local-day, and still-due-next-day behavior, and checks the same local-day key as the mark schedule.

## RED / GREEN evidence

- RED: `dotnet test windows/tests/OjNexus.Windows.Core.Tests/OjNexus.Windows.Core.Tests.csproj --no-restore --filter FullyQualifiedName~Store_MarkReviewThenDueListUsesSharedLadderSemantics` failed at line 438 on 2026-10-23. A review scheduled for `2026-10-19` was returned by `Assert.Empty` because the query used the real machine date. TRX: 1 failed, 0 passed.
- API RED: the new fixed-clock test failed to compile because the optional clock constructor did not yet exist (CS1729); this was expected before the production change.
- GREEN: focused tests for the fixed-clock behavior and original shared-ladder behavior passed, 2/2.

## Windows Release gates

All command output is preserved under `%TEMP%\phase-123-review-clock\` with an exit line in each completed command log:

- `dotnet restore windows/OjNexus.Windows.sln`: exit 0.
- `dotnet test windows/OjNexus.Windows.sln --no-restore --configuration Release`: exit 0; TRX totals: Core 81/81, CLI 65/65, Desktop 21/21 (167 passed, 0 failed, 0 skipped).
- `dotnet build windows/OjNexus.Windows.sln --no-restore --configuration Release`: exit 0; 0 warnings, 0 errors.
- `pwsh -File windows/scripts/smoke.ps1 -Configuration Release`: exit 0; CLI reported `status=ready`, exit 0, WPF process remained alive within 15 seconds.
- `pwsh -File windows/scripts/ui-smoke.ps1 -Configuration Release`: exit 0; Dashboard, Connectors, and Sync History rendered, 3/3 screenshots, UI SMOKE PASS.

The CLI smoke only checks status, and UI smoke only covers Dashboard, Connectors, and Sync History; neither script exercises the review due-date flow.

## Repository context and limits

This worktree is pinned to base `11555095e2a66c8dfc4dd9918118ed81d97720da`. At that revision, `docs/LONG_RANGE_PLAN_121_220.md` and the referenced Phase 121–125 kickoff plan are absent, so their Task 3 wording could not be checked here. The task's explicit acceptance criteria were followed. The implementation does not change duplicate-mark behavior, workflows, or version numbers. No push, PR, release, or merge was performed.
