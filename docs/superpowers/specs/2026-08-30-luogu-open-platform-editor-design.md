# 阶段 2：洛谷 Open Platform 与本地代码工作区设计

## 范围

本阶段为 OJ NEXUS 增加一个本地代码工作区，允许用户在应用内编辑代码，并通过洛谷官方 Open Platform 执行“运行”或“题库评测”。云端账号、跨设备同步、洛谷主站密码、主站 Cookie/Session/CSRF 登录均不在范围内。

## 官方接口边界

- 基地址：`https://open-v1.lgapi.cn`。
- 授权：HTTP Basic，凭据是洛谷 Open Platform 提供的 OpenApp Token，不把主站密码当作输入。
- 评测：`POST /problem`，请求包含 `pid`、`lang`、`o2`、`code` 和可选 `trackId`。
- 运行：`POST /run`，请求包含 `input`、`lang`、`o2`、`code` 和可选 `trackId`。
- 结果：`GET /result/{id}`；结果尚未产生时接受 HTTP 204，结果缓存窗口有限。
- 额度不足按 402 处理；401/403 不重试，不把授权失败伪装成普通网络错误。

## 安全与生命周期

1. OpenApp Token 仅由用户在设置中显式录入，界面不提供主站密码字段。
2. Token 由 Android Keystore 生成的 AES-GCM 密钥加密，密文保存于 `noBackupFilesDir`；Room、DataStore、数据库导出和日志均不包含 Token。
3. HTTP 客户端在每次请求时读取 Token，只设置 `Authorization` 请求头，禁止日志拦截器输出该头或请求体中的代码/输入。
4. 取消、清除凭据和应用卸载都应让后续请求失效；清除操作删除密文和相关内存引用。
5. 评测请求只由前台用户操作触发；网络重试只允许对结果 GET，POST 不自动重试，避免重复提交。
6. 未配置凭据、授权失败、额度不足、评测尚未完成、完成、编译失败、运行错误都必须有独立 UI 状态。

## 本地数据模型

保存一个不含代码正文、不含 Token 的 `submission_jobs` 表：judge、requestId、trackId、kind（RUN/PROBLEM）、pid、language、status、createdAt、updatedAt、lastErrorType。代码和标准输入只保留在当前编辑器状态，离开工作区或用户清除后释放。

## 编辑器与工作流

- 首版采用 Compose `BasicTextField`，不使用 WebView，不捆绑本地编译器。
- 题目评测从本地已同步题库中选择 PID；运行模式允许用户输入标准输入。
- 运行/提交按钮在未配置 OpenApp Token、代码为空、PID 为空或请求进行中时禁用。
- 提交后保存 request ID，前台轮询结果；退到后台只停止轮询，不启动后台提交。
- 结果页面显示编译消息、状态、输出和资源信息；不把服务器返回的原始错误当作 UI 文案。

## 分步实现

1. 先实现纯 Kotlin 请求模型、校验器、错误映射和 fake 凭据存储测试。
2. 接入 Retrofit Open Platform API 和 Keystore 凭据存储，加入请求头/状态码测试。
3. 增加本地任务表与迁移，完成运行/提交 repository 及结果轮询。
4. 接入代码工作区 UI、中英文资源和前台生命周期控制。
5. 运行全量测试、Debug 构建和 lint；无真实 OpenApp Token 时不声称完成线上提交验证。

## 明确不做

- 不实现洛谷主站密码登录。
- 不抓取或持久化主站 Cookie、Session、CSRF。
- 不自动提交，不在同步 Worker 中提交代码。
- 不实现云端服务、账号系统、跨设备同步或云端代码保存。
- 不把洛谷 Open Platform 当成本地 C++ 编译器；本阶段的“运行”是官方远程运行接口。
