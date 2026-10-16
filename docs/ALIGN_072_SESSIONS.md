# v0.72 会话分组指标对齐

目标固定为桌面 v0.72.0 / `126e9a43dcc51d3d7b2609231eac9974ba73bed7`。本子项只核对 #1053，不宣称其他功能已经对齐。

## 上游与 Android 映射

GitHub API 实时读取 [PR #1053](https://github.com/Javis603/token-monitor/pull/1053)，合入提交 `631c25cb4947bd7a75aebf5997bbeadd33cc9c98`；锁定目标版本的 [sessionRows.js](https://github.com/Javis603/token-monitor/blob/126e9a43dcc51d3d7b2609231eac9974ba73bed7/src/electron/renderer/sessionRows.js) 定义 `sessionUsageMetrics`、`sessionCacheHitPercent`、`subagentGroupRow`。本轮直接读取源码，没有 REA 或桌面运行验证。

| 项目 | 原 Android 行为 / 修改位置 | 数据依赖与验收 |
|---|---|---|
| 分组调用次数 | 原关系组沿用成员字幕且没有全组活动指标；`SessionFamilyMetrics.from` 对同组账目键去重求和 | `messageCount` 是携带 usage 的回复/调用数，不是完整聊天消息数。成员任一缺失或非法，整体显示“调用次数未知”，不以其他成员推算 |
| 缓存命中率 | `SessionFamilyMetrics` 累加原始输入分量后相除，`SessionListDisplay.cachePercent` 支持真实冷缓存 0% | 常规分母 input+cacheRead+cacheWrite；Goose 的 input 已含 cacheRead，分母只用 input。成员未知则整组不可用；分母0无读数，不平均百分比 |
| 生成速度 | 按成员先 cap(timedOutput,output)，再累加与正 duration 毫秒；输出乘1000除毫秒总和 | 没有 duration 的输出不借用其他成员的计时。无有效计时输出显示“— tok/s”。这是有计时数据部分的速度，与桌面口径一致，不代表无计时成员速度 |
| 最近活动 | 沿用组内最大 `sortTime`，UI 使用同一时间格式化函数 | 不平均时间戳，测试父/子/孙三级时间顺序 |
| 展开成员与汇总 | 保留 `reviews` 中自己的原始指标及 main/subagent 身份；Review/Grok 也提供聚合指标 | 不改 period 总数，不把虚拟 summary 再算入父组；重复同 id 仅算一次，不按 native identity 删除不同账目行 |
| 委托根角色 | `SessionRelationshipGroups.delegatedRole` 从同身份所有 continuation 的有效 parent link 判定，Compose沿用本地化角色资源 | 未挂父关系的新 continuation 不抹掉角色；内部别名链接与冲突证据不提供角色，独立委托根也保留角色 |
| 上下文与 prompt-cache | 关系组只读取最新 main 成员（既有排序），不合并窗口/估计 | 源数据缺失则不生成上下文，Grok/Review 没有 main，不制造上下文 |

## 缺失数据策略与 Hub 边界

旧 Hub 和历史缓存没有必需新字段，仍可显示会话/费用/Token。`cacheReported: true` 是可选证据，用于区分有输入的真实冷缓存0%与没有缓存遥测；正缓存流量也能证明遥测存在。计算仍要求 input/cacheRead/cacheWrite 为有效非负数字（Goose不依赖cacheWrite）；缺任何必要分量都不把缺值当0。完整snapshot、freshness及原始缓存路径保留该字段。不能用公开 health 或 NAS Release 判断生产端已产出字段；本轮未修改或部署 Hub/Agent，也没有真实私有 Hub 验证。

Android 在成员缺失调用/缓存遥测时比桌面的缺值转0更保守：整个分组对应读数不可用，避免把部分总和当完整指标。速度按上游的有计时样本口径计算；缺少任何一个 output/timedOutput/duration 时，该成员不贡献计时分子或分母。组与展开成员的未知显示 `—%` / `— tok/s`，不伪造0。

## 验证

新增 `SessionMetrics072Test` 覆盖加权聚合、逐成员cap、无计时输出、嵌套关系、continuation跨客户端关系、重复键、旧Hub缺字段、显式冷缓存、0分母、非法计数、Review/Grok、full projection和freshness。待主任务统一运行 `SessionMetrics072Test`、`SessionListTest`、`Session069Test`、`Provider071ContractTest` 及 `SessionMetrics072ScreenTest`（浅/深色组指标、展开成员、缺字段，截图输出到应用 external files 的 `sessions072-*.png`）并补UI/截图证据。本子任务首次定向Gradle在主任务同步通知后主动中断，尚无测试结论；不将未完成运行写成通过。

没有新接口、没有采集器移植，没有桌面专属排除项。账号、缓存、连接状态机未改；其回归及最终签名/版本/升级/Release由主任务统一验收。
