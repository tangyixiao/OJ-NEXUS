# Luogu OpenApp Evaluation Usability Implementation Plan / 洛谷 OpenApp 评测可用性实施计划

> For agentic workers: use the test-driven-development and executing-plans skills to implement this plan task by task. Every step is checked before moving on.

Goal: Add an optional foreground-only Luogu OpenApp WebSocket result signal, keep authenticated HTTP GET as the authoritative fallback, and persist/render nullable evaluation details in the local submission center. / 目标：增加仅前台可用的洛谷 OpenApp WebSocket 结果信号，保留鉴权 HTTP GET 作为权威回退，并在本地提交中心持久化/展示可空评测详情。

Architecture: LuoguOpenPlatformClient implements a result-signal capability over the official wss://open-ws.lgapi.cn/ws endpoint and filters judge.result messages by request ID. The shared foreground helper runs that signal alongside bounded HTTP checks; a signal only causes an immediate HTTP fetch, so LuoguSubmissionRepository.fetchResult remains the only persistence path. Room v8 adds nullable evaluation columns to submission_jobs. / 架构：客户端通过官方 WebSocket 按 request ID 过滤结果信号；共享前台工具与有限 HTTP 查询并行，信号只触发立即 HTTP 查询，因此仓储仍是唯一持久化入口；Room v8 添加可空评测列。

Tech stack: Kotlin coroutines, OkHttp WebSocket, Retrofit, kotlinx.serialization, Room migration, Compose, JUnit, MockWebServer. / 技术栈：Kotlin 协程、OkHttp WebSocket、Retrofit、序列化、Room 迁移、Compose、JUnit、MockWebServer。

Spec: docs/superpowers/specs/2026-08-31-openapp-evaluation-design.md

## Global Constraints

- Only explicit foreground result checks may open the WebSocket; no background service, WorkManager submission, or automatic POST retry. / 只有明确触发的前台结果查询可以打开 WebSocket；不新增后台服务、WorkManager 提交或 POST 自动重试。
- Only the existing Keystore-backed OpenApp pair is accepted; never request or store Luogu main-site passwords, cookies, sessions, CSRF state, or cloud credentials. / 只接受现有 Keystore 保护的 OpenApp 凭据；不请求或存储主站密码、Cookie、Session、CSRF 或云端凭据。
- WebSocket messages are wake-up signals only; authenticated HTTP GET remains the authority. / WebSocket 只作唤醒信号，鉴权 HTTP GET 仍是权威结果。
- New Room fields are nullable; source code, standard input, and credentials remain absent from Room and backups. / 新 Room 字段全部可空；源代码、标准输入和凭据不得进入 Room 或备份。
- All user-visible strings are added to both English and Simplified Chinese resources. / 所有用户可见字符串必须同时进入英文和简体中文资源。
- Production behavior is written after a failing test and verified with focused and full commands. / 生产行为必须先有失败测试，再完成聚焦和全量验证。

---

### Task 1: Write failing signal and helper tests / 编写结果信号与工具失败测试

Files:
- Modify app/src/test/java/com/ojnexus/judge/luogu/open/LuoguOpenPlatformTest.kt
- Create app/src/test/java/com/ojnexus/judge/luogu/open/LuoguOpenResultPollingTest.kt

Interfaces:
- Consumes current LuoguOpenPlatformClient, MockWebServer, and LuoguOpenResult.
- Produces expectations for awaitResultSignal(requestId, timeoutMillis) and pollLuoguOpenResult.

- [ ] Step 1: Add a failing WebSocket test. Configure a MockWebServer WebSocket upgrade that sends:

    judge.result plus NUL plus {requestId:other}
    judge.result plus NUL plus {requestId:req-1}

    Assert awaitResultSignal(req-1, 1000) returns true, the request path is /ws, query token is u:s, and query channel is judge.result. Add a timeout assertion returning false. Do not print a raw token.

- [ ] Step 2: Add failing polling tests. One test injects an immediate true signal and a Pending then Ready fetch sequence; it asserts the next fetch happens without a delay. A second test injects false and asserts the bounded HTTP sequence still returns Ready. Use a no-op delay callback and assert returned results.

- [ ] Step 3: Run the focused tests and verify RED:

    .\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.judge.luogu.open.LuoguOpenPlatformClientTest --tests com.ojnexus.judge.luogu.open.LuoguOpenResultPollingTest --no-daemon --console=plain

    Expected failure: missing signal capability/helper parameters, not a test typo. No production implementation before this RED result.

### Task 2: Implement official foreground WebSocket signal / 实现官方前台 WebSocket 信号

Files:
- Modify app/src/main/java/com/ojnexus/judge/luogu/LuoguUrls.kt
- Modify app/src/main/java/com/ojnexus/judge/luogu/open/LuoguOpenPlatform.kt
- Modify app/src/main/java/com/ojnexus/OjNexusApplication.kt
- Test app/src/test/java/com/ojnexus/judge/luogu/open/LuoguOpenPlatformTest.kt

Interfaces:
- Produces LuoguOpenResultSignal.awaitResultSignal(requestId: String, timeoutMillis: Long): Boolean.
- LuoguOpenGateway and LuoguSubmissionCenter extend the capability with a false default so fakes remain deterministic.

- [ ] Step 1: Add OPEN_PLATFORM_WEBSOCKET_URL = wss://open-ws.lgapi.cn/ws and the capability interface. Let the client receive the app OkHttpClient plus a test-overridable WebSocket URL while keeping the production URL unchanged.

- [ ] Step 2: Build the URL with HttpUrl.Builder using token=user:secret and channel=judge.result. Use suspendCancellableCoroutine and withTimeoutOrNull. Parse the text after the first NUL as LuoguJudgeCallbackDto, ignore malformed/non-matching frames, return true only for the requested ID, and return false on timeout, close, failure, or cancellation. Always close or cancel the socket. Do not add a logging interceptor or log the URL.

- [ ] Step 3: Pass the existing app OkHttpClient when constructing LuoguOpenPlatformClient. Keep the public Luogu Retrofit client and OpenApp API boundary separate.

- [ ] Step 4: Re-run Task 1 focused tests. Expected GREEN for URL/channel filtering, timeout cleanup, and existing OpenApp request tests.

### Task 3: Share signal wake-up with workspace and submission center / 接入工作区与提交中心

Files:
- Modify app/src/main/java/com/ojnexus/judge/luogu/open/LuoguOpenPlatform.kt
- Modify app/src/main/java/com/ojnexus/judge/luogu/open/LuoguSubmissionRepository.kt
- Modify app/src/main/java/com/ojnexus/feature/workspace/WorkspaceViewModel.kt
- Modify app/src/main/java/com/ojnexus/feature/submissions/SubmissionCenterViewModel.kt
- Test the two corresponding ViewModel test files

- [ ] Step 1: Extend the shared helper with an optional awaitResultSignal callback. Start it concurrently with the existing eight-attempt, one-second HTTP loop. Use select so a true signal skips the next delay; false, timeout, failure, and cancellation use the existing fallback. Cancel the signal job in finally.

- [ ] Step 2: Use this exact public shape:

    internal suspend fun pollLuoguOpenResult(
        requestId: String,
        fetch: suspend (String) -> LuoguOpenResult,
        delayForResult: suspend (Long) -> Unit = { delay(it) },
        awaitResultSignal: (suspend (String, Long) -> Boolean)? = null,
    ): LuoguOpenResult

    LuoguSubmissionRepository delegates awaitResultSignal to its wrapped gateway. Workspace passes gateway::awaitResultSignal; submission center passes submissionCenter::awaitResultSignal. Only these foreground actions call the helper.

- [ ] Step 3: Run:

    .\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.feature.workspace.WorkspaceViewModelTest --tests com.ojnexus.feature.submissions.SubmissionCenterViewModelTest --no-daemon --console=plain

    Expected: existing bounded polling, new signal wake-up, duplicate suppression, retry, and error tests pass.

### Task 4: Add Room v8 evaluation columns and migration / 增加 Room v8 评测列与迁移

Files:
- Modify app/src/main/java/com/ojnexus/core/database/entity/SubmissionJobEntity.kt
- Modify app/src/main/java/com/ojnexus/core/database/OjNexusDatabase.kt
- Modify app/src/test/java/com/ojnexus/core/database/MigrationTest.kt
- Generate and inspect app/schemas/8.json

- [ ] Step 1: Add a failing v7-to-v8 test that creates a v7 database, inserts request legacy-1, applies MIGRATION_7_8, opens the current database, and asserts the row survives with null in all new fields. Run the MigrationTest class and verify RED because version eight and the migration do not exist yet.

- [ ] Step 2: Add nullable fields with exact names compile_success, compile_message, output, exit_code, execution_time_ms, and memory_kib. Set OJ_NEXUS_SCHEMA_VERSION to 8. Add MIGRATION_7_8 using six ALTER TABLE submission_jobs ADD COLUMN statements, and append it to the database builder migration chain.

- [ ] Step 3: Update all migration test chains to include MIGRATION_7_8 when opening the current database. Generate app/schemas/8.json and inspect that all six columns are nullable and v7 columns/indexes remain.

- [ ] Step 4: Re-run MigrationTest and verify GREEN, including historical migration preservation.

### Task 5: Persist and render evaluation details / 持久化并展示评测详情

Files:
- Modify app/src/main/java/com/ojnexus/judge/luogu/open/LuoguSubmissionRepository.kt
- Modify app/src/main/java/com/ojnexus/feature/submissions/SubmissionCenterScreen.kt
- Modify app/src/main/res/values/strings.xml
- Modify app/src/main/res/values-zh-rCN/strings.xml
- Test app/src/test/java/com/ojnexus/judge/luogu/open/LuoguSubmissionRepositoryTest.kt

- [ ] Step 1: Extend the existing ready-result repository test with compileSuccess=false, a compile message, output=stderr, exitCode=1, timeMs=23, and memoryKiB=64. Assert every corresponding persisted field and preserve existing attempt materialization.

- [ ] Step 2: In the Ready branch copy compileSuccess, compileMessage, output, exitCode, timeMs, and memoryKiB into the job update. Keep score, judgeStatus, terminal detection, and idempotent attempt materialization unchanged.

- [ ] Step 3: Add localized labels for compile status/message, execution time, memory, exit code, and output. Render non-null/non-blank values in SubmissionJobCard. Compile success/failure must carry text, not color alone; raw judge status remains visible.

- [ ] Step 4: Run the repository focused test and the Android resource compilation. Missing values must render absent rather than fabricated zeroes or verdicts.

### Task 6: Bilingual docs, fresh verification, device acceptance, and Release / 双语文档、验证、设备验收与发布

Files:
- Modify README.md and docs/ROADMAP.md
- Update this plan checkboxes with actual evidence
- Add no deletion to prior phase notes or historical Releases

- [ ] Step 1: Document Phase 20 in English and Chinese: WebSocket signal, HTTP authority, persisted nullable details, local-first/security boundaries, and explicit non-goals.

- [ ] Step 2: Run fresh full verification:

    git diff --check
    .\tools\gradlew-local.bat clean test assembleDebug lintDebug --no-daemon --rerun-tasks --console=plain

    Count TEST-*.xml tests and failures/errors, record APK SHA-256, and inspect output completely before claims.

- [ ] Step 3: Install app-debug.apk on Pixel_9 with D:\Android\platform-tools\adb.exe. Launch com.ojnexus, navigate Profile to 提交中心, inspect empty/local state and Settings OpenApp-only text, check logcat for FATAL EXCEPTION, and do not enter fabricated credentials or shut down the machine.

- [ ] Step 4: After verification and secret diff audit, commit and push:

    git add README.md docs/ROADMAP.md app/src/main app/schemas/8.json docs/superpowers/specs/2026-08-31-openapp-evaluation-design.md docs/superpowers/plans/2026-08-31-openapp-evaluation-plan.md
    git commit -m feat: improve Luogu evaluation flow / 完善洛谷评测流程
    git push origin codex/phase-5-arena

- [ ] Step 5: Create GitHub Release v0.3.16 with bilingual notes, the real test count, APK SHA-256, device result, historical-content retention, and security/cloud boundaries. Verify gh release view, local HEAD, remote branch SHA, remote tag SHA, and asset digest all agree.
