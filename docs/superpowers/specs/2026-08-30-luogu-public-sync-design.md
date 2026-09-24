# Luogu public sync design

## Objective

Add a local-first Luogu synchronization pipeline for the data that is publicly
available without credentials: the bound user's profile and rating history, the
problem catalog, and the contest catalog. The pipeline must be usable offline
after a successful sync and must not add a cloud service.

## Explicit boundary

The Luogu web application exposes structured JSON when requests include the
first-party `x-lentille-request: content-only` transport header. OJ NEXUS will
use that JSON transport only; it will not parse HTML, execute page JavaScript,
harvest browser cookies, or ask for an OJ password.

The anonymous record endpoint returns an authentication/login envelope rather
than public submission rows. The submission stage therefore reports a typed
`AUTH_REQUIRED`/partial result and persists no fabricated attempts. Password,
Cookie, Session, CSRF, auto-submit, editor/compiler/runner, and cloud sync are
outside this stage.

## User-visible behavior

- A bound Luogu account gains a manual “Sync now” action and is eligible for
  the existing periodic background sync mechanism.
- A successful public sync updates the generic profile, rating history,
  contests, and remote problem catalog tables. Repeating a sync is idempotent.
- If public stages succeed but records are authentication-gated, the run is
  `PARTIAL`; the settings UI keeps the existing persisted data and presents the
  typed authorization limitation.
- The app remains functional when Luogu is unavailable: existing cached rows
  remain available and the sync state records the error.

## Transport and endpoint contract

`LuoguApi` will expose typed Retrofit calls for:

- `GET /user/{uid}` — public profile and current rating summary;
- `GET /user/{uid}/practice` — public rating/ELO history and practice totals;
- `GET /problem/list?page={page}` — paginated public problem catalog;
- `GET /contest/list?page={page}` — paginated public contest catalog;
- `GET /record/list?user={uid}&page={page}` — attempted submission import,
  which is expected to return the typed auth envelope when anonymous.

The four content endpoints carry `Accept: application/json`,
`X-Requested-With: XMLHttpRequest`, and
`x-lentille-request: content-only`. The client keeps the existing per-request
rate gate, bounded retries, cancellation propagation, and typed network/HTTP/
parse errors.

The response envelope is validated by status/template. A non-200 response or
malformed payload is a parse/API error. A `template == "login"` or
`instance == "auth"` record response is `LuoguAuthenticationRequired`.

## Mapping

### Profile

The Luogu user payload maps to the existing judge-agnostic profile snapshot:

- `name` → `handle`;
- `avatar` → `avatar`;
- `slogan`/`introduction` → the profile bio field;
- `eloValue` (or the public `gu.rating` fallback) → current `rating`;
- `ranking`, `followerCount`, `followingCount`, `passedProblemCount`,
  `submittedProblemCount`, `ccfLevel`, `xcpcLevel`, and `badge` are retained as
  nullable Luogu profile metadata so that synchronization does not discard
  public information.

The account binding continues to store only the canonical handle. The sync
repository resolves that handle to a UID through the existing public search
endpoint before fetching the user pages; no new credential or identity secret
is stored.

### Rating history

Public ELO entries are sorted chronologically and upserted by
`(judge, contest_id)`. `new_rating` is the entry rating. `old_rating` uses the
entry's explicit previous rating, then the previous chronological rating when
available; if neither exists, it remains nullable. Luogu does not expose a
Codeforces-style rank in this payload, so `rank` is nullable rather than
invented as zero. Missing contest IDs are skipped because they cannot satisfy
the idempotency key.

### Problems

Each catalog item maps to `RemoteProblemEntity` with `externalId = pid`, the
native difficulty as `rating` with `OFFICIAL` provenance when present, tags as
the existing serialized JSON list, `totalAccepted` as `solvedCount`, and the
judge-specific problem type in `type`. Items are upserted page by page. A
bounded page budget and the server-reported count prevent an unbounded loop.

### Contests

Each contest maps by string ID. `startTime` and `endTime` become UTC epoch
seconds and duration. The phase is derived as `UPCOMING`, `LIVE`, or `ENDED`
from the sync clock; the raw Luogu method/rated information is kept in the
generic type string. Contest pages are upserted page by page with the same
bounded pagination rule.

## Persistence and sync orchestration

`LuoguSyncRepository` owns resolution, mapping, Room upserts, and stage
freshness. `LuoguSyncCoordinator` owns stage ordering and account-liveness
checks. Public stages run before the auth-gated submissions stage so one
unavailable private endpoint cannot discard usable public data:

`PROFILE → RATING → CONTESTS → PROBLEMS → SUBMISSIONS`.

The existing `SyncStateEntity` timestamps and stage fields are reused. The
repository writes each page immediately, so process death leaves a valid
partial cache. A successful stage records its timestamp; a failed stage leaves
the prior cache intact and records its typed error. Full catalog refreshes do
not delete rows because a transiently incomplete page must not erase cached
data.

The Room schema adds nullable Luogu profile metadata and a nullable rating rank/
old-rating field through a new migration. Existing Codeforces and AtCoder rows
remain readable without backfill.

## Tests and acceptance evidence

Before production code, unit tests must fail for and then cover:

1. content-only envelope decoding and header-bearing endpoint definitions;
2. profile and rating mapping, including missing old rating/rank;
3. problem and contest mapping, phase derivation, and page-budget termination;
4. auth-envelope detection for records;
5. repository idempotency, per-page persistence, and stale-cache preservation;
6. coordinator partial outcome when public stages pass and submissions are
   `AUTH_REQUIRED`;
7. Room migration from schema 5 to the new schema;
8. the existing complete Gradle test, debug build, and lint commands.

## Known limitations

- Luogu's public content-only transport is an implementation-facing web API,
  not a versioned public SDK; the adapter remains `EXPERIMENTAL` and parse
  failures are surfaced rather than silently guessed.
- Anonymous submission records and code submission are not implemented in this
  stage because they require authorization and CSRF handling.
- Cloud account/cross-device synchronization is intentionally deferred.
