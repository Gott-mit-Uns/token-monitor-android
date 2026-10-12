# 0.2.51 与桌面 v0.69.0 功能差异

审计日期2026-10-12。A=Android已支持；B=Android缺失但Hub已有所需数据；C=Android缺失且当前Hub不提供；D=仅桌面无需移植；E=证据不足。一个发布功能可拆为不同 wire/本地部分；不把目标Hub支持等同当前部署已有。

上游固定 [v0.68.0…v0.69.0](https://github.com/Javis603/token-monitor/compare/v0.68.0...v0.69.0)，16提交；目标 `3f106ae3d4712b26cced8272f0d488eaab0de1e6`。下表上游路径基于该提交，Android路径基于当前源码；定向 diff 在 `work/audit-069-source/relevant.diff`。

**当前入口边界：**Lucky公开health报告 coreRevision74/buildId匹配v0.68.0注册表，目标v0.69.0核心为78。v0.68 normalizer没有liveActivity/waitingForInput/nativeSessionId/parentSessionId/t3ThreadId；因此若当前服务确为注册表对应代码，它会丢弃这些新字段。未读鉴权stats、未核验部署源码（NAS SSH拒绝连接），这些项当前分类为E，并记录“可能C”；不能据health确认实际字段。B仅在已有v0.68字段足够时使用，或明确注明“目标v0.69 Hub能力”。

| 项目/拆分 | 分类与当前状态 | 实际证据 | 影响/优先级/下一步 |
|---|---|---|---|
| 新显示字段 liveActivity、waitingForInput | E当前/目标B；Android缺失 | 上游 `docs/API.md:358`、`usage.js:703,822`；Android `SessionDisplay.kt:17,67` 无字段且仅运行/结束/闲置 | P1，鉴权样本仅取字段存在与状态统计，再补可选模型和状态投影 |
| nativeSessionId、parentSessionId、parentClient、t3ThreadId | E当前/目标B；Android缺失 | 上游 `usage.js:142-181,837` normalize/merge；Android `SessionMetadataParser` 未保留 | P1，目标Hub/生产端协同确认，按账目key只加一次，禁止改Hub总量 |
| 等待回复/批准（有用量会话） | E当前/目标B | 上游 `sessionLive.js:36-118`：liveActivity 30秒租约，unknown/未来/归档处理；Codex waitingForInput 有10分钟边界，结束优先 | P1，状态不能靠最近Token或turnEnded猜成批准等待；旧Hub缺字段保持旧行为 |
| 尚无Token的本机会话、桌面局部实时活动 | D桌面采集；若要求远程同等展示则C（包括目标Hub） | `docs/API.md:358` 明确zero-token rows/local overlays不上传；`syncDisplayStats.js:18-45` 本地native覆盖 | 不移植采集；远程可达范围需产品确认/Hub协议另立任务 |
| Codex子代理/续接分组 | E当前/目标B；Android仅支持旧auto-review一组 | `sessionRows.js` 新关系树；`tests/shared/sessionRelationshipWire.test.js` wire契约；Android `SessionListDisplay.grouped:77` | P1，父缺失、循环、跨client、重复rollout、人工fork、分页/明细/总量各作定向验收 |
| T3 Code跨provider续接与委派关系 | E当前/目标B | `t3SessionMetadata.js`、`usage.js:165-180`、renderer关系分组；t3ThreadId不替换记账key | P1，先相同App身份，再parent，人工fork独立；冲突值不能强行合并 |
| Cursor Grok Bot基本分组 | B（已有sessionId/models）；Android缺失 | `sessionRows.js:265` 利用sand-subagent前缀或正数grok-bot模型；Android无此分组 | P1，可用现有Hub模型字段纯展示，不依赖本地文件 |
| Grok Bot跨历史补充身份 | C远程缺数据/桌面部分D | `syncDisplayStats.js:24-30` grokBotSessionIds是Electron-only历史overlay；`usage.js` 不携带该字段 | 不能声称Android完整复制历史识别；需要Hub明示能力再补远程增强 |
| Antigravity标题/重命名显示 | A展示读取，E当前生产端标题/策略 | Android `HubData.kt:50`、`SessionMetadataParser` 已读取受限title；上游 `providers/antigravity/sessionMetadata.js` SQLite摘要+撤销/重命名watch | P1仅联调标题启用、重命名、撤销；桌面读取SQLite属于D，Android不实现 |
| Antigravity原生生成速度 | A通用counter消费；E当前生产端时序数据 | 上游vendor pin增加原生duration；Android `LiveTokenRate.kt` 连续设备counter差分，`SessionListDisplay.rate` session duration比值 | P1确认agent产出timedTokens/timedOutputTokens/timedDurationMs及capability；缺数据显示无读数 |
| Antigravity推理Token纳入总量/历史 | A消费Hub权威total；生产端计算D，当前修正是否生效E | 上游 `history.js` 将antigravity加入disjoint reasoning集合；vendor更新；Android按Hub totalTokens读取 | 不在Android重复加reasoning；P1验证生产端升级后总量/历史/components一致 |
| Antigravity单可见模型组同时显示5-hour/Weekly | A，第二阶段已实现；真机仍未验证 | 上游 `limits/providerPresentation.js:271` 单组两周期分支；Android `LimitDisplay.compactWindows` 分别选session/weekly，HomeQuotaLayout标签含模型与周期 | 15组输入的定向逻辑证据、Compose矩阵和边界见 [第二阶段交接](HANDOFF_ANTIGRAVITY_DUAL_WINDOWS.md)；现有Hub数据足够，不需Hub升级 |
| Cursor turn结束指示 | A通用turnEnded消费；生产端及时检测D，当前数据E | Android `HubData.kt:54`三态、`SessionDisplay.activity`；上游Cursor metadata新增完成检测 | 定向fixture确保显式true/false/缺失；不重新实现采集 |
| 设备名称统一 | B部分缺失 | 上游 `deviceBreakdown.js:10` displayName→deviceId→hostname→device；Android项目设备行已有类似顺序，但 `LiveTokenRate.kt:92` 仍用hostname，`DeviceDetails.kt` 也走name | P2统一只读展示resolver，明确本地别名优先级和空白处理 |
| 导航Lucide图标 | B静态UI差异，不依赖Hub | 上游4129b5e修改model/session/status/tool/trends SVG；Android资源由旧v0.68导入 | P2仅资源/许可/尺寸更新；保留八入口及48dp触摸验收 |
| Hub API/SSE协议 | A旧事件消费；新可选字段缺失为上述E/目标B | diff未修改 `src/hub/server.js`；shared usage及Worker副本变更；事件仍snapshot/stats/freshness，stream v2 | P1增加字段解析与缓存恢复，不能称“服务文件未改所以协议无变化” |
| Cursor活动驱动云端用量刷新 | D生产端 | 上游collector/source watch、自刷新节流；Android只看Hub | 不移植，不启动后台轮询 |
| Windows Edge Dock恢复置顶、Electron43.7.9、headless安装文档 | D | 050f983、ad3f9aa、8891608 | 无Android开发任务 |

每项当前部署E不是实现任务许可。后续先解决证据缺口；如确认字段被当前Hub丢弃，改记C并将Hub/Agent升级放入独立授权范围。Android不得补本地采集。

## V4核查入口

用户授权按现有可选v0.69合同补客户端消费，生产字段存在性仍为E；客户端fixture支持不等于已部署。等待批准原因、零Token local overlay、Grok跨历史身份和Windows Dock受限，详见HANDOFF_DESKTOP_069_UI.md阶段A。
