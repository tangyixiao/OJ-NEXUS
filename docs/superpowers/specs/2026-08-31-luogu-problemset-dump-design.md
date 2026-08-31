# Luogu official problemset dump / 洛谷官方题库导入设计

## Context / 背景

The paginated public problem list is useful for incremental checks, but it is a poor first
source for a complete local catalog: it requires many requests and is vulnerable to page-count
changes while syncing. Luogu documents an official gzip-compressed NDJSON export for the public
problemset. This phase adds that source behind the existing `LuoguAdapter` boundary.

分页公开题目列表适合增量检查，但不适合作为完整本地题库的首选来源：它需要大量请求，且同步过程
中题目总数变化会影响分页结果。洛谷官方文档提供了公开题库的 gzip 压缩 NDJSON 导出文件。本阶段
在既有 `LuoguAdapter` 边界之后接入该数据源。

## Scope / 范围

- Use the official endpoint `https://cdn.luogu.com.cn/problemset-open/latest.ndjson.gz`.
- Stream-decompress gzip and parse one JSON problem per line.
- Map only catalog fields needed by local search and list screens: pid, title, difficulty, tags,
  judge, and sync timestamp.
- Write Room rows in bounded batches so the complete export is not retained in memory.
- Keep the paginated endpoint as a fallback for adapters that do not support the dump.
- Keep the existing public, local-first, OpenApp-only boundary.

- 使用官方地址 `https://cdn.luogu.com.cn/problemset-open/latest.ndjson.gz`。
- 流式解压 gzip，并按每行一个 JSON 题目的方式解析。
- 只映射本地搜索和列表所需的题库字段：题号、标题、难度、标签、评测平台和同步时间。
- 以有界批次写入 Room，不把完整导出文件保留在内存中。
- 不支持该导出的适配器继续回退到分页接口。
- 保持公开数据、本地优先、仅 OpenApp 的现有边界。

## Non-goals / 不包含

- No problem statement/detail bulk import in this phase; details remain on-demand.
- No main-site password, cookie, session, CSRF login, cloud account, or cross-device sync.
- No automatic submission or background network worker.

- 本阶段不批量导入题面详情，详情仍按需获取。
- 不新增主站密码、Cookie、Session、CSRF 登录、云端账号或跨设备同步。
- 不新增自动提交或后台网络 Worker。

## Failure behavior / 失败行为

HTTP failures use the existing bounded transport policy. A malformed nonblank NDJSON line or a
gzip/decompression failure is a non-retryable parse failure; the sync stage reports the error and
does not claim a complete catalog. Blank lines are ignored. The response body is always closed.

HTTP 失败沿用现有有界传输策略。非空 NDJSON 行格式错误或 gzip/解压失败时，返回不可重试的解析
错误；同步阶段报告失败，不宣称题库已完整导入。空行忽略，响应体始终关闭。

## Verification / 验证

- Parser tests cover gzip NDJSON, catalog mapping, and malformed input.
- Transport tests prove the dump path and streaming response handling.
- Repository tests prove the dump is preferred and rows are batch-imported without paged calls.
- Full unit tests, assemble, lint, and a debug APK launch are required before release.

- 解析器测试覆盖 gzip NDJSON、题库字段映射和错误输入。
- 传输测试验证导出路径和流式响应处理。
- Repository 测试验证优先使用导出并批量导入，且不会调用分页接口。
- 发布前必须完成全量单元测试、构建、Lint 和 Debug APK 启动验收。
