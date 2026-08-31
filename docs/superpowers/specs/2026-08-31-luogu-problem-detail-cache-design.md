# Luogu problem detail cache / 洛谷题目详情缓存设计

## Context / 背景

Phase 24 imports the Luogu catalog locally, but opening a native Luogu problem detail still
depends on the public network every time. That makes the catalog less useful offline and causes a
previously viewed problem to disappear from the app when the network is unavailable.

第 24 阶段已经把洛谷题库目录导入本地，但打开原生洛谷题目详情时仍然每次依赖公开网络。这会削弱
本地题库的离线价值，也会导致用户曾经看过的题目在断网时无法继续查看。

## Decision / 决策

Add a small judge-scoped `remote_problem_details` Room table and make the Luogu detail repository
cache-first:

1. Return a cached detail immediately when one exists.
2. If no cache exists, fetch the public content-only detail and persist it.
3. Expose an explicit refresh path that fetches the public detail and updates the cache.
4. If refresh fails because of network or timeout and a cached value exists, return the cached value;
   HTTP/auth/parse failures remain visible instead of being hidden by stale data.

新增按评测平台隔离的 `remote_problem_details` Room 表，并让洛谷详情 Repository 遵循缓存优先：

1. 有缓存时立即返回缓存。
2. 没有缓存时请求公开 content-only 详情并持久化。
3. 提供显式刷新路径，请求公开详情并更新缓存。
4. 刷新因网络或超时失败且存在缓存时返回缓存；HTTP/鉴权/解析错误不被旧数据掩盖。

## Data / 数据

The entity stores the rendered-detail source fields required by the native screen: pid, title,
difficulty, integer tags, submit/accepted counts, background, description, input/output formats,
hint, samples, time limit, memory limit, and `updatedAt`. Samples and tags are serialized as JSON
strings so the table remains one row per judge/problem and no user-owned problem rows are touched.

实体保存原生详情页所需的源字段：题号、标题、难度、整数标签、提交/通过数、背景、题目描述、输入/输出
格式、提示、样例、时间限制、内存限制和 `updatedAt`。样例和标签序列化为 JSON 字符串，使该表保持每个
评测平台/题目一行，且不修改用户自己的本地题目记录。

## Error and freshness / 错误与新鲜度

The first load is cache-first and therefore may show the last known snapshot. A detail screen can
call refresh explicitly; the repository returns a small result describing whether the data came
from `NETWORK` or `CACHE_FALLBACK`, allowing the ViewModel to keep the UI honest. A cache is never
created from an error response or malformed payload.

首次加载采用缓存优先，因此可能显示上一次已知快照。详情页可以显式刷新；Repository 返回数据来源
`NETWORK` 或 `CACHE_FALLBACK`，让 ViewModel 不夸大数据新鲜度。错误响应或格式错误的数据不会写入缓存。

## Non-goals / 不包含

- No main-site password, cookie, session, CSRF login, cloud account, or cross-device sync.
- No bulk detail import from the official dump in this phase; dump download remains catalog-only.
- No WebView or HTML scraping; the existing safe Markdown renderer remains the presentation boundary.

- 不新增主站密码、Cookie、Session、CSRF 登录、云端账号或跨设备同步。
- 本阶段不从官方导出批量导入全部题面详情，导出同步仍只负责题库目录。
- 不使用 WebView 或 HTML 抓取；继续使用现有安全 Markdown 渲染器。

## Verification / 验证

- Mapper tests cover round-tripping all cached detail fields, including samples and limits.
- Repository tests cover cache hit without network, network miss with persistence, explicit refresh,
  network fallback, and non-network errors not being hidden.
- Migration tests prove Room v8 data survives the v8→v9 table creation.
- Full unit tests, assemble, lint, debug APK launch, and the next GitHub Release are required.

- Mapper 测试覆盖所有缓存详情字段（包括样例和限制）的往返转换。
- Repository 测试覆盖有缓存不访问网络、无缓存在线获取并持久化、显式刷新、网络失败回退，以及非网络
  错误不被隐藏。
- Migration 测试验证 v8 数据在创建 v8→v9 新表后完整保留。
- 必须完成全量单元测试、构建、Lint、Debug APK 启动和下一版 GitHub Release。
