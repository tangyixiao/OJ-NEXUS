# Phase 60: Workspace Execution Cockpit / 工作区执行驾驶舱

## Context

OJ NEXUS already has a native Workspace with local draft persistence, language selection,
custom-input run mode, Luogu Open Platform submission, foreground result polling, and structured
evaluation output. Two user-visible gaps remain: the existing `o2` request field has no control in
the UI, and sample input/output shown by the Luogu problem detail screen is lost when the user
opens the Workspace. The page also has no compact readout of the current editing/execution state.

## Goal

Make the Workspace a clearer local execution cockpit without changing the judge protocol or
inventing execution results. A user should be able to see the current working state, choose the
optimization flag, load a real cached/detail-page sample into standard input, and understand the
latest request state at a glance.

## Chosen approach

Use the existing route and ViewModel boundaries. The problem-detail screen passes the first sample
pair as optional, URL-encoded route context; the Workspace ViewModel owns the editable input and
the `o2` flag. This keeps the feature local to the existing flow, survives navigation recreation,
and avoids a new network request or database migration. Entry points without sample context keep
working exactly as before.

## User experience

### Workspace pulse

Place a compact `WORKSPACE PULSE` section below the problem identity. It reports four facts from
the current state: selected mode, language, source-code line count, and draft status. Counts are
derived locally and update immediately as the user types. The pulse uses existing NEXUS metrics,
hairline dividers, the single blue accent, and reduced-motion-safe value transitions.

### Editor controls

- Keep the existing RUN/SUBMIT mode and language controls.
- Add an explicit `O2` toggle beside the language controls. Its state is wired to the existing
  `WorkspaceViewModel.setO2` and is forwarded unchanged to both run and submit request models.
- Keep code and input as native Compose text fields. No syntax-highlighting dependency is added.
- In RUN mode, show `LOAD SAMPLE` when a sample input was supplied by the problem detail route.
  Loading replaces only the editable input and saves it through the existing local draft path.
  Show `CLEAR INPUT` only when input is non-empty; it clears input but never clears source code.
- Show the supplied sample output in a read-only `EXPECTED OUTPUT` block, clearly separated from
  editable input. If no sample pair is available, the block is omitted.

### Result feedback

Retain the existing request/check actions and structured evaluation fields. Add a restrained state
rail around the result section for IDLE, PENDING, and READY, with text labels and existing tone
tokens so state is never communicated by color alone. Busy actions remain disabled and continue to
use the current foreground polling behavior.

## Data flow

```text
Luogu detail samples -> encoded optional route args -> WorkspaceScreen
                                              -> WorkspaceViewModel initial state
typed code/input/O2 -> local StateFlow + existing draft repository
RUN/SUBMIT -> existing LuoguOpenGateway -> existing poll/evaluation state
```

The route builder must omit blank optional values and preserve the existing PID/title encoding.
Sample strings are display context only; they are not added to Open Platform DTOs, credentials,
logs, or persistent database entities.

## Architecture changes

- Extend `NexusRoutes.workspace` and its Workspace navigation arguments with optional sample input
  and sample output.
- Thread the sample pair from `LuoguProblemDetailScreen` through `NexusApp` to `WorkspaceScreen`.
- Extend `WorkspaceViewModel` with sample context and a `loadSampleInput` action; reuse
  `setInput`/draft scheduling for the user-visible input change.
- Add a small pure `WorkspaceTelemetry` model/function for line and character metrics, with unit
  tests independent of Compose.
- Keep all new UI text in English and Simplified Chinese string resources. Use only existing
  design-system colors, spacing, typography, shapes, motion, and components.

## Empty, error, and safety behavior

- Missing samples: omit sample actions/expected output; the normal manual input field remains.
- Missing credentials: preserve the current settings guidance and disabled action behavior.
- Network/server/result failures: preserve current error mapping and failed-result semantics.
- Oversized input: continue to rely on the existing Open Platform validator; loading a sample does
  not bypass validation.
- No new passwords, cookies, sessions, tokens, background POSTs, or automatic retries.

## Testing and acceptance

- Unit-test route encoding/omission for title and sample context.
- Unit-test telemetry line/character metrics for blank, single-line, and multiline content.
- Unit-test `loadSampleInput`, `setO2`, and forwarding of `o2` for both run and submit requests.
- Run `test`, `assembleDebug`, and `lintDebug` successfully.
- Install the versioned APK on the configured emulator. Verify the Workspace pulse, O2 control,
  sample loading/clearing, expected-output rendering when available, and no fatal app exception.
- Capture a Workspace screenshot and record the APK identity/hash in the release evidence.

## Explicit non-goals

This phase does not add a local compiler, a custom execution engine, syntax highlighting, code
completion, multi-file projects, remote sample fetching from Workspace, or any new judge adapter.
