# Android 当前功能与数据边界

当前记录基线为 Android `0.72.0-01` / code76，固定桌面 `v0.72.0` / `126e9a43dcc51d3d7b2609231eac9974ba73bed7`。客户端适用功能对齐不等于全平台功能对等，`fullyAligned=false` 保留。逐上游提交、代码、字段和验收映射见[0.72 清单](DESKTOP_072_ALIGNMENT.md)，既有增量见[0.70](DESKTOP_070_DIFF.md)与[0.71](DESKTOP_071_DIFF.md)。

| 功能 | Android 当前范围 | Hub / Agent 依赖及限制 |
|---|---|---|
| 统计、趋势和日/月记录 | 读取权威总量、历史与日/月明细，保留日历选日/月和时区一致性 | Hub 提供历史与周期数据；缺数据、未加载、缓存、失败和零值分别显示 |
| 会话分组及指标 | 关系、续接、Review/Grok 分组与混合排序；去重账目汇总调用、缓存率、生成速度和最近活动 | 依赖 messageCount、input/cache/output、timed* 及关系字段；缺失指标不可用，不平均百分比/速度/时间戳 |
| 会话活动与等待 | 消费有限租约、waitingForInput、turnEnded 等可选字段 | 不补本地活动 overlay；批准原因、远程逐轮正文与 Grok 跨历史增强缺完整合同 |
| Cursor X 套餐 | Included usage 与真实百分比、预付 USD 余额分开；账号、时效和旧套餐兼容 | Agent 负责采集及 cents → USD；手机仅消费 windows，不接入桌面 Cookie/登录，真实账号仍待验收 |
| OpenCode 2.x | 新旧 Hub wire 的来源、token、时间、分组及去重兼容 | session_v2/session_message 数据库恢复属于采集端；样例链路不等于真实采集端到端 |
| Goose / Notion / WorkBuddy 等 | 消费来源身份、健康、额度周期与独立账号数据 | 可选字段依赖 Agent/Hub；远程 Pi/CodeBuddy 会话正文没有新增合同 |
| 设备与连接信息 | 本机、Hub、采集设备卡片；Xiaomi 型号映射、可选 Hub displayName 与诚实降级 | 友好名称来自鉴权数据，不能从 IP、health 或 collector 推测；生产部署另行核验 |
| 设置、字体、订阅与价格 | 紧凑页内设置、现有内置字体、额度比例/间距与去重订阅入口；模型价格只读 | 已加载订阅区手动刷新按钮已删除，保留未加载读取和现有自动同步 |
| Android 自身更新 | 启动/恢复时前台节流，与关于页共用状态；手动检查和说明入口保留 | 只使用正式版本编号；旧三段编号客户端需手动覆盖安装一次，见[版本规则](../VERSIONING.md) |
| 桌面最新版查询 | 保留独立桌面稳定发行查询 | 不与 Android APK 版本或 Hub build 混为一谈 |
| 桌面小组件 | 已取消，现行产品不提供 | [取消功能档案](archive/INDEX.md#已取消的小组件)只作追溯，未经用户要求不恢复 |
| 本地采集及桌面系统集成 | 不适用 | Electron、Windows Edge Dock、SQLite恢复、鼠标悬停、桌面安装器和后台解锁检查不移植 |

客户端自动化、模拟器、真实账号、真实 Hub 和实体手机分别记账；NAS 发行存在不证明已部署。当前证据与未验证项见[当前交接](HANDOFF_0.72.0-01.md)、[UI证据](UI_072_VALIDATION.md)和[当前待办](NEXT_TASKS.md)。

历史 A/B/C/D/E 分类原文保留于[整理前矩阵](archive/history/FEATURE_MATRIX_BEFORE_CLEANUP.md)。现行约束中的 C 类继续指缺远程必要数据的能力；不得通过手机本地采集补齐。
