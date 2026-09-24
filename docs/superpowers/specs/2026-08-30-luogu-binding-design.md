# OJ NEXUS 洛谷账号绑定设计

## 目标

在设置页增加洛谷公开用户名绑定。用户只输入洛谷用户名，应用通过洛谷公开用户搜索接口确认账号，保存规范化用户名和验证状态；不请求、存储或发送密码、Cookie、Session 或 CSRF 凭据。

## 范围

- 新增独立的 `LuoguAdapter`、公开 API DTO、限速客户端和 `LuoguAccountConnector`。
- 将洛谷 Adapter 和 Connector 注册到现有 `JudgeRegistry`，设置页自动显示 LUOGU 面板。
- 洛谷本阶段只声明 `ACCOUNT_BINDING` 能力；未实现稳定的提交/题库同步，不显示会导致异常的同步按钮。
- 绑定成功后复用现有 Room `JudgeAccountEntity`，沿用单 OJ 单活跃账号、重绑替换和断开保留本地训练历史的规则。
- 输入为空、格式不合法、搜索无精确匹配、HTTP/网络/解析异常均映射为现有设置页错误状态。
- 用固定 JSON 样例测试 DTO 解析，用 fake adapter 测试 connector 的 trim、精确匹配、异常映射和公开账号验证。

## 公开数据边界

洛谷当前公开用户搜索接口为 `GET /api/user/search?keyword=...`，返回候选摘要。适配器只接受 `name` 与规范化输入完全匹配的候选，避免把模糊搜索第一项错误绑定。来源标记为 `EXPERIMENTAL`，因为该接口不是稳定的官方开发者 API。

## 资源与架构

网络 DTO 仅存在于 `judge/luogu` 包；核心只接收 `AccountBinding` 和统一的 `JudgeAccountEntity`。洛谷客户端拥有自己的请求间隔和有限重试，错误不泄漏到其他 OJ。设置页根据 Adapter capabilities 隐藏洛谷同步操作，并使用现有本地化资源呈现 LUOGU、验证状态和错误文案。

## 验收标准

1. 输入 `kkksc03` 这类公开且精确匹配的用户名可绑定，保存 canonical handle，状态为 `VERIFIED`，来源为 `EXPERIMENTAL`。
2. 空输入、非法字符、无精确匹配和网络/API 错误都不会写入账号表。
3. 洛谷面板可在设置页显示；绑定后不出现未实现的同步按钮。
4. Codeforces/AtCoder 现有绑定和同步测试不受影响。
5. `tools\\gradlew-local.bat clean test assembleDebug lintDebug` 成功，模拟器安装后可实际完成一次洛谷绑定。
