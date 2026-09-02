# Session Review Actions — Design Spec

## Context

Phase 63 added a terminal `SESSION DEBRIEF` for finished training sessions. It exposes solved,
attention, and pending lanes, and shows `OPEN REVIEW` only when a problem already has a review
row. The next useful action is to turn the attention signal into a local review queue without
making the terminal report responsible for navigation or network work.

## Goal

Add a single `SCHEDULE ATTENTION` action to the terminal debrief. It schedules every session
problem in the ATTENTION or PENDING lane that does not already have a review row, using the
existing deterministic review ladder. Once Room emits the inserted rows, the affected debrief
rows must immediately switch from `OPEN` to `OPEN REVIEW`.

## Non-goals

- Do not schedule SOLVED rows through this bulk action.
- Do not restart or overwrite an existing review ladder.
- Do not add network calls, background work, migrations, credentials, or new session states.
- Do not add a confirmation dialog for a local, reversible queue insertion.

## Behavior

1. Derive review candidates in a pure helper: ATTENTION/PENDING rows with `inReview == false`,
   preserving session order.
2. The repository validates all candidate problem IDs before writing. It inserts stage-0 review
   rows due according to the existing `ReviewScheduler.initialSchedule` policy inside one Room
   transaction and returns the number scheduled.
3. The ViewModel exposes one action that schedules the current candidate IDs. Empty candidate lists
   are a successful no-op; repository failures surface through the existing session action error.
4. The panel shows a compact action row with the candidate count. The action disappears when no
   candidates remain and a quiet `REVIEW QUEUE READY` status replaces it when the panel contains
   at least one already queued problem.
5. Existing row navigation remains authoritative: `OPEN REVIEW` routes to the review screen and
   `OPEN` routes to problem detail. Reactive Room state, not a local optimistic flag, drives the
   label and accessibility description.

## UI direction

Use the existing dark telemetry surface: one accent action strip below the lane filters, a small
uppercase count label, and the current restrained row animation. No new colors, gradients,
icons, or decorative looping motion are needed. The action must remain readable with Chinese
localization and font scaling.

## Verification

- Pure unit tests cover candidate filtering, solved exclusion, already-queued exclusion, and
  source-order preservation.
- Repository tests cover one transaction scheduling multiple valid problems, no-op empty input,
  and all-or-nothing behavior when an ID is missing.
- A source-layout test proves the terminal panel exposes the schedule action and keeps both row
  destinations.
- Full Gradle test, debug assemble, and lint gates must pass. Install the APK, use a finished
  session with attention/pending rows, tap `SCHEDULE ATTENTION`, and verify the rows switch to
  `OPEN REVIEW` without a fatal exception.

