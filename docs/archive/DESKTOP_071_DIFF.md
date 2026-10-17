# 固定桌面 v0.71.0 增量

2026-10-15 Asia/Shanghai。Android 基于 GitHub main `77f2ff9`，最新正式 v0.2.64/code73/tag `a7d5099`。未提交主工作区文档及 APK 体积实验不合入。独立分支 `feat/desktop-071-candidate`。仅比较 v0.70.0 `ac4eeb2c4aea239dd94b67f074c73f69aafc46f8` 至 v0.71.0 `a514e2063563e318ef73c2a17fad68057c4e300c`，不跟随后续主线。

| 分类 | 增量及 Android 处理 |
| --- | --- |
| 直接适配 | Goose 客户端目录、`source.checks[].id=goose-db`，真实健康枚举和未知降级；权威会话累计量不重算、不把单记录 messageCount 当真实调用次数，缓存输入比例避免重复叠加 |
| 直接适配 | Notion AI provider 目录、滚动6小时/session360分钟及月度/billing窗口；实际 used/limit 百分比，缺 reset 明确未知；多账户按采集端 hash 区分；不进入 money/credits/Token总量 |
| 直接适配 | WorkBuddy 精确 accountKey 绑定，键失配不切换同邮箱/同名账户；完整快照整组健康、独立键、窗口/币种/边界/region兼容时展示合计，合计不进入任何绑定或权威余额 |
| 依赖 Hub | 新版采集端须提供 Goose totals/会话/health、Notion额度和稳定私有hash、WorkBuddy 独立账户及失败状态。Android不能补采集。旧Hub继续可读，不将缺字段推成0/满额 |
| 桌面专属 | WorkBuddy 官方浏览器登录/管理账户/凭据刷新与本地App认证、Notion手动Cookie工作区配置、Goose本地DB扫描、Codex执行器探测、托盘/菜单栏、冷启动usage archive优化、官网动画 |
| 真实缺口 | Pi/CodeBuddy 新聊天明细是本地 session:getDetail IPC 与文件解析；Node Hub路由、Worker和API合同没有远程明细接口。Android目前只有会话摘要/模型/上下文，保留已有刷新状态，不伪造消息 |
| 待验证 | Notion真实Business/Enterprise登录/资格及内部API；新采集端到实际NAS Hub的数据链、实体手机覆盖升级和实网LAN/HTTPS/蜂窝/锁屏恢复。本轮只用公开合成fixtures及独立模拟器 |

上游证据：固定tag的 `docs/providers/goose.md`、`notion.md`、`workbuddy.md`，`src/shared/clientCatalog.js`、`clientHealth.js`、`providers/notion/limits.js`、`limits/core.js`、`src/electron/renderer/limits/providerPresentation.js`（workbuddyCreditTotals）、`src/shared/subscriptionDisplay.js`、`src/electron/preload.js`、`src/hub/server.js`、`docs/API.md`。对应本地只读冻结 work/token-monitor-reference/desktop-v0.71.0。

0.70已完成的CatPaw、MiniMax/TypeSafe余额、会话投影性能及既有八页/主题/字体/动效/紧凑趋势不重复实施。删除Android Widget的决定保留，远端electron-widget/Mac Widget继续作为设备信息。R8/resource shrinking依旧关闭。

## NAS最小协调需求

无需为Android变更已有stats/history/SSE路径。升级采集/Hub时保持 provider目录含notion；透传上述标准字段，稳定hashed accountKey、WorkBuddy region以及逐账户status/stale/actionRequired/完整windows。不要传原始user/workspace ID、Cookie、凭据或provider原始响应；缺资格/未知schema必须unavailable。完成独立安全验证后才部署，Android本轮不操作NAS。

远端聊天明细若另行授权，至少需要私有能力发现、精确device/client/sessionId归属、显式opt-in及标题/正文隐私边界、有界分页/大小、稳定message/turn键和revision、取消及401/403/404语义。客户端仅在能力确认后前台显式读取、原revision刷新保留展开/滚动、不缓存或同步正文。这个提案不是v0.71.0既有协议，也不是本轮已实现功能。
