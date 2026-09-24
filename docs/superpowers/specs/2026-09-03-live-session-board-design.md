# Phase 62 — Live Session Board / 实时训练队列

## Context

OJ NEXUS already persists a training session lifecycle and can derive elapsed active time from
the session's wall-clock snapshots. The active Session screen currently exposes only the session
type, timer, target, and an attached-problem count. That leaves the user without a live view of
which selected problem is solved, attempted, or still waiting, and without a direct route from a
session to the problem workspace.

## Goal

Turn the live session into an actionable training queue without changing the session state
machine, adding a database migration, or introducing network work. The user should be able to
answer three questions at a glance:

1. How much of the target session is complete?
2. Which attached problems are solved, attempted, or pending?
3. How do I open one of those problems now?

The same progress rows should remain available when opening a finished or cancelled session, so
history is useful for review rather than being only a counter summary.

## Visual direction

- Subject: a focused competitor using a local session as a pit board during practice.
- Palette: existing dark NEXUS background and surface, NEXUS BLUE for active progress, success for
  AC, warning for paused/attempted state, and neutral text/borders for pending data.
- Type: existing `displayData` for elapsed time, `data` for problem identity, and
  `sectionLabel` for compact telemetry labels.
- Layout: a compact `SESSION PULSE` block, followed by a `PROBLEM QUEUE` section. Each row uses a
  three-pixel status rail, judge/external ID, title, attempt count, status text, and an `OPEN`
  action.
- Signature: one thin progress rail under the pulse metrics. Its width represents the ratio of
  solved attached problems to total attached problems, while the textual metrics remain the
  source of truth for accessibility.
- Motion: 120–300ms content/rail updates only when data changes; snap transitions when reduced
  motion is enabled. No looping or ambient animation.

## User flow

1. Open Training and start or resume a session.
2. Session Pulse shows elapsed time, target, attached count, solved count, and a determinate rail
   when problems are attached. With no attached problems it shows a neutral empty state rather
   than implying progress.
3. Problem Queue lists each attached problem. A row is marked `SOLVED` when an AC attempt exists
   inside the session window, `ATTEMPTED` when one or more non-AC attempts exist, and `PENDING`
   when no attempt exists.
4. Selecting `OPEN` navigates to the existing local problem detail route. The user can then use
   the existing workspace/review actions and return to the session.
5. A finished or cancelled session shows the same queue as a historical snapshot derived from the
   persisted session bounds. Existing summary metrics and lifecycle controls remain unchanged.

## Architecture and data flow

### Data

- Add a Room projection for session problem progress. It joins
  `training_session_problems`, `training_sessions`, `problems`, and `attempts`.
- Count only attempts where `timestamp >= started_at` and, for terminal sessions,
  `timestamp <= finished_at`. Aggregate `attempts` and whether any verdict is `AC`.
- Expose the projection as a `Flow` from `SessionDao` and map it in `TrainingRepository` to the
  existing `SessionProblem` domain model. Room invalidation keeps the list current when attempts
  sync or are manually recorded.
- Keep the existing suspend summary path as the fallback for finished-session summary computation;
  the new flow is the screen's display source and must not duplicate network logic.

### UI and navigation

- `SessionViewModel` exposes `sessionProblems: StateFlow<List<SessionProblem>>` for both the active
  route and a historical session route. It derives pulse counts from that list.
- `SessionScreen` accepts an `onOpenProblem(Long)` callback and renders the board for live and
  terminal sessions.
- `NexusApp` wires the callback to the existing `problem/{id}` route. No new destination is added.
- All new labels and content descriptions are added to both `values/strings.xml` and
  `values-zh-rCN/strings.xml`.
- UI values use `NexusSpacing`, `NexusSize`, `NexusRadius`, `NexusMotion`, and existing theme
  colors. Feature code does not add raw colors, new arbitrary radii, or looping effects.

## State and edge cases

- Loading/error behavior continues to use the existing `Loadable` state. A projection failure
  must render the existing screen error instead of a fabricated queue.
- No attached problems: show `NO PROBLEMS ATTACHED` and a neutral rail; solved ratio is not
  computed as 100%.
- A problem deleted after the session was created: omit it from the live join and keep the
  existing count/summary behavior safe; the row must never crash on a missing title.
- Unknown verdicts count as attempts but never as solved. The status text is always present, so
  state is not conveyed by color alone.
- The screen remains usable at larger font scales and with reduced motion enabled.
- Opening a problem is a local route operation. No browser, credential, or submission behavior is
  added by this phase.

## Verification

- Unit-test the SQL projection/repository mapping for pending, attempted, solved, outside-window,
  and empty-session cases.
- Unit-test pulse ratio/count derivation, including no attached problems and unknown verdicts.
- Keep existing session state-machine and repository tests green.
- Run `test`, `assembleDebug`, and `lintDebug`.
- Install the resulting APK on `emulator-5554`, verify the package identity, launch the app, and
  inspect the Training screen and an active session for fatal exceptions. Do not seed demo data
  merely to claim a runtime state; if the local database has no session, verify the honest empty
  state and record that boundary.

## Scope boundary

This phase does not add automatic session completion, background timers, network submission,
custom-input execution, new session states, editable session metadata, or a schema migration.
