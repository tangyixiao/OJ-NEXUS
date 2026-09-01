# Release identity design / 发布版本身份设计

## Context / 背景

GitHub already publishes `v0.3.33`, but the Android application metadata still reports
`versionCode=1` and `versionName=0.1.0`. Users and Android upgrade checks therefore see a
version identity that does not describe the published artifact.

GitHub 已发布 `v0.3.33`，但 Android 应用元数据仍报告 `versionCode=1` 和
`versionName=0.1.0`。因此用户和 Android 升级判断看到的版本身份与实际发布产物不一致。

## Goal / 目标

Make the next release self-describing: APK `versionName=0.3.34`, `versionCode=34`, Git tag
`v0.3.34`, release notes, README status, and roadmap all use the same release identity.

让下一版具备自描述的版本身份：APK 使用 `versionName=0.3.34`、`versionCode=34`，Git 标签、
Release 说明、README 状态和路线图统一使用 `v0.3.34`。

## Scope / 范围

- Change only `app/build.gradle.kts` version metadata.
- Add bilingual phase and release documentation while preserving every earlier phase and release.
- Verify the installed package metadata and published APK digest.

- 只修改 `app/build.gradle.kts` 中的版本元数据。
- 增加双语阶段说明和 Release 说明，保留此前所有阶段与 Release。
- 验证已安装应用的包版本信息和已发布 APK 摘要。

## Non-goals / 不在本阶段

No new Luogu endpoint, credential flow, database migration, cloud service, cross-device sync,
local compiler, custom-input runner, or automatic submission retry is introduced.

本阶段不新增洛谷接口、凭据流程、数据库迁移、云端服务、跨设备同步、本地编译器、自定义输入运行器
或自动提交重试。

## Acceptance criteria / 验收标准

1. The full project gate reports `BUILD SUCCESSFUL`.
2. After installation, Android reports `versionCode=34` and `versionName=0.3.34`.
3. Branch, tag, and GitHub Release `v0.3.34` point to the same verified commit.
4. The Release APK asset digest equals the locally built APK digest.
5. The emulator remains online and the app launches after installation.

1. 项目全量门禁报告 `BUILD SUCCESSFUL`。
2. 安装后 Android 报告 `versionCode=34`、`versionName=0.3.34`。
3. 分支、标签和 GitHub Release `v0.3.34` 指向同一个已验证提交。
4. Release APK 资产摘要与本地构建 APK 摘要一致。
5. 模拟器保持在线，安装后应用可以启动。
