# Android 当前功能与数据边界

当前候选 Android `0.78.0-01` / code86，固定桌面 `v0.78.0` / `d52e0b9cf970a03a30a5f5a1e9139693bb211cd8`；上一正式发行0.77.0-02。本轮验证ZCode保存标题、子代理/工作流归组，fork和side chat保持独立；复用现有Hub解析与展示，不在Android读取SQLite。三项持续排除规则及既有功能保持，`fullyAligned=false`。实跑与未测见[当前验收](VALIDATION.md)。

上一正式基线0.77.0-01的并发模型实时速率、New API账户分周期消费及Hub/Cursor/Cline兼容保持，13项增量映射仍见[当前交接](HANDOFF.md)。不补手机采集，`fullyAligned=false`保留；公开发行不证明NAS或手机已更新。

| 功能 | Android 当前范围 | Hub / Agent 依赖及限制 |
|---|---|---|
| 统计、趋势和日/月记录 | 读取权威总量、历史与日/月明细，保留日历选日/月和时区一致性 | Hub 提供历史与周期数据；缺数据、未加载、缓存、失败和零值分别显示 |
| 项目工具/模型占比 | 展开后切换，权威汇总models优先，别名、未知剩余及紧凑响应式指标 | 新Agent/Hub提供projects.models；旧汇总缺模型不借会话补齐，明细不完整禁止会话模型回退；API36模拟器定向及12组窗口验收通过，生产Hub/真机未验证 |
| 会话分组及指标 | 关系、续接、Review/Grok 分组与混合排序；去重账目汇总调用、缓存率、生成速度和最近活动 | 依赖 messageCount、input/cache/output、timed* 及关系字段；缺失指标不可用，不平均百分比/速度/时间戳 |
| 会话活动与等待 | 消费有限租约、waitingForInput、turnEnded 等可选字段 | 不补本地活动 overlay；批准原因、远程逐轮正文（包括OMP）与 Grok 跨历史增强缺完整合同 |
| Cursor X 套餐 | Included usage 与真实百分比、预付 USD 余额分开；账号、时效和旧套餐兼容 | Agent 负责采集及 cents → USD；手机仅消费 windows，不接入桌面 Cookie/登录，真实账号仍待验收 |
| OpenCode 2.x | 新旧 Hub wire 的来源、token、时间、分组及去重兼容 | session_v2/session_message 数据库恢复属于采集端；样例链路不等于真实采集端到端 |
| Goose / Notion / WorkBuddy 等 | 消费来源身份、健康、额度周期与独立账号数据 | 可选字段依赖 Agent/Hub；远程 Pi/CodeBuddy 会话正文没有新增合同 |
| 设备与连接信息 | 本机、Hub、采集设备卡片；Xiaomi 型号映射、可选 Hub displayName 与诚实降级 | 友好名称来自鉴权数据，不能从 IP、health 或 collector 推测；生产部署另行核验 |
| 设置、字体、订阅与价格 | 紧凑页内设置、现有内置字体、额度比例/间距与去重订阅入口；模型价格只读 | 已加载订阅区手动刷新按钮已删除，保留未加载读取和现有自动同步 |
| Android 自身更新 | 仅明确点击发起，保留加载/结果/失败及说明；前后台与重建不触发或自动重试 | 只使用正式版本编号；旧三段编号客户端需手动覆盖安装一次，见[版本规则](../VERSIONING.md) |
| 手机设置精简 | 移除桌面最新版查询、自定义字体名称输入、主题码操作 | 保留Android更新、静态桌面参考、字体预设及旧字体/配色；持续排除规则见[上游同步](UPSTREAM_SYNC.md#android-持续排除项) |
| 本地采集及桌面系统集成 | 不适用 | Electron、Windows Edge Dock、SQLite恢复、鼠标悬停、桌面安装器和后台解锁检查不移植 |

客户端自动化、模拟器、真实账号、真实 Hub 和实体手机分别记账；NAS 发行存在不证明已部署。当前证据与未验证项见[当前交接](HANDOFF.md)、[UI证据](archive/UI_072_VALIDATION.md)和[当前待办](HANDOFF.md)。


桌面小组件已取消；历史调查保留在有限档案，未经用户明确要求不恢复。本机采集及桌面系统集成不适用。
