# OJ NEXUS — Next Three Phases Design

## Intent

OJ NEXUS has completed Phase 69, where an active local training session can select a queue row
and record an existing verdict. The next work is intentionally ordered as three connected
vertical slices:

1. Phase 70 — SESSION MOMENTUM: turn result logging into a complete training loop.
2. Phase 71 — OJ CONNECTOR CENTER: make foreground OJ synchronization and result handling easier
   to understand and operate.
3. Phase 72 — DASHBOARD COMMAND SURFACE: give the app a stronger operational overview that
   consumes the stable local signals from the first two slices.

The slices remain native Android, local-first, and compatible with the current Kotlin, Compose,
Room, Coroutines/Flow, Retrofit/OkHttp, and `JudgeAdapter` architecture.

## Delivery order and boundaries

The phases are sequential, not parallel. Each phase ends with its own tests, build verification,
documentation, commit, and published version. The expected package identities are 0.3.68,
0.3.69, and 0.3.70 respectively.

The work does not add a social feed, chat, AI product surface, compiler, custom-input runner,
main-site passwords, harvested cookies, CSRF state, cloud account, cross-device synchronization,
or automatic background submission. Existing authentication and OpenApp security boundaries are
preserved. No database migration is planned for Phase 70 or Phase 72; Phase 71 adds schema only
if an existing adapter cannot represent a required sync receipt without corrupting current data.

## Phase 70 — SESSION MOMENTUM

### User outcome

After recording AC, WA, TLE, MLE, RE, CE, PE, or OTHER for the selected problem, the user should
immediately understand what changed and what to do next. The running session should feel like a
focused command loop rather than a static list.

### Surface

The active session surface gains a compact `NOW / NEXT / LEFT` momentum rail inside the existing
session section:

- `NOW` identifies the problem whose result was just recorded, or the currently selected problem
  before the first result.
- `NEXT` identifies the first unsolved or otherwise pending problem in the persisted session
  order. A visible `OPEN NEXT` action selects it and routes through the existing local detail
  action.
- `LEFT` shows pending count and, when a target exists, remaining target time.
- A successful quick result keeps the Room-driven queue as the source of truth, then advances the
  ephemeral selection to the next pending row. If no pending row remains, the rail changes to a
  local completion state and points to the existing finish/debrief controls.
- A repository error leaves the current selection visible, exposes the existing localized error
  state, and never advances the queue speculatively.

The existing quick-result rail, `OPEN`, pause/resume, finish, cancel, debrief, and review actions
remain available. No automatic OJ submission is triggered by a local verdict.

### State and architecture

`SessionViewModel` continues to expose Room-backed session and problem flows. A small pure
derivation boundary computes momentum from the current session, ordered `SessionProblem` rows,
elapsed time, and the last successful local action. Page-local selection remains ephemeral and
is normalized when Room removes or changes a row. The ViewModel owns the action-in-flight and
one-shot feedback state so repeated taps cannot create duplicate operations.

The derivation must distinguish pending, attempted, solved, cancelled, and empty states. It must
not infer a submission result from a title, color, or missing field. All new labels and content
descriptions go through the English and Simplified Chinese string resources.

### Verification

- Pure tests cover next-row ordering, all-solved completion, empty sessions, stale selection,
  paused sessions, and failed result logging.
- ViewModel tests cover success, failure, duplicate action suppression, and Room refresh.
- Compose tests cover the rail, selection movement, accessible action names, reduced motion, and
  font scaling-safe layout.
- One real Activity test covers select → log result → next selection → completion state.

## Phase 71 — OJ CONNECTOR CENTER

### User outcome

The user should be able to see which OJ data is current, trigger an explicit foreground sync, and
understand the result of an existing submission without guessing whether the app is still working.

### Surface

The existing profile, settings, and submission routes are composed into a focused connector area:

- One compact row per supported judge shows availability, last successful sync, current operation,
  and the next permitted action.
- Manual sync is explicit and foreground-only. Loading, success, empty, error, unauthorized,
  quota, and offline states each have a text label and actionable recovery guidance.
- The existing Luogu OpenApp submission/result flow remains the only submission path. Its request,
  compile, judge, output, and resource metadata remain visible in Submission Center.
- Codeforces and AtCoder integration starts with their existing public profile, rating, contest,
  problem, and submission synchronization boundaries. Adapter-specific DTOs do not enter core
  models.
- Every operation exposes a stable local receipt: judge, operation, started/finished time,
  imported/updated count, and typed failure when applicable.

### State and architecture

Each judge keeps its own `JudgeAdapter` implementation and network mapping. A repository/use-case
layer coordinates explicit foreground work and writes only validated local snapshots. `StateFlow`
drives the UI; network responses never become the only source of truth, so the last valid local
snapshot remains usable offline. WorkManager is not used for automatic submission or silent sync
in this slice.

The connector surface must not ask for OJ passwords or cookies. Existing Keystore-protected
OpenApp credentials remain local and are never included in submission metadata, logs, release
assets, or error text. Authorization failures clear only the rejected credential according to the
existing repository policy; transient network failures preserve retryable local state.

### Verification

- Adapter contract tests cover mapping, pagination, malformed responses, rate/quota errors, and
  judge-specific partial results.
- Repository tests cover offline retention, receipt persistence, retry boundaries, and no DTO
  leakage into core models.
- ViewModel and Compose tests cover every Loadable state and accessible manual actions.
- MockWebServer tests use deterministic fixtures only and never represent fake live data as real.
- A connected smoke test confirms explicit sync and existing Luogu result recovery without
  clearing local data.

## Phase 72 — DASHBOARD COMMAND SURFACE

### User outcome

On launch, the user should see the most important current state and a clear next action within a
single scan: training momentum, OJ sync health, submission status, and contest timing.

### Visual and interaction system

The Dashboard keeps the NEXUS dark-first telemetry tone and uses the existing design system as the
only source of color, spacing, typography, radii, and motion tokens:

- A primary `NOW` zone presents the active training action or the honest empty state.
- A `NEXT` zone presents one deterministic local action, such as opening the next session item,
  reviewing a due problem, or checking a pending result.
- A `SIGNAL` strip presents sync freshness, submission health, and the nearest contest countdown.
- Sections and hairline dividers provide hierarchy; restrained radii are used only where the
  design system already permits them.
- Motion is limited to meaningful 120–300ms transitions, honors reduce-motion, and never uses
  gradients, glow, particles, looping decoration, or emoji icons.
- Text remains uppercase and operational. It does not become marketing copy or an AI
  recommendation feed.

### State and architecture

Dashboard data is derived from existing local Room/Flow snapshots and Phase 70/71 repositories.
The screen does not call a network client. A small dashboard projection maps source state to
stable sections and handles Loading, Success, Empty, Error, and Offline without color-only
signals. Existing navigation destinations and deep links remain unchanged.

### Verification

- Projection tests cover precedence when multiple signals compete, stale data, empty data, and
  error/offline combinations.
- Compose tests cover all states, large font scale, narrow width, touch targets, semantics, and
  reduced motion.
- A screenshot review checks the dashboard at the default and enlarged font scales.
- A real Activity smoke test verifies cold launch, navigation to the active action, and no fatal
  exception.

## Shared definition of done

Every phase must leave the worktree intentional and documented. Before claiming completion, run
the relevant unit and connected tests, `assembleDebug`, `assembleRelease`, and `lintDebug`; run
`git diff --check`; inspect the final diff for credentials and unrelated changes; install the
exact release APK when an emulator is available; record its package identity and SHA-256; push
the branch; and create the corresponding GitHub Release with the APK and checksum asset.

The phase is not complete when only a process starts or a narrow unit test passes. The UI,
repository boundary, error behavior, accessibility, release metadata, and public artifact must
all agree with the same versioned implementation.
