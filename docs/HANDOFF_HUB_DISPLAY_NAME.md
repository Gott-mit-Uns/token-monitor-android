# 手机与 Hub 显示名称：联合交接

本改动来自独立 `fix/sync-device-display-names` 分支，基线 b9a8de1。所有者已在 Android 对齐聊天要求纳入联合验收和交付，因此交给现有 Android PR #7 整合；不另建 Android PR、不提升独立候选版本、不创建独立 Release。

## 已实现

- `SyncSettingsPresentation.kt`：匹配实际手机 `2304FPN6DC`，保留既有 `2304FPN60C` 映射以兼容并行工作；匹配时只做 trim/Locale.ROOT 大写，其他型号保持原文本。手机仍只读，不成为采集设备。
- `HubDisplayName.kt`：仅接受有界、无控制/格式/孤立代理码点的显示名称。
- `HubData.kt`：从可选 `hub.displayName` 读取到 `Snapshot.hubDisplayName`，默认 null。HTTP/完整 SSE 投影无需改写；freshness 的现有 copy 保留名称，加密缓存沿用原始快照。
- `SyncSettingsPresentation.hubHostName`：优先已接受快照中的名称，再沿用 DNS/名称未知回退，绝不从采集设备或 IP 猜测。
- `HubDisplayNameTest.kt` 与 `SyncSettingsPresentationTest.kt`：解析、HTTP/SSE projection、freshness 不可改名、缺失/异常/旧 Hub、离线显示及机型边界。
- `HubStoreTest.kt`：真实加密存储重开、名称不以明文保存、冷恢复名称与用量保持。
- `SyncDeviceGridScreenTest.kt`：数字 IP 的明确名称、离线保留、旧快照清除名称和截图断言；复用现有浅深色/大字体卡片测试。
- `HUB_API_CONTRACT.md`：合同和兼容边界。

Hub 配套是 NAS PR #11：`TOKEN_MONITOR_HUB_DISPLAY_NAME=DXP4800`，名称仅在鉴权 stats/普通及 Windows 完整 SSE 中；匿名 health 不公开。配置不能改成 Agent 的 `TOKEN_MONITOR_DEVICE_ID`。

## 测试状态与联合接管

独立工作区的初始候选完成 43 项定向单元测试，0 失败/错误/跳过，Debug 应用与测试 APK 构建完成。此后为保留并行任务的旧 `2304FPN60C` 映射，只补回同一分支和对应断言；冻结交接后不再单独重跑 Gradle，最终整合提交仍须由 PR #7 重跑相关测试。Lint 的完成状态在聊天交接中另报，不能由上述构建结果推断。

新建独立 API36 模拟器因宿主磁盘空间不足未启动，因此本分支没有 UI/加密缓存 instrumentation 通过声明。最终联合候选必须执行：`HubDisplayNameTest`、`SyncSettingsPresentationTest`、`HubJsonProjectionTest`、`HubFreshnessTest`、`HubStoreTest`、`SyncDeviceGridScreenTest`（浅/深色），并确认旧 Hub、离线及冷恢复。不同候选的结果分别记录，不将初始 43 项通过写成最终合并提交全部通过。

无本任务子代理；本任务没有写 `work/token-monitor-android-072`。该目录同时存在的六项订阅刷新按钮/文案/机型修改来自其他来源，联合整合时保留，不能用本分支文件整体覆盖。建议三方 cherry-pick，并在 `SyncSettingsPresentation` 的冲突中保留两种机型与新增 Hub 名称读取。订阅按钮不属于本名称分支。

本任务不操作 emulator-5556 或另一任务的 emulator-5590，不覆盖生产 APK，不读取凭据，不修改或部署 NAS。最终版本、签名、联合 APK、自动审查及 Release 状态由 Android PR #7 的实际证据决定。
