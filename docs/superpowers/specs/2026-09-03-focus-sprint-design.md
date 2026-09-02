# Phase 66 — Focus Sprint Design

## Goal

Turn the existing Training review queue and ranked recommendations into a fast, editable
training-session preset. A user should be able to see what the sprint will contain, open the
existing session form with those problems selected, adjust the selection, and start the normal
local training session flow.

## Scope

- Add a pure `FocusSprintPlan` selector over existing due reviews and training recommendations.
- Select at most five distinct problems with deterministic ordering:
  1. overdue/today reviews by due day, due timestamp, then problem ID;
  2. remaining recommendations by descending priority, then problem ID.
- Add a `FOCUS SPRINT` panel to the inactive Training session section with source counts, target
  count, a short problem preview, and an accessible launch action.
- Pre-fill the existing `NewSessionDialog` with `FOCUS`, `25` minutes, `FOCUS SPRINT`, and the
  plan's selected problem IDs. The user can still edit every field before confirmation.
- Reuse `TrainingViewModel.startSession()` and `TrainingRepository.createAndStartSession()`;
  no new persistence, network request, scheduler, credential, or session state is introduced.
- Respect existing NEXUS dark-first tokens, localized resources, reduced motion, and accessibility
  semantics. No gradients, glow, emoji, or marketing copy.

## Data flow

```text
Room/Flow review queue + ranked candidates
                |
                v
       buildFocusSprintPlan(...)
                |
                +--> Training FOCUS SPRINT preview
                |
                +--> NewSessionDialog initial values
                              |
                              v
             existing startSession -> Room transaction -> SessionScreen
```

`FocusSprintPlan` is derived at composition time from the same `TrainingUiState` snapshot already
used by Training. It does not create a review, task, or session until the user confirms the
existing session form. Duplicate problem IDs are represented once, and an empty plan renders an
honest unavailable/empty state with the launch action disabled.

## UI direction

The signature element is a compact vertical blue plan rail: a single accent line beside the
planned item count and a two-metric readout (`TARGETS`, `25 MIN`). The panel is intentionally
quieter than the review pulse so the action reads as an executable plan, not a recommendation.
Problem preview rows show judge, external ID, title, and whether each came from a due review or a
ranked target. A 120–300ms size transition is used when the panel expands or updates and snaps
when reduce motion is enabled.

## Error and empty behavior

- No candidates: show the localized `NO FOCUS TARGETS` state and disable the launch affordance.
- Candidates exist: the launch affordance opens the existing form; it never silently starts a
  session before confirmation.
- Existing active session: the repository's existing transaction guard and action error remain
  authoritative.
- Missing/changed candidate at confirmation: the existing repository failure remains visible in
  the existing session flow; the preset itself is a snapshot and is not silently rebuilt.

## Testing and acceptance

- Pure unit tests cover due-first ordering, recommendation fallback, de-duplication, five-item
  limit, and empty plans.
- UI source/layout tests cover the panel, localized labels, reduced-motion transition, and dialog
  preset values. Route tests remain unchanged because the existing session route is reused.
- Full gate: `test assembleDebug lintDebug --no-daemon --console=plain`.
- Install `versionName=0.3.64`, `versionCode=64` on `emulator-5554`; verify Training shows the
  focus panel, launch opens a pre-filled session form, and the app starts without a fatal
  exception. Capture a screenshot of the panel/form for the release note.
