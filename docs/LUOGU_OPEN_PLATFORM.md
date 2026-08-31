# Luogu Open Platform 工作区

阶段 2 使用洛谷官方 Open Platform，而不是主站登录流程。

- API 基地址：`https://open-v1.lgapi.cn/`
- 认证：HTTP Basic，凭据由 Open Platform 提供。
- 题库评测：`POST /judge/problem`
- 查询异步结果：`GET /judge/result/{requestId}`；尚未产生结果时为 204
- 查询账户可用计费点：`GET /judge/quotaAvailable`

应用只在设置页显式录入 OpenApp 用户和密钥，并通过 Android Keystore 加密后写入
`noBackupFilesDir`。凭据不进入 Room、数据库备份、日志或同步任务。工作区代码和标准输入
只保留在当前 ViewModel/UI 生命周期内；提交任务只保留请求 ID、题目、语言和结果元数据，
并在工作区重启后恢复该题最近一次本地任务的查询状态。

代码工作区提供洛谷官方语言标识的选择器，当前覆盖 C/C++、Python/PyPy、Java、Kotlin、
Go、Rust、Pascal、Haskell、Node.js、PHP、Ruby、Perl 和 Scala；提交请求会原样使用用户
选择的 `lang` 标识。语言列表以官方文档为依据，平台新增语言时再单独更新列表。

洛谷公开题库同步结果可在 Problems 的远端题库筛选器中选择 `LUOGU`，再将条目加入本地
题库；加入时会生成标准洛谷题目链接，不会把远端缓存自动复制成个人题目。

题库评测产生终态结果后，任务仓库会将一次真实的用户发起评测幂等写入本地 `attempts`，
并用 `(source_judge, external_submission_id)` 防止重复计数。历史主站提交记录仍不能由此
推断或补造。

运行与提交都需要用户前台点击。提交 POST 不自动重试，结果由用户点击“查询结果”获取；
这样不会因为网络重试造成重复评测。401、403、402 和 204 分别映射为授权失败、无权访问、
配额不足和等待中。

提交中心从本地 `submission_jobs` 表读取最近请求。它展示请求类型、题号、语言、请求 ID、
时间、状态和已有评测元数据；对等待中或失败的请求，用户可以在前台点击“查询结果”。查询
错误会保留在本地界面，成功查询同一请求后才清除。题目请求可以返回工作区，运行请求不会
伪造题目入口。提交中心不读取源码或标准输入，也不执行后台轮询。

设置页的“查询可用计费点”是用户主动触发的前台查询。额度响应只保留在当前 ViewModel
状态中，不写入 Room、备份或同步数据；应用展示官方返回的可用点数总和和活动额度批次数。
设置页同时提供官方 OpenApp 文档入口，用户可在录入凭据前核对凭据来源和评测能力边界。

本阶段不实现洛谷主站密码、Cookie、Session、CSRF，不实现云端服务/跨设备同步，也不捆绑
本地 C++ 编译器。官方 Open Platform 当前只提供题库评测接口；题库评测本身包含远程编译
和运行。自定义输入运行没有在官方 API 规范中提供，因此工作区不会把它当作可用能力。

## 外部参考

- [洛谷开放平台文档](https://docs.lgapi.cn/open/)：评测能力、题库导出和 API 边界。
- [洛谷帮助中心源码](https://github.com/luogu-dev/docs)：题目、竞赛和 Markdown 规则参考。
- [Markdown*Palettes](https://github.com/luogu-dev/markdown-palettes)：网页端 Markdown 编辑交互参考。

Markdown*Palettes 是 Vue 网页组件，本项目保持原生 Android Compose，不复制网页组件或引入
WebView。帮助中心内容受 CC BY-NC-ND 4.0 许可约束，应用只使用链接和结构作为参考，不打包
其题面或帮助正文。

## Native problem details / 原生题目详情

The remote Luogu catalog now opens a native Compose detail screen through the public
`problem/{pid}` content-only page. It shows the problem description, input/output format, samples,
limits, and a safe subset of Markdown without executing HTML or embedding a WebView. / 远端洛谷题库
现在可以通过公开的 `problem/{pid}` content-only 页面打开原生 Compose 详情页，展示题目描述、
输入输出格式、样例和限制，并以安全子集展示 Markdown，不执行 HTML，也不嵌入 WebView。

The user can still open the canonical source page or the local Open Platform workspace explicitly.
No main-site password, cookie, session, CSRF state, or cloud synchronization is involved. /
用户仍可主动打开标准原题页面或本地 Open Platform 工作区；不涉及主站密码、Cookie、Session、
CSRF 状态或云端同步。

## Arena contest details / Arena 竞赛详情

For Luogu contests, Arena reads the public `contest/{id}` content-only response and shows the
official description and server-provided contest problem membership. Scores, indexes, PIDs, and
titles are taken from that response; no membership is inferred from a separate catalog. / 对于洛谷
竞赛，Arena 读取公开的 `contest/{id}` content-only 响应，展示官方说明和服务器返回的竞赛题目
成员关系；分值、编号、PID 和题名均取自该响应，不从独立题库推断成员关系。
