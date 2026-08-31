# Luogu OpenApp Evaluation Usability / 洛谷 OpenApp 评测可用性设计

## Goal / 目标

Make a foreground Luogu OpenApp evaluation easier to follow from both the workspace and the
submission center: use the official WebSocket result notification as an optional accelerator,
retain HTTP result polling as the authoritative fallback, and persist enough evaluation detail
for a user to understand the result after leaving the workspace. / 让工作区和提交中心中的洛谷
OpenApp 评测更容易跟踪：使用官方 WebSocket 结果通知作为可选加速，保留 HTTP 结果查询作为
权威回退，并持久化足够的评测详情，使用户离开工作区后仍能理解结果。

## Scope / 范围

- The foreground check remains the only lifecycle that may open a result connection. No background
  service, WorkManager submission, automatic POST retry, or cloud account is introduced. / 只有
  前台查询流程可以打开结果连接；不新增后台服务、WorkManager 提交、POST 自动重试或云端账号。
- The existing Keystore-backed OpenApp credential is the only credential boundary. Main-site
  passwords, browser cookies, sessions, and CSRF state remain out of scope. / 继续只使用现有
  Keystore 保护的 OpenApp 凭据；主站密码、浏览器 Cookie、Session 和 CSRF 状态仍不在范围内。
- A WebSocket notification is a wake-up signal, not a second result authority. After a matching
  notification, the client performs the normal authenticated HTTP GET so the existing repository
  persistence and error mapping remain the single path. If the socket fails or times out, bounded
  foreground HTTP polling continues. / WebSocket 通知只作为唤醒信号，不作为第二个结果权威来源。
  收到匹配通知后仍执行原有鉴权 HTTP GET，使持久化和错误映射保持单一路径；Socket 失败或超时
  时继续有限的前台 HTTP 轮询。
- Persist nullable compile success/message, execution output, exit code, time, and memory on the
  local submission job. Source code, standard input, and credentials are never persisted in this
  phase. / 本阶段在本地提交任务中持久化可空的编译成功标志/信息、运行输出、退出码、耗时和
  内存；绝不持久化源代码、标准输入或凭据。
- The submission center renders these fields with localized labels and explicit empty handling;
  raw judge status remains visible because status-code semantics can change upstream. / 提交中心
  用本地化标签展示这些字段并明确处理空值；由于上游状态码语义可能变化，仍保留原始评测状态。

## Data flow / 数据流

1. The user explicitly submits from the workspace, or explicitly checks a pending/failed request
   from the submission center. / 用户在工作区明确提交，或在提交中心明确查询 Pending/失败请求。
2. The request is persisted as pending exactly as today. / 请求仍按现有流程持久化为 Pending。
3. The foreground result helper starts one optional WebSocket signal wait for the request ID and
   performs HTTP result checks. A matching signal triggers an immediate HTTP check; lack of a
   signal never changes correctness because the bounded HTTP fallback remains. / 前台结果工具
   为 request ID 启动一次可选 WebSocket 信号等待并执行 HTTP 查询；匹配信号触发立即查询，
   没有信号也不影响正确性，因为仍有有限 HTTP 回退。
4. `LuoguSubmissionRepository.fetchResult` remains the only persistence path. It updates the
   job with all available nullable evaluation fields and materializes a local attempt only for a
   terminal problem result. / `LuoguSubmissionRepository.fetchResult` 仍是唯一持久化入口，
   更新所有可用的可空评测字段；只有题目评测终态才物化本地提交记录。
5. The workspace shows the current result; the submission center reads the persisted snapshot and
   shows the same details after navigation or process restart. / 工作区展示当前结果，提交中心
   读取持久化快照，在跳转或进程重启后展示同样详情。

## Components / 组件

- `LuoguOpenResultSignal`: a small gateway capability returning whether a matching foreground
  notification was observed before a caller-provided timeout. The default implementation is
  unsupported/false so test fakes and other providers remain deterministic. / 小型网关能力，
  返回在调用方指定超时内是否观察到匹配的前台通知；默认实现为不支持/false，测试 fake 和
  其他提供者保持确定性。
- `LuoguOpenPlatformClient`: creates an authenticated WebSocket using the official
  `wss://open-ws.lgapi.cn/ws` endpoint and `judge.result` channel, never logs the token, filters
  messages by request ID, and always closes the socket on success, timeout, cancellation, or
  failure. / 使用官方 endpoint 和 `judge.result` 频道创建鉴权 WebSocket，不记录 Token，按
  request ID 过滤消息，并在成功、超时、取消或失败时关闭连接。
- Shared foreground result helper: accepts the optional signal capability, then invokes the
  existing HTTP fetch/persistence path. It never retries POST and never launches from a background
  worker. / 共享前台结果工具接收可选信号能力，再调用现有 HTTP 查询/持久化路径；不重试
  POST，也不由后台 Worker 启动。
- Room v8 migration: add only nullable evaluation columns to `submission_jobs`, preserving all
  v7 rows and keeping export/import schema validation intact. / Room v8 迁移只向
  `submission_jobs` 添加可空评测列，保留全部 v7 数据并继续通过备份导入导出的 schema 校验。

## Error handling / 错误处理

- WebSocket connection, protocol, timeout, and malformed-message errors are non-terminal for the
  foreground flow; the HTTP fallback remains visible and authoritative. / WebSocket 连接、协议、
  超时和消息格式错误不直接终止前台流程，HTTP 回退仍可见且为权威结果。
- HTTP credential, quota, authorization, network, not-found, and server errors retain the existing
  localized error mapping. / HTTP 凭据、额度、授权、网络、找不到和服务器错误沿用现有本地化
  映射。
- Missing evaluation fields render as absent rather than fabricated zeroes or verdicts. / 缺失
  评测字段显示为空，不伪造 0 值或判定。

## Verification / 验证

- Unit tests cover WebSocket URL/channel and request-ID filtering with a local fake WebSocket
  transport, signal timeout/fallback, all new Room columns, v7-to-v8 migration, and submission
  center rendering data mapping. / 单元测试覆盖 WebSocket URL/频道和 request ID 过滤、信号
  超时/回退、新 Room 列、v7 到 v8 迁移，以及提交中心详情映射。
- Full `test`, `assembleDebug`, and `lintDebug` are required. Pixel_9 must show the submission
  center details/empty state and the credential-safe settings text without a crash. / 必须通过
  全量 `test`、`assembleDebug` 和 `lintDebug`；Pixel_9 需显示提交中心详情/空状态及凭据安全
  设置文本，且无崩溃。

## Non-goals / 不做事项

This phase does not add Luogu main-site login, cookies, sessions, CSRF, cloud sync, cross-device
sync, local C++ compiler, or custom-input execution. The official OpenApp submission endpoint
continues to be used only for explicit problem judging. / 本阶段不加入洛谷主站登录、Cookie、
Session、CSRF、云端同步、跨设备同步、本地 C++ 编译器或自定义输入运行；官方 OpenApp 提交
接口仍只用于用户明确发起的题目评测。
