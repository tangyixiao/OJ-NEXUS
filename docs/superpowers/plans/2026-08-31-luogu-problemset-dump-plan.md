# Phase 24 plan / 第 24 阶段计划

## Goal / 目标

Replace the default full-catalog Luogu problem sync with the official streamed gzip NDJSON
problemset export, while preserving the existing paginated fallback and local-first security
boundary.

将默认的洛谷完整题库同步切换为官方流式 gzip NDJSON 导出，同时保留分页回退和本地优先的安全边界。

## Steps / 步骤

1. Write parser, transport, and repository tests first; confirm the new tests fail for missing
   dump support.
   先编写解析器、传输层和 Repository 测试，确认在未实现导出支持时按预期失败。
2. Add the official URL, streaming Retrofit response, adapter capability, gzip NDJSON parser,
   and Room batch import. Keep paged sync for adapters without the capability.
   增加官方地址、流式 Retrofit 响应、适配器能力、gzip NDJSON 解析器和 Room 批量导入；不支持该能力
   的适配器继续使用分页同步。
3. Run focused tests, then full `test`, `assembleDebug`, and `lintDebug`; inspect the diff for
   credentials, generated dumps, or accidental historical deletion.
   先跑聚焦测试，再跑完整 `test`、`assembleDebug` 和 `lintDebug`；检查差异中没有凭据、生成的题库文件
   或误删历史说明。
4. Update bilingual README, roadmap, and release notes without deleting earlier entries; commit,
   push, create the next GitHub Release with the APK, and verify its tag and asset digest.
   在不删除旧条目的前提下更新双语 README、路线图和 Release 说明；提交、推送、创建下一版 GitHub
   Release 并核对标签和 APK 摘要。

## Acceptance / 验收

- `LuoguProblemsetDumpParserTest`, `LuoguPublicTransportTest`, and `LuoguSyncRepositoryTest` pass.
- Full verification ends with `BUILD SUCCESSFUL`.
- The app launches on the connected Pixel emulator without a fatal exception.
- The published Release is public, non-draft, non-prerelease, points to the pushed commit, and
  contains the verified debug APK.

- 三个新增聚焦测试全部通过。
- 全量验证以 `BUILD SUCCESSFUL` 结束。
- 已连接 Pixel 模拟器可启动应用且无致命异常。
- 发布的 Release 为公开、非草稿、非预发布，指向已推送提交，并包含已核验的 Debug APK。
