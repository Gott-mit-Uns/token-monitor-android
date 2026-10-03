# 简体中文术语与覆盖检查

依据桌面端 Javis603/token-monitor **v0.65.0** 的 `src/electron/renderer/i18n.js` 中 `MESSAGES['zh-CN']`，本地源检出的标签经 `git describe --tags --exact-match` 确认为 v0.65.0。GitHub API 本次访问失败，使用已有的同版本源码核对，未凭记忆补充上游译文。

参考原文件：https://github.com/Javis603/token-monitor/blob/v0.65.0/src/electron/renderer/i18n.js

本文件是术语与实现检查记录。不得把数据协议键、枚举存储值、模型 ID、供应商品牌、URL、包名或应用签名一并翻译。

## 与桌面端保持一致的术语

| 英文/含义 | 简体中文 | 桌面端 key |
|---|---|---|
| Home | 主页 | views.home |
| Tools | 工具 | views.tool |
| Status | 状态 | views.status |
| Devices | 设备 | views.device |
| Models | 模型 | views.model |
| Projects | 项目 | views.project |
| Sessions | 会话 | views.session |
| Limits | 额度 | views.limits |
| Trends | 趋势 | views.trends |
| Activity | 活动 | home.activity |
| Overview | 总览 | dashboard.tab.activity |
| Total tokens | 总 Token | dashboard.stat.totalTokens |
| Total cost | 总花费 | dashboard.stat.totalCost |
| Cost ranking | 成本 | settings.modelRanking.cost |
| Today | 今日 | edgeDock.period.today |
| Month | 本月 | periodRange.month |
| Week | 本周 | periodRange.week |
| Last 7 days | 最近 7 天 | periodRange.last7 |
| Last 30 days | 最近 30 天 | periodRange.last30 |
| All time | 总计 | edgeDock.period.allTime |
| 90 days / year | 90 天 / 1 年 | dashboard.range.90 / .365 |
| Account | 账号 | settings.codex.title / .nAccounts |
| Current balance | 当前余额 | subscription.tooltip.balance |
| Prepaid balance | 预付余额 | settings.limits.prepaidBalance |
| Used | 已用 | home.used |
| Remaining | 剩余 | settings.limits.barsRemaining |
| Reset | 重置 | home.reset |
| Input cache hit | 输入 (缓存命中) | dashboard.tooltip.inputCacheHit |
| Input cache miss | 输入 (缓存未命中) | dashboard.tooltip.inputCacheMiss |
| Output | 输出 | dashboard.tooltip.output |
| Unclassified | 未分类 | dashboard.tooltip.unclassified |
| Active days | 活跃天数 | trends.activeDays |
| Current streak | 连续天数 | trends.currentStreak |
| Longest streak | 最长连续 | trends.longestStreak |
| Active time | 活跃时间 | trends.activeTime |
| Peak day | 峰值单日 | trends.peakDay |
| Peak | 峰值 {value} | home.peakTokens |
| Messages | 消息数 | dashboard.stat.messages |
| By tool / model | 按工具 / 按模型 | dashboard.stack.client / .model |
| Bars / K-line | 柱状图 / K 线 | dashboard.mode.bars / .kline |
| Subscriptions | 订阅 | settings.limits.capability.subscription |
| Connect to Hub | 连接到 Hub | settings.sync.connectHub |
| Hub URL | Hub URL | settings.sync.hubUrl |
| Secret / shared secret | 密钥 / 共享密钥 | settings.sync.secret / .sharedSecret |
| Device ID | 设备 ID | settings.sync.deviceId |
| Refresh | 刷新 | settings.codex.refresh |
| Reorder reset | 重置排序 | settings.views.resetOrder |
| Show all | 显示全部 | settings.views.showAll |
| Customize Home | 自定义主页 | home.customize |
| Just synced device | {age}同步 | devices.synced |
| Stale device | 离线 | home.staleDevice |
| Stale quota | 数据过期 | settings.limits.status.stale |
| Source device | 来自 {device} | settings.limits.device.from |
| No synced data | 暂无同步数据 | settings.limits.status.noSyncedData |
| Need sign in / again | 需登录 / 重新登录 | settings.limits.status.signIn / .relogin |
| Disabled | 已停用 | settings.limits.status.disabled |
| Limited source | 来源受限 | settings.limits.status.usageApiLimited |
| Unavailable | 暂不可用 | settings.limits.status.unavailable |
| Healthy / degraded / outage / unknown | 正常 / 降级 / 中断 / 未知 | serviceStatus.ok / .degraded / .outage / .unknown |
| Authentication error | 密钥错误或缺失 | settings.sync.offline.unauthorized |
| Connection interrupted | 连接中断，正在重连 | settings.sync.offline.disconnected |
| Connect failed | 连接失败 | settings.sync.offline.network |
| Timeout | 连接超时 | settings.sync.offline.timeout |
| DNS failure | 找不到主机：URL 可能填错 | settings.sync.offline.dns |

## 安卓补充译法（不是照抄桌面端已有键）

- Settings：设置；Close：关闭；Save：保存；Cancel：取消；Find：查找；Disconnect：断开连接；Move up/down：上移／下移。
- Estimated cost：估算费用。桌面用“成本”“总花费”的地方可以一致，但金额是 API 定价等值估算时不得误写为“实际账单”；余额保留供应商原币。
- Cache write：缓存写入。不要误合并为缓存命中；Hub 单独报告 cacheWriteTokens。
- 单次额度的 Session 是额度窗口，不是“会话明细”。若有可靠 kind=session，可以显示“5 小时额度”；daily/weekly/monthly/billing 可显示“每日／每周／每月／账单周期”。桌面主页的 Session/Daily/Weekly/Billing/Monthly 本身仍为英文，安卓可完整汉化，但品牌／未知 label 需保留原值。
- Expires/expiry：到期；Reset/reset：重置；Changes/mixed：变更。根据 boundaryKind 区分，不能都译作重置。
- SSE：实时推送；轮询：定时刷新；小组件 Live：实时更新。网络方式和设备上报频率需区分，避免把 widget 60 秒请求说成设备 60 秒上报。
- 缓存数据、设备离线、账号额度过期、手机连接失败是不同状态，不能统一称为“离线”。
- Hub 凭据是“Hub 共享密钥”，不是“Cloudflare API 密钥”。不应在只读安卓端引导用户登录供应商账号；鉴权失败应引导检查 Hub 连接信息。

## 仍需覆盖的英文出口

检查时源文件尚未完整汉化，以下是容易只改主页而漏掉的出口。行号可能随实现变化，按文件和函数查找。

1. `ui/DashboardViewModel.kt`：DashboardView 的 label、首次连接成功／取消／发现 Hub／断开提示。持久化枚举用 `.name`，翻译 `.label` 不应修改枚举名。
2. `ui/UsageFormatting.kt`：`formatDuration`、`formatActiveDuration`、`formatRelativeAge`、`formatReset`、`formatBoundary`、`formatCapturedAt`、`windowPeriodLabel`、`windowTitle`。目前英文 `Reset now`、`Expires`、`just now`、`ago`、`Not reported` 等以及 US 星期月份会继续出现。
3. `ui/UsageExplorer.kt`：期间对比、不完整历史、搜索无匹配、日详情、模型归因说明、记录天数和估算费用。
4. `ui/LimitsScreen.kt`：account status 映射、无额度数据、来源/更新时间、可用量、订阅默认名。不要直接改协议值 `notConfigured`、`unauthorized`、`rateLimited`、`sourceRateLimited`、`noSyncedData`。
5. `ui/ProjectsScreen.kt`：上下文已用／剩余及动态标题；`DevicesScreen.kt`：TOP MODELS、设备同步时间和零用量说明。
6. `ui/ConnectionSettings.kt`：主题/字体/减少动画/额度显示枚举 label、说明、小组件引导、首页配置上移下移按钮的 contentDescription。
7. `ui/DashboardChrome.kt`、`ActivityHeatmapGrid.kt`：返回、刷新、设置、视图选择及图表 TalkBack contentDescription。Compose 动画调试 label（如 `segmented indicator`）不面向用户，没必要翻译。
8. `widget/WidgetDeckData.kt`、`WidgetDeckRenderer.kt`、`WidgetDeckDrawing.kt`：LIVE/OFFLINE/CONNECTING/STALE/NO DATA/SAVED、Canvas 标题、估算费用、7 days、活跃天数、消息数、底部操作和 RemoteViews contentDescription。只改 strings.xml 不会翻译 Canvas 字符串。
9. `widget/WidgetLiveService.kt`：前台通知标题、说明、Stop、通知频道名、会话结束/超时/系统暂停提示。ACTION_*、CHANNEL ID、intent extra key 必须保持。
10. `data/network/HubApiClient.kt`、`HubAddressValidator.kt`、`HubRepository.kt`、`data/storage/SecureConnectionStore.kt`：网络、地址、Keystore 失败提示。后台异常 presentation 最后同样进入界面。
11. `data/network/ServiceStatusClient.kt`：本地错误 fallback 可以汉化；第三方 status 页返回的事件原文／名称不应靠静态逐字替换篡改，保留原文或明确来自供应商。
12. XML strings、布局 contentDescription、manifest label、通知权限和桌面小组件配置入口需一起检查。

## 实现和验证注意事项

- 不把全文件英文字符串机械替换为中文：JSON 字段、URI/path、enum.name、ACTION、字体名和模型/provider ID 均是稳定标识。
- 品牌 Token Monitor、Codex、Claude Code、DeepSeek、Hermes Agent、Cloudflare、Tailscale、OpenCode，以及 Hub/SSE/HTTPS/API 名称保留。
- 用量总数可继续逗号分组；紧凑数字使用中文“万／亿”或保留现有 K/M/B 必须统一。只改单位字符串而不改数值除数会导致数量错误。
- `UsageFormatting` 的日期 `MMM d, h:mm a` 和星期 `EEE, MMM d` 使用英文或系统 Locale；中文界面应该指定合适的中文模式／locale，例如 M月d日 HH:mm、周几，时区仍用系统时区。
- 小组件绘制为位图，需真实渲染中文 glyph；自定义字体通常没有完整汉字，通过 Android fallback 字体显示，不得用英文字母宽度估算中文宽度。
- 不把小组件 action/stat ID、状态判定字符串一起翻译。中文文案更短不代表所有文本必然能容纳；保留 measureText/fitText 界限并检查 4×2 实际位图。
- 升级迁移测试应保持 enum.name、偏好 key 和缓存协议不变。英文默认 provider/model/session/project 标题与真实用户标题应区分；只汉化 App 的默认空标题。
- 测试中精确比较英文 label、错误文案、通知内容的断言需要合理更新。协议／数值断言应保持不变。
- 至少查看首次连接、主页、额度、趋势、设备、设置与小组件的合成截图；检查 TalkBack 和错误入口，不能仅以“编译通过”认定没有英文遗漏。
