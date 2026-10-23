# OJ NEXUS Linux Client

Native Qt 6 / C++20 desktop client and CLI. The GUI has the three connection-center
views `DASHBOARD`, `CONNECTORS`, and `SYNC HISTORY`; all persistence and network work
lives in the C++ Core.

The `CONNECTORS` view accepts a public handle or Luogu `uid:<number>`, stores it in the
local SQLite ledger, and offers a per-judge `SYNC` action. It never asks for or stores a
password, Cookie, or Session, and never reads browser data. Codeforces and AtCoder handles
accept only ASCII letters, digits, `-`, `.`, and `_`; query or
path delimiters are rejected before a network request is constructed.

## Build

```sh
cmake -S . -B build -G Ninja -DOJNEXUS_BUILD_GUI=ON -DOJNEXUS_BUILD_TESTS=ON
cmake --build build
ctest --test-dir build --output-on-failure
```

The build requires Qt 6 Core/Network/Sql/Quick/QuickControls2 and SQLite's Qt SQL driver.
Runtime packages must provide Qt QML modules as well as `QtQml.WorkerScript` for the optional
QML test.

## CLI

```sh
ojnexus status --json
ojnexus sync --judge codeforces --handle tourist --json
ojnexus sync --judge all --json
ojnexus sync --judge all --force --json
ojnexus history --json
ojnexus config show --json
ojnexus auth login --judge codeforces --handle tourist --json
ojnexus auth status --judge luogu --json
ojnexus auth logout --judge luogu --handle 1000 --json
ojnexus auth remove --judge luogu --handle 1000 --json
```

`status --json` includes each account's last sync status, start/finish timestamps, typed error
category, the latest matching profile update time, the locally cached rating-change count, and
the latest 20 normalized submission rows (time, problem, verdict, and language);
the Qt CONNECTORS view renders the same bounded rows beneath each configured account.
Rows are ordered by the normalized submission time when the source provides one, with local
update time and external ID used only as deterministic fallbacks.
an account with no local history reports `lastSyncStatus: NEVER`.

`--force` includes configured accounts currently marked disabled; without it, `sync` only
selects enabled accounts. An explicit `--handle` remains a one-shot sync account.

Authentication is not a credential-import feature. The Linux client uses public handles only;
protected endpoints remain typed `AUTHENTICATION` failures and do not open a Cookie or Session
fallback path.

`sync --handle` performs a one-shot public-handle sync and does not change the configured
account list. Omit it to sync enabled accounts already stored in the local ledger.
For automation, `sync --json` returns an aggregate `status` and keeps one report per account;
the process exit code follows the contract shared with the Windows client: `0` for all-success,
`1` for mixed or otherwise uncategorized failure, `2` for a usage error, `3` for all
network/offline failures, `4` for all-authentication failures, `5` for a cancelled batch, and
`6` for all-storage failures. A cancelled batch reports `CANCELLED` and exits `5`. A run-level error
decides the category first; when a run is only partial, the first module that did not succeed decides
it, which is what makes Luogu `uid:2` (anonymous submissions refused) exit `4` on both clients.
During a sync, `SIGINT`/`SIGTERM` requests cooperative cancellation; the active batch closes its
operation as `CANCELLED` and still emits its JSON report before exiting.
With `--json`, preflight failures also emit a machine-readable `status: ERROR` object with an
`error` code and an empty `reports` array; non-JSON invocations retain human-readable stderr.
For isolated automation, set `OJ_NEXUS_DATA_DIRECTORY` to an explicit temporary root; it
places the database at `<root>/state.db`, configuration at `<root>/config/config.json`, and
the log at `<root>/state/ojnexus.log`, taking precedence over the XDG directory variables.
`auth login` configures a public handle locally; it is not a password login and stores no
credential.
Profile snapshots retain their fetch completion time and the Dashboard displays it, so cached
data is distinguishable from a live response.
The CONNECTORS view and `status` output expose the local rating-change count without re-fetching
or exposing raw server payloads.
Profile payloads are accepted only when the returned public identity matches the requested
handle; Codeforces matching is case-insensitive to accommodate canonical casing.
The SQLite profile write boundary repeats judge-specific handle validation and trims only the
outer whitespace before persisting, so callers cannot bypass the request identity contract.
Sync preflight validates the account before opening a ledger operation or making any network
request; invalid configuration is returned as `INVALID_CONFIGURATION` without a history row.
When reading SQLite, malformed account, operation, or profile rows are ignored rather than being
projected back into the CLI or GUI.
Public requests carry explicit `Accept` and `User-Agent` headers.
HTTP 401/403 responses are recorded as `AUTHENTICATION` instead of being reported as an
undifferentiated API failure.
If a module outcome cannot be written to SQLite, the run is marked `STORAGE/ERROR` and later
network stages are not started.
Luogu list payloads must contain their expected `records`, `contests`, or `problems` array
(directly or under `result`); an explicit empty array is valid, while a missing or malformed list
is reported as `PARSE` instead of being silently treated as empty.
Each module's payload batch is committed as one SQLite transaction before its module outcome is
recorded; cancellation is checked while persisting each row and before that commit, rolling back
the active module without waiting for the rest of a large payload. Operation history timestamps
cover the complete sync run.
Personal profile, rating, and submission cache rows are scoped to a validated judge/handle pair.
One-shot syncs write under their requested public handle without replacing the configured
account's view. Changing or removing an account keeps its owned cache and sync history; another
handle cannot read those personal rows. Contest and problem catalogs are shared public data per
judge. Version-1 rating/submission rows without a stored owner remain unassigned and are retained,
but are not attributed to a handle. Version-1 profile rows retain their validated stored handle.
Re-saving an equivalent handle preserves the existing canonical spelling and disabled state,
so account configuration does not silently re-enable a connector.
`auth status` and `auth logout` operate only on an already configured account, including a
disabled account. `auth logout` disables that account without creating or changing its handle.
`auth remove` removes only the account configuration; existing profile snapshots and sync
history remain local.
`history --json` includes the persisted operation-level `error` category when one was recorded,
so cancelled, authentication, offline, and storage outcomes remain machine-readable after restart.
Without `--json`, nested history and adapter data remain visible as compact JSON values instead of
being silently rendered as empty strings.
In the GUI, `SYNC HISTORY` retry actions use the persisted operation ID and exact judge/handle;
they refuse to run when that account was removed, renamed, or disabled instead of falling back to
every enabled account for the judge.

## Packages

`cpack -G DEB` and `cpack -G RPM` produce native packages. `packaging/PKGBUILD` is the
Arch/pacman recipe; build it on an Arch host with `makepkg`.
