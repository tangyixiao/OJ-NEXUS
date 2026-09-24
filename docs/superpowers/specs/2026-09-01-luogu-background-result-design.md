# Luogu OpenApp background result convergence / 洛谷 OpenApp 结果后台收敛设计

## Goal / 目标

Make an already-created Luogu OpenApp submission remain useful after the user leaves the
workspace or restarts the app. A local WorkManager job will query the official asynchronous
result endpoint and persist the returned evaluation into the existing submission center.
让用户离开工作区或重启应用后，已经创建的洛谷 OpenApp 提交仍然可用：由本地 WorkManager
查询官方异步结果接口，并把评测结果写入现有提交中心。

## Scope and safety boundary / 范围与安全边界

- Only `GET /judge/result/{requestId}` is performed in the background. The worker never submits
  code and never retries a POST. / 后台只执行 `GET /judge/result/{requestId}`，不提交代码，也不重试 POST。
- The request ID and existing local lifecycle metadata are used; source code, standard input,
  passwords, cookies, sessions, and CSRF state are never added to the job input or database.
  / 只使用 Request ID 和已有本地生命周期元数据；不把源代码、标准输入、密码、Cookie、Session 或 CSRF
  状态加入任务输入或数据库。
- The existing Keystore-backed OpenApp credential store remains the only credential source.
  Missing or invalid credentials stop the worker with a visible local failure; they are not
  retried indefinitely. / 继续使用 Keystore 保护的 OpenApp 凭据；凭据缺失或失效时本地记录失败，不无限重试。
- A bounded retry budget keeps pending requests recoverable without creating a perpetual job.
  When the budget is exhausted, the local job remains `PENDING` and can still be checked from the
  submission center. / 使用有界重试预算；预算耗尽后本地任务仍为 `PENDING`，用户仍可从提交中心手动查询。

## Architecture / 架构

### Scheduler / 调度器

Add `LuoguResultWorkScheduler`, a small interface whose production implementation enqueues a
unique `LuoguOpenResultWorker` with a connected-network constraint and a short initial delay.
The delay lets the foreground workspace finish its existing signal-plus-poll path before the
background query starts. `ExistingWorkPolicy.KEEP` prevents duplicate workers for one request ID.
增加 `LuoguResultWorkScheduler` 接口；生产实现使用联网约束和短暂初始延迟，唯一任务名由 Request ID
组成，并采用 `ExistingWorkPolicy.KEEP` 防止同一请求重复执行。初始延迟让工作区原有的前台通知加轮询
流程优先完成。

### Worker / Worker

`LuoguOpenResultWorker` receives only a non-blank request ID. It calls the existing
`LuoguSubmissionRepository.refreshResult`, which already persists pending, in-progress, terminal
evaluation, and local attempt data. The worker maps outcomes as follows:

| Result / error | Worker action |
| --- | --- |
| `Ready` | `success`; local repository has converged the job |
| `Pending` or `InProgress` within budget | `retry` with WorkManager backoff |
| network, timeout, HTTP 408/425/429/5xx | `retry` within budget |
| missing/invalid credential, forbidden, not found, malformed or other permanent error | `failure` |
| retry budget exhausted | `success`; leave local job pending for manual check |

`LuoguOpenResultWorker` 只接收非空 Request ID，并调用已有的
`LuoguSubmissionRepository.refreshResult`；仓储已经负责保存等待中、评测中、终态结果以及本地 attempt。
结果和错误按上表处理。

### Lifecycle wiring / 生命周期接线

After `LuoguSubmissionRepository` successfully persists a new `submitProblem` request, it asks
the scheduler to enqueue the request. The same hook is used for any supported `run` request, so
the boundary remains gateway-agnostic even though the concrete Luogu provider currently does not
advertise custom-input execution. Existing foreground polling remains unchanged and is not
replaced by the worker.
在 `LuoguSubmissionRepository` 成功保存新的 `submitProblem` 请求后，调用调度器加入后台任务；支持的
`run` 请求也复用同一钩子。现有前台轮询保持不变，后台 Worker 只是补偿离开页面后的结果收敛。

On application startup, a bounded list of locally pending Luogu jobs is reconciled into the same
unique work namespace. This covers requests created by an older app process or before a process
death, without creating an unbounded scan or changing any stored payload. / 应用启动时还会把本地有限数量
的待处理洛谷任务补入同一唯一任务空间，覆盖旧进程或进程被杀死前创建的请求；不会无限扫描，也不改变
已保存的数据内容。

## Failure handling / 失败处理

The worker never hides a repository error. The repository records the existing local error type
and timestamp; WorkManager only decides whether another bounded GET is worthwhile. Offline state
is represented by WorkManager constraints, not an error dialog. Credential failures are terminal
until the user fixes the OpenApp setting and manually checks the request again.
Worker 不吞掉仓储错误；仓储继续记录既有本地错误类型和时间，WorkManager 只决定是否值得再次进行有界
GET。离线通过 WorkManager 网络约束表达，不弹错误对话框；凭据失败在用户修复 OpenApp 设置前视为终态，
用户可之后从提交中心手动查询。

## Testing / 测试

- Pure decision tests cover ready, pending, transient HTTP/network, permanent credential, and
  exhausted-budget outcomes.
- Scheduler tests cover stable unique names, request-ID-only input, connected constraint, and
  duplicate prevention using a fake enqueue boundary.
- Repository tests prove a successful submission schedules its request only after local metadata
  is persisted, and startup reconciliation only schedules bounded pending jobs, while existing
  submission and workspace tests remain green.
- Full `clean test assembleDebug lintDebug` and an emulator smoke test will verify no crash and
  that the existing workspace → submission-center flow remains intact.

## Explicit non-goals / 明确不做

No Luogu main-site password login, Cookie/Session/CSRF handling, cloud account, cross-device
sync, local compiler, custom-input runner, automatic POST retry, or background submission is
introduced. Historical documentation and Releases remain unchanged except for the new bilingual
phase entry. / 不新增洛谷主站密码登录、Cookie/Session/CSRF 处理、云端账号、跨设备同步、本地编译器、
自定义输入运行器、POST 自动重试或后台提交。历史文档和 Releases 除新增本阶段双语说明外全部保留。
