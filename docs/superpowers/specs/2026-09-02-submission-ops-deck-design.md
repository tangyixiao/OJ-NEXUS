# Phase 61: Submission Ops Deck / 提交运营台

## Context

OJ NEXUS already stores recent Luogu Open Platform jobs locally and exposes foreground result
checking, manual WorkManager recovery, problem workspace navigation, and a four-metric submission
pulse. The current center renders every metadata field for every row at once, which makes a long
history difficult to scan. It also requires users to repeat the same action for each pending or
failed request.

## Goal

Make the local Submission Center faster to scan and safer to operate while preserving the existing
request, polling, recovery, and credential boundaries.

## Design

### Submission pulse actions

The existing pulse remains the first visual anchor: total, pending, ready, and failed counts. Add
conditional manual actions below the metrics:

- `CHECK PENDING` appears when at least one pending job exists and starts foreground checks for all
  currently pending request IDs.
- `QUEUE FAILED` appears when at least one failed job exists and the recovery scheduler is
  available. It manually enqueues recovery for all currently failed request IDs.

Actions operate on a distinct request-ID snapshot, preserve source order, and reuse the existing
per-request busy/error handling. They do not create new jobs, retry POST requests, or run when the
user has not tapped them.

### Collapsible submission inspector

Each recent job is a compact inspector row by default:

- Always visible: job kind, problem display, status, language, updated time, and the actions that
  are valid for that status.
- Expanded on demand: request ID, compile details, judge status, score, output, exit code,
  execution time, memory, and error details already present in the local entity.
- The existing `CHECK RESULT`, recovery, and `OPEN WORKSPACE` actions remain available without
  requiring expansion.

The toggle uses explicit localized `DETAILS` / `HIDE DETAILS` labels and an accessibility
description containing the problem identity and current expansion state. Expansion uses the
existing Nexus motion tokens and snaps when reduced motion is enabled.

### State and data flow

`SubmissionCenterViewModel` exposes whether manual recovery is available and adds bulk commands
that delegate to the existing `checkResult` and `queueRecovery` guards. No repository, Room
entity, network DTO, migration, or API contract changes are needed. Expansion state is screen-local
Compose state keyed by request ID and is not persisted as user data.

### Localization and design-system rules

All new labels and content descriptions are added to both default and Simplified Chinese string
resources. New layout uses existing Nexus spacing, typography, tone, divider, and motion tokens;
no raw colors, gradients, glow, emoji, or marketing copy are introduced. Status remains conveyed
by both text and tone.

## Error and empty behavior

- Empty history keeps the existing empty state and shows no bulk actions.
- A bulk check with no eligible IDs is a no-op.
- A scheduler failure keeps cached rows visible and routes the request ID through the existing
  action-error surface.
- Individual busy guards continue to prevent duplicate foreground checks.
- Unknown statuses remain visible in `ALL` and have no destructive bulk action.

## Tests and acceptance

- Pure tests cover stable pending/failed request-ID selection, duplicate removal, and source-order
  preservation.
- ViewModel tests cover bulk checks, scheduler availability, bulk recovery, and existing
  per-request duplicate/error behavior.
- Localization resource tests continue to enforce mirrored keys and format placeholders.
- Full acceptance requires `test`, `assembleDebug`, and `lintDebug` to finish successfully,
  installation of the versioned APK on the connected emulator, runtime verification of a
  collapsible row and a bulk action, and a post-launch fatal-exception check.

## Scope boundary

This phase adds no password, cookie, session, CSRF state, cloud account, cross-device sync, local
compiler, custom-input runner, background submission, automatic POST retry, or database migration.

The release identity is `versionName=0.3.57` and `versionCode=57`.
