# OJ NEXUS Quick Start / OJ NEXUS 首次使用

This guide describes the supported local-first Luogu workflow in the released Android app. /
本指南说明已发布 Android 应用中受支持的本地优先洛谷使用流程。

## 1. Install / 安装

1. Download `OJ-NEXUS-v0.3.46.apk` from the [v0.3.46 GitHub Release](https://github.com/tangyixiao/OJ-NEXUS/releases/tag/v0.3.46).
   / 从 [v0.3.46 GitHub Release](https://github.com/tangyixiao/OJ-NEXUS/releases/tag/v0.3.46) 下载 `OJ-NEXUS-v0.3.46.apk`。
2. Verify the download with `SHA256SUMS-v0.3.46.txt` before installing. /
   安装前使用 `SHA256SUMS-v0.3.46.txt` 校验下载文件。

## 2. Connect public Luogu data / 连接洛谷公开资料

1. Open `SETTINGS` and enter your public Luogu username in the Luogu account section. /
   打开 `SETTINGS`，在洛谷账号区域输入你的公开用户名。
2. Tap `CONNECT`, then use `SYNC NOW` when available. `/ 点击 `CONNECT`，随后在可用时点击 `SYNC NOW`。
3. `PROFILE` displays the locally cached public profile and Rating data after synchronization. /
   同步后，`PROFILE` 会显示本地缓存的公开资料和 Rating 数据。

Public sync uses content-only public endpoints and does not need a main-site password, browser
Cookie, Session, or CSRF token. / 公开同步只使用公开 content-only 接口，不需要主站密码、浏览器 Cookie、
Session 或 CSRF Token。

## 3. Browse a Luogu problem / 浏览洛谷题目

1. Open `PROBLEMS` → `REMOTE CATALOG` → `LUOGU`. /
   打开 `PROBLEMS` → `REMOTE CATALOG` → `LUOGU`。
2. Enter a PID or keyword, such as `B4132`. / 输入题号或关键词，例如 `B4132`。
3. Use `DETAIL` for the native problem statement, samples, and limits. /
   点击 `DETAIL` 查看原生题面、样例和限制。

## 4. Configure OpenApp and submit / 配置 OpenApp 并提交

1. In `SETTINGS` → `LUOGU OPEN PLATFORM`, open the official OpenApp documentation first. /
   在 `SETTINGS` → `LUOGU OPEN PLATFORM` 中，先打开官方 OpenApp 文档。
2. Enter the OpenApp user and secret issued by the official platform, then tap `SAVE CREDENTIAL`.
   / 输入官方平台提供的 OpenApp 用户和密钥，然后点击 `SAVE CREDENTIAL`。
3. From a native `DETAIL` page, tap `WORKSPACE`, choose a language, enter source code, and tap
   `SUBMIT`. / 从原生 `DETAIL` 页面点击 `WORKSPACE`，选择语言、输入源代码，再点击 `SUBMIT`。
4. After the explicit submit, the workspace checks the asynchronous result in the foreground.
   Terminal results appear automatically; `PENDING` results can be checked again. /
   明确提交后，工作区会在前台查询异步结果；终态结果会自动显示，`PENDING` 结果可以再次查询。
5. Open `PROFILE` → `SUBMISSIONS` to review local request metadata. Opening a problem request
   from there restores its saved title when available. / 打开 `PROFILE` → `SUBMISSIONS` 查看本地请求元数据；
   从那里打开题目请求时，会在可用情况下恢复已保存的题目标题。

The OpenApp secret is stored locally through Android Keystore. Source code and standard input
are not stored in submission metadata or uploaded to a cloud service. / OpenApp 密钥通过 Android Keystore
在本地保存；提交元数据不保存源代码和标准输入，也不会上传云端。

## Supported boundary / 当前边界

- Public profile, Rating, contest metadata, problem catalog, native details, and local submission
  result metadata are supported. / 支持公开资料、Rating、竞赛元数据、题库、原生题面以及本地提交结果元数据。
- Submission is an explicit foreground OpenApp action; POST requests are not automatically retried.
  / 提交必须由用户在前台明确触发，POST 请求不会自动重试。
- Main-site password/Cookie/Session/CSRF login, cloud/cross-device sync, and a local compiler or
  custom-input runner are intentionally not included. / 主站密码/Cookie/Session/CSRF 登录、云端/跨设备同步、
  本地编译器和自定义输入运行器暂不包含。
