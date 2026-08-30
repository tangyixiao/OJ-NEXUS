# Luogu Open Platform capability correction

## Context

The current production client exposes a custom-input `run` operation, but the official Luogu
Open Platform specification currently defines the judge submission endpoint as
`POST /judge/problem` and the result endpoint as `GET /judge/result/{id}`. It does not define
the `/run` endpoint used by the app. Treating that path as a supported runner can turn a user
action into a guaranteed HTTP failure and misstates the product capability.

## Decision

- Correct the Retrofit paths to the official `judge/problem` and `judge/result/{id}` paths.
- Remove the production network declaration for `/run`.
- Keep a capability-aware `LuoguOpenGateway` extension point so another explicitly supported
  provider can offer custom-input execution later; the official Luogu client reports it as
  unsupported and fails before any network request.
- Make the workspace read that capability. For the official client it defaults to problem
  submission, hides the custom-input editor and run-mode action, and never sends a fabricated
  run request. Existing test fakes may opt into the capability to preserve coverage of the
  generic workspace state machine.
- Keep official problem judging: it compiles and evaluates submitted code remotely, and the
  existing foreground result query remains unchanged apart from its corrected path.

## Data and security

No new credentials, cookies, sessions, CSRF state, cloud storage, or background requests are
introduced. Source code and custom input remain transient UI state; no unsupported operation
is persisted as a submission job.

## Verification

- Retrofit tests assert the official paths and Basic authorization.
- A focused test asserts custom-input execution is rejected before a request is sent.
- Workspace tests assert the official client starts in submit mode and hides the unsupported
  capability while opt-in fakes retain run-mode coverage.
- Full `test`, `assembleDebug`, and `lintDebug` must pass.

Reference: https://docs.lgapi.cn/open/openapi
