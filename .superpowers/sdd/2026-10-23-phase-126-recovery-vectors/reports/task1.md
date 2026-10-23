# Task 1 verification record

Baseline: `c39f6be` (`docs: distinguish work permission from recovery actions`). This report covers only the canonical synthetic corpus and its test-only strict decoder; it does not record the Phase 126 production-decision consumers from Task 2.

## Executed command

```powershell
$env:JAVA_HOME='D:\Android Studio\jbr'
$env:ANDROID_HOME='D:\Android'
.\gradlew.bat :app:testDebugUnitTest --tests '*RecoveryVectorCorpusTest'
```

Result from the final run:

```text
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 6s
28 actionable tasks: 3 executed, 25 up-to-date
```

The Gradle XML at `app/build/test-results/testDebugUnitTest/TEST-com.ojnexus.core.data.restore.RecoveryVectorCorpusTest.xml` recorded `tests=5`, `failures=0`, `errors=0`, `skipped=0`, `time=0.188` seconds. Executed test cases:

- `canonical corpus has all stable IDs and only the nine declared projections`
- `relations keep blocked aborted empty and retry outcomes honest`
- `decoder rejects unsupported version duplicate ID absent fields and unknown fields`
- `nullable contract keys are required and recovery journal may be explicitly null`
- `decoder rejects unknown enum and broken safety relations`

## Covered by this run

The decoder checks exact root/vector/expected/retry key sets, requires the explicit nullable `android`, `expected.error`, recovery `journal`, and generation `expected_generation` keys, and validates projection key sets by `kind`. The test accepts an explicit `journal: null`; it rejects omitted nullable keys, foreign-variant keys even when their value is null, unsupported versions, duplicate IDs, missing required values, unknown fields and enums, and invalid relations including a generic `RETRY` prerequisite.

The corpus assertions cover RV001–RV032 identity and ordering, the exact nine projection IDs, permission for ordinary work only on matching-generation RV021, artifact preservation for BLOCKED, OLD/OLD for aborted import, valid empty REPLACE versus UPSERT, and denied-retry `after=NONE`. Preconditions now state 32 distinct symbolic setup facts. The assertions specifically inspect RV010 confirmation durability, RV013 validated NEW data, RV017 unavailable target plus remaining rollback source, RV018 independently valid OLD data and known OLD generation, and RV026 transactional rollback from OLD data.

These are contract-structure and corpus-consistency checks. They do not execute storage recovery, inject native faults, validate file operations, or prove end-state recovery. The nine direct production decision/classification consumers remain Task 2 coverage.
