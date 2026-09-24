# Installable release build design / 可安装 Release 构建设计

## Context / 背景

The repository's `release` variant currently builds successfully but produces
`app-release-unsigned.apk`. GitHub v0.3.35 distributes `app-debug.apk`, which is installable
but is a Debug variant and can expose development-only Demo controls.

仓库的 `release` 变体目前可以成功构建，但产物是 `app-release-unsigned.apk`。GitHub v0.3.35
分发的是可安装的 `app-debug.apk`，但它属于 Debug 变体，可能暴露仅供开发使用的 Demo 控件。

## Goal / 目标

Publish an installable Release variant for direct Android use. The artifact must have
`BuildConfig.DEBUG=false`, keep development-only UI hidden, and remain reproducible without
committing a keystore or secret.

发布可直接在 Android 上使用的可安装 Release 变体。产物必须满足 `BuildConfig.DEBUG=false`、
隐藏开发专用 UI，并且不提交密钥库或任何秘密信息。

## Approach / 方案

- Build `assembleRelease` with the existing release configuration.
- Sign the local distribution artifact with the machine's standard Android debug keystore;
  this key is never copied into the repository or logged. The GitHub note labels the artifact
  as locally debug-key signed, while the APK itself is a Release variant.
- Publish a clearly named APK and `SHA256SUMS.txt`; preserve the older Debug APK release.

- 使用现有 Release 配置构建 `assembleRelease`。
- 使用本机标准 Android debug keystore 为本地分发产物签名；密钥不会复制进仓库或写入日志。GitHub
  说明明确标注该产物使用本地 debug key 签名，但 APK 本身是 Release 变体。
- 发布清晰命名的 APK 和 `SHA256SUMS.txt`，保留旧的 Debug APK Release。

## Non-goals / 不在本阶段

No production signing secret, Play Store upload, cloud service, new Luogu endpoint, credential
flow, database migration, or functional feature is introduced.

本阶段不引入生产签名密钥、Play Store 上传、云端服务、新洛谷接口、凭据流程、数据库迁移或新业务功能。

## Acceptance criteria / 验收标准

1. `assembleDebug`, `test`, `lintDebug`, and `assembleRelease` pass.
2. The signed Release APK installs and Android reports `versionName=0.3.36`, `versionCode=36`.
3. The Release APK launches with Debug-only Demo controls absent.
4. The release asset and `SHA256SUMS.txt` match the local SHA-256 digest.
5. No keystore or secret is tracked; the emulator remains online.

1. `assembleDebug`、`test`、`lintDebug` 和 `assembleRelease` 全部通过。
2. 已签名 Release APK 可以安装，Android 报告 `versionName=0.3.36`、`versionCode=36`。
3. Release APK 成功启动，Debug 专用 Demo 控件不存在。
4. Release 资产和 `SHA256SUMS.txt` 与本地 SHA-256 摘要一致。
5. 不跟踪任何密钥库或秘密信息；模拟器保持在线。
