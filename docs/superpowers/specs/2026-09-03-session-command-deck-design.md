# Session Command Deck Design

## Context

OJ NEXUS already persists active training sessions, derives a reactive problem queue, and
supports opening a problem or changing the session lifecycle. The active queue is currently
observational: a user must leave the session to record a result. Phase 69 turns that queue into a
compact local command surface without introducing another session model.

## Goals

- Let a user select one attached problem in an active session.
- Show a `LOG RESULT` command rail for the selected problem with the existing unified verdicts.
- Record the result through `ProblemRepository.addAttempt`, preserving the existing transaction
  that updates both the attempt row and the problem's derived counters.
- Keep progress counts, latest verdict, and solved/pending lanes reactive through the existing
  Room flows.
- Make the selected row and the command rail visibly distinct while preserving the dark,
  telemetry-style NEXUS BLUE design system.

## Non-goals

- No automatic submission to Codeforces, AtCoder, Luogu, or any other judge.
- No database schema or migration changes; an attempt remains the existing domain event.
- No new session states, session-problem table fields, network requests, or credentials.
- No quick action on finished/cancelled history sessions.

## User flow

1. An active `RUNNING` or `PAUSED` session displays its existing progress board.
2. Tapping a queue row selects it and exposes `LOG RESULT` underneath the queue. The selected
   row uses a border/accent treatment and retains its status text, so selection is not conveyed by
   color alone.
3. The command rail displays the selected problem identity and verdict actions for `AC`, `WA`,
   `TLE`, `MLE`, `RE`, `CE`, `PE`, and `OTHER`. Each action has a visible verdict label and an
   accessible description containing the problem identity and verdict.
4. Choosing a verdict immediately calls the existing local attempt repository with no duration,
   language, or note. The rail remains selected so a second result can be logged without opening
   another screen; the Room-backed queue refreshes counts and latest verdict.
5. `OPEN` continues to open the local problem detail. `PAUSE`, `RESUME`, `FINISH`, and `CANCEL`
   remain the existing session lifecycle controls.
6. Selecting another row changes the target without creating any data. Selecting no row hides the
   command rail. Empty queues keep the existing empty state.

## Architecture and data flow

`SessionRunningView` owns only ephemeral selected-problem UI state. It passes the selected
`SessionProblem` and an `onLogResult(problemId, verdict)` callback to a focused
`SessionQuickActions` composable. `SessionScreen` wires that callback to
`SessionViewModel.logAttempt`, while `SessionViewModel` launches
`problemRepository.addAttempt(problemId, verdict)` in `viewModelScope` and leaves all other
session operations unchanged.

The existing `SessionViewModel.sessionProblems` flow observes the session progress query. After
the attempt transaction completes, Room invalidation updates the queue, the latest verdict, the
attempt count, and the progress pulse. No manual counter or optimistic database mutation is
added. A failure is exposed through the existing `SessionActionError.Generic` state and remains
visible below the command rail; the selected problem is not cleared on failure.

## Visual and accessibility rules

- Use existing `NexusTheme`, `NexusSpacing`, `NexusRadius`, `NexusSize`, `NexusMotion`, and
  `NexusTone` tokens; add only named design-system dimensions when a new rail size is required.
- Keep one NEXUS BLUE accent, hairline borders, restrained radii, and uppercase English resource
  labels with Chinese translations.
- Use a 120–300ms selection/content-size transition and `snap()` when `reduceMotion` is enabled.
- Every verdict action is a button-sized target with a visible label and a localized content
  description. No emoji or color-only status is used.

## Error and lifecycle behavior

- Logging is disabled while the session is not live because the quick rail is not rendered.
- A repository failure keeps the selected problem and displays the existing error text; the user
  can retry another verdict.
- A configuration change may restore the selected problem ID with `rememberSaveable`; if the
  restored ID is no longer in the queue, the selection is cleared safely.
- On session finish/cancel or route disposal, the ephemeral selection disappears with the
  running surface and no additional persistence is written.

## Verification

- Unit-test `SessionViewModel.logAttempt` wiring at the repository boundary with a fake or test
  repository, including success and failure mapping.
- Unit-test selection normalization: unknown/removed IDs become no selection and a selected row
  is retained after a failed attempt.
- Compose-test the quick rail's title, problem identity, all verdict labels, click callback,
  selected-row semantics, reduced-motion rendering, and hidden empty state.
- Run `test`, `assembleDebug`, `lintDebug`, and `connectedDebugAndroidTest` on the configured
  Pixel 9 API 37 emulator.
- Install the final `versionName=0.3.67`, `versionCode=67` APK, launch it, manually exercise
  select → log `WA` → refreshed attempt count/status → `OPEN`, and verify no fatal crash.
