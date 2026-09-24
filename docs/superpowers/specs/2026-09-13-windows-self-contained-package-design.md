# OJ NEXUS — Windows Self-Contained Package Design

## Status

Proposed design for the next Windows release increment. This package is a distributable
runtime bundle, not an installer, signed release, or Microsoft Store submission.

## Goal

Produce a reproducible `win-x64` Windows package that runs on a machine without a separately
installed .NET runtime. The package must contain the native WPF client and the `ojnexus` CLI,
retain the existing public-handle/local-first boundary, and provide evidence that the exact
packaged binaries—not only the project output—start and respond correctly.

## Scope and non-goals

Included:

- self-contained `win-x64` publish for the CLI and WPF desktop projects;
- a versioned directory and ZIP archive with a stable layout;
- a SHA-256 manifest covering the shipped executable and support files;
- package-level CLI status and WPF launch/UI smoke checks;
- local documentation and a CI artifact upload.

Not included:

- MSI, MSIX, registry writes, Start Menu integration, or an uninstaller;
- code signing, certificate generation, timestamping, or Store packaging;
- automatic updates or a network bootstrapper;
- changes to the application data location, migration model, or OJ adapters.

## Package contract

The release script accepts a package version, configuration, and runtime, defaulting to
`0.1.0`, `Release`, and `win-x64`. It publishes with the repository's .NET 8 projects and
`SelfContained=true`; it does not use `PublishSingleFile`, so native SQLite support files
remain inspectable and runtime extraction behavior is avoided.

The archive layout is:

```text
OJ-NEXUS-Windows-v0.1.0-win-x64/
  cli/
    ojnexus.exe
    <self-contained CLI support files>
  desktop/
    OjNexus.Windows.Desktop.exe
    <self-contained WPF support files>
  README.txt
  SHA256SUMS.txt
```

The package never contains a user database, temporary smoke data, credentials, cookies,
keystores, or build secrets. At runtime the application continues to use
`%LOCALAPPDATA%\OJ-NEXUS\ojnexus.db`, unless the existing `OJ_NEXUS_DATA_DIRECTORY` automation
override is explicitly supplied.

## Build and data flow

1. Validate the requested version and runtime names and resolve the repository root.
2. Publish CLI and desktop into separate staging directories with `--self-contained true` and
   `--no-restore`; restore remains an explicit caller/CI step.
3. Copy the Windows package readme and generate `SHA256SUMS.txt` from the staged files using
   stable relative paths and SHA-256 hashes.
4. Create the versioned ZIP from the staging directory without including the staging parent,
   temporary data, or unrelated `bin/obj` files.
5. Verify the staged CLI with `status --json` and an isolated temporary data directory.
6. Verify the staged WPF executable with the existing bounded process smoke and UI Automation
   smoke, passing the exact staged executable path.
7. Leave the package and its manifest under `windows/artifacts/` for local inspection and CI
   upload; that directory remains ignored by Git.

The package script is orchestration only. Application logic remains in Core, and no package
step makes a network request to an OJ endpoint.

## Failure behavior

- Missing project output, invalid parameters, failed publish, missing archive members, a hash
  mismatch, non-zero CLI status, or a WPF process that exits before its timeout causes a
  non-zero script exit.
- The script uses one generated temporary data directory and removes only that exact directory.
- A failed UI screenshot remains a separately reported visual-capture limitation, as in the
  existing UI smoke. UI Automation success must not be upgraded to screenshot or human visual
  acceptance.
- No error path prints environment secrets or raw user/OJ payloads.

## CI integration

The Windows workflow keeps its existing restore, solution test, and warning-free build gates.
After those gates it invokes the package script, runs package-level smoke against the staged
paths, and uploads the versioned ZIP plus the unpacked manifest as artifacts. CI does not sign,
publish externally, or call real OJ endpoints.

## Verification contract

The implementation is complete only when all of the following are demonstrated on Windows:

- solution tests pass: Core, CLI, and Desktop suites all green;
- Release solution build has zero warnings and zero errors;
- the package contains both expected executables and `SHA256SUMS.txt`;
- every manifest hash matches the staged package contents;
- the packaged CLI returns exit code 0 and `status=ready` with isolated data;
- the packaged WPF process starts within the existing 15-second bound;
- UI Automation visits all three pages at `900x560`, including the three semantic public-handle
  fields and the history filter;
- package staging and temporary smoke data are cleaned or remain only in the ignored artifact
  directory;
- documentation states clearly that the artifact is unsigned and has no installer semantics.

## Alternatives considered

1. Keep framework-dependent output. This is the smallest change, but it leaves the end user
   responsible for installing the matching .NET runtime and does not satisfy a standalone
   Windows distribution goal.
2. Add MSIX. This provides a stronger installation/update model, but requires package identity,
   signing, certificate handling, and a Store or sideload policy that the current repository
   does not define.
3. Use a third-party MSI generator. This adds an external toolchain and installer behavior
   without an approved product contract.

The self-contained directory plus ZIP is selected because it solves the runtime dependency
without introducing signing secrets, registry side effects, or an undocumented update model.
