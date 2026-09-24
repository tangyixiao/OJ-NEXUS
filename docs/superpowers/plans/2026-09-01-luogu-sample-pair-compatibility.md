# Luogu sample-pair compatibility Implementation Plan

> **For agentic workers:** Execute this plan task-by-task with tests and verification checkpoints.

**Goal:** Make native Luogu problem details render live nested sample pairs while preserving old flat fixtures and the Room cache contract.

**Architecture:** Decode sample shape at the network DTO boundary with a custom kotlinx.serialization adapter. Flatten nested pairs into the existing ordered `List<String>` domain value, so the mapper, cache, and Compose UI remain unchanged.

**Tech Stack:** Kotlin, kotlinx.serialization, Retrofit, Room cache, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-01-luogu-sample-pair-compatibility-design.md`

## Global Constraints

- Keep the native Android Compose architecture and existing Luogu content-only transport.
- Do not add main-site passwords, Cookie, Session, CSRF state, cloud service, compiler, or custom runner.
- Preserve the existing flat `List<String>` sample and Room cache representation.
- Every new behavior has a failing unit test before production implementation.

---

### Task 1: Reproduce and decode the live sample shape

**Files:**
- Modify: `app/src/test/java/com/ojnexus/judge/luogu/LuoguProblemDetailTest.kt`
- Modify: `app/src/main/java/com/ojnexus/judge/luogu/api/dto/LuoguProblemDetailDtos.kt`

**Interfaces:**
- Consumes: `LuoguProblemDetailResponse` decoded by the existing content-only Retrofit converter.
- Produces: `LuoguProblemDetailDto.samples: List<String>` accepting nested sample pairs and flat strings.

- [x] **Step 1: Write the failing test**

Add a test that decodes `{"data":{"problem":{"pid":"B4132","name":"Sample","samples":[["1 2","3"],["4 5","9"]]}}}` and asserts the mapped samples equal `listOf("1 2", "3", "4 5", "9")`.

- [x] **Step 2: Run the focused test to verify RED**

Run:

```text
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.judge.luogu.LuoguProblemDetailTest --no-daemon --console=plain
```

Expected: failure during JSON decoding because the current DTO expects a string where the live response supplies an array.

- [x] **Step 3: Implement the smallest DTO serializer**

Decode each JSON array element as either a string or an array of strings, flattening the result;
leave the existing `List<String>` property and all mapper/cache code unchanged.

- [x] **Step 4: Run focused and cache tests to verify GREEN**

Run:

```text
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.judge.luogu.LuoguProblemDetailTest --tests com.ojnexus.judge.luogu.LuoguProblemDetailCacheTest --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL` with the nested and flat sample assertions passing.

### Task 2: Release and live detail acceptance

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.42.md`
- Create: `docs/releases/SHA256SUMS-v0.3.42.txt`

**Interfaces:**
- Consumes: the green DTO compatibility implementation and existing release workflow.
- Produces: signed `v0.3.42` APK, bilingual release notes, and verified live detail rendering.

- [x] **Step 1: Run the full quality gate**

Run `git diff --check` and `clean test assembleDebug lintDebug assembleRelease` with the repository helper; all required tasks must finish successfully.

- [x] **Step 2: Install without clearing data and open a Luogu detail**

Install the signed Release APK over `emulator-5554`, open a cached/public Luogu problem detail,
and verify the title, description, and samples are visible while the device remains online.

- [x] **Step 3: Publish and audit the GitHub Release**

Commit with a bilingual message, push `codex/phase-5-arena`, create annotated tag `v0.3.42`,
publish both APK and checksum assets, then compare local/remote branch SHA, peeled tag SHA,
Release asset digest, manifest hash, package version, and clean worktree.
