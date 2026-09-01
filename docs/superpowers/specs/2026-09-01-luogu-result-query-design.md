# Luogu result query alignment / 洛谷结果查询对齐设计

## Context / 背景

The Luogu Open Platform documentation defines asynchronous result polling as
`GET /judge/result?id=<RequestId>`. OJ NEXUS currently calls `/judge/result/{RequestId}`.
The submission request can therefore be accepted while a later result check uses the wrong
HTTP shape.

洛谷 Open Platform 文档将异步结果轮询定义为 `GET /judge/result?id=<RequestId>`，而 OJ NEXUS
当前调用的是 `/judge/result/{RequestId}`。因此提交请求可能成功，但后续结果查询使用了错误的
HTTP 形式。

## Goal / 目标

Make every result check use the documented query parameter while preserving the existing 204
Pending, partial 200, terminal 200, WebSocket wake-up, and local recovery behavior.

让每次结果查询都使用文档规定的 query 参数，同时保留现有的 204 Pending、部分结果 200、终态 200、
WebSocket 唤醒和本地恢复行为。

## Scope / 范围

- Change the Retrofit result declaration from a path parameter to a query parameter.
- Update the contract test so the wrong path cannot regress.
- Correct the bilingual Open Platform documentation and add a bilingual phase/release note.

- 将 Retrofit 结果接口从路径参数改为 query 参数。
- 更新契约测试，防止错误路径回归。
- 修正双语 Open Platform 文档，并增加双语阶段/Release 说明。

## Non-goals / 不在本阶段

No new login mode, main-site password, Cookie, Session, CSRF state, cloud service, local
compiler, custom-input runner, automatic POST retry, or public submission-history import is added.

本阶段不新增登录模式、主站密码、Cookie、Session、CSRF 状态、云端服务、本地编译器、自定义输入运行器、
自动 POST 重试或公开提交历史导入。

## Acceptance criteria / 验收标准

1. The focused HTTP contract test requires `/judge/result?id=req-1`.
2. The full project gate reports `BUILD SUCCESSFUL`.
3. The installed app launches and the existing Luogu public sync data remains available.
4. Branch, tag, and GitHub Release `v0.3.35` point to the verified commit and the APK digest matches.

1. HTTP 聚焦契约测试要求 `/judge/result?id=req-1`。
2. 项目全量门禁报告 `BUILD SUCCESSFUL`。
3. 安装后的应用可以启动，现有洛谷公开同步数据仍然可用。
4. 分支、标签和 GitHub Release `v0.3.35` 指向已验证提交，APK 摘要一致。
