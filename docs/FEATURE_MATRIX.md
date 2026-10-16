2026-10-16 **Android0.72.0-01/code76已正式公开**：正式v0.72.0-01与latest兼容v0.2.67两Tag同PR #7源码3c4d2d6，最终APK db90保持；514单元、Lint0错误及受影响9＋2通过，17b7视觉78限定继承。公开Android查询／旧原解析器与真实官方64→75→匿名公开76升级／冷保留已验；两Release共8附件已匿名完整下载并核验，两APK字节相同，size／GitHub digest／SHA256SUMS／SOURCE全部匹配。完整回执见[GITHUB_PUBLICATION.md](GITHUB_PUBLICATION.md)及[联合交接最新节](HANDOFF_0.72.0-01.md)。NAS PR #11已合并并随NAS0.72.0-02发布，实际部署未验收；真机／真实Cursor／生产手机网络边界保持。以下为历史阶段记录。

2026-10-16 本轮固定桌面v0.72.0，Android0.72.0-01/code76联合候选：逐项实现、Hub依赖和桌面排除见[对齐清单](DESKTOP_072_ALIGNMENT.md)，实际验证/发布状态见[本轮交接](HANDOFF_0.72.0-01.md)。此前0.71设置与日历优化已包含，配套NAS0.72发行纳入协议核对；不代表生产部署或全平台完整对齐。

2026-10-15 **Android 0.2.64/73 已正式发布**：按用户明确要求不再提供 Android 桌面小组件或常规中的相关设置，旧组件功能矩阵与测试证据仅保留为历史。多设备同步采集设备等宽两列，字段、离线/未知语义不变；关于四入口与真实双协议端点改进保留。Tag `v0.2.64` / `a7d509903a46223af5c0ecb6f82937aee16108b3` 已在 GitHub main；四附件及公开下载核对见 [发布回执](GITHUB_PUBLICATION.md)，最终范围与验收见 [HANDOFF_0.2.64.md](HANDOFF_0.2.64.md)。客户端发布不补齐真机或生产字段证据。下列 0.2.63 Widget 实现声明不适用于当前版本。

## Fixed desktop v0.71.0 candidate increment

Goose/goose-db health, Notion rolling 6-hour and monthly non-monetary allowances with independent hashed accounts, and WorkBuddy identity/failure/display-total rules are adapted and tested. Existing Pi/CodeBuddy summaries preserve refresh state; remote chat Detail still has no Hub contract. Old Hub compatibility remains, without Android collection, third-party login or Widget restoration. See [increment and NAS requirements](DESKTOP_071_DIFF.md) and [acceptance handoff](HANDOFF_0.71.0_ANDROID_0.2.65.md). The matrix below is historical context.


2026-10-14 **Android 0.2.63/72本地统一候选**：实际 Mac WidgetKit 扩展经 REA JS/Apple/native 定向分析，原生 RemoteViews 支持摘要/仪表盘/活动/工具/模型及额度、三周期与每实例配置，旧 provider 保留。关于四入口响应式同组排列；HTTP/HTTPS 同屏，成功请求端点与上次连接区分；设备名/OS/运行版本/在线/同步均复用已有字段，未知不编造。A=客户端实现；Mac per-cell Intent、动画及 WidgetKit 调度属于平台差异；实际 Launcher/覆盖升级/小米边界分别见[统一交接](HANDOFF_0.2.63.md)和[对照清单](MAC_ANDROID_WIDGET_COMPARISON_063.md)。没有服务端协议扩展或 NAS 部署。

2026-10-14 **Android 0.2.62/71已正式发布**：固定桌面0.70 commit ac4eeb2；客户端roll/count、近期/标题投影/分组分页、外观高级选项、CatPaw/本币余额支持已通过Release验收（458单元、55Android、8组96窗口及签名升级）。Tag源码`aefe855c700cb7cac69ab43dac4df64e8e8ff677`。已支持的客户端字段不证明生产已升级，core78/runtime5无新providers观察与全部依赖/未验证范围保留。

# Android 0.2.62：桌面 v0.70.0 增量适配

2026-10-14。本轮固定0.69→0.70源码差异与逐项验收见 [DESKTOP_070_DIFF.md](DESKTOP_070_DIFF.md)，当前实现、首失败和设备边界见 [HANDOFF_0.2.62.md](HANDOFF_0.2.62.md)。数字roll/count、外观高级选项、近期选择/列表CPU优化、CatPaw通用数据及MiniMax本币余额客户端已实现；数据生成/计费/额度刷新继续依赖Hub/Agent。DXP4800真实样本core78/runtime5且无新provider，不宣称生产新字段或全量对齐。此前矩阵作为历史记录保留。

# 0.2.51 与桌面 v0.69.0 功能差异

2026-10-13 / 0.2.61 手机增量：设置开关补齐180ms桌面视觉过渡；Android自身更新入口与Hub构建状态独立；缓存额度Widget和日图长按拖动为手机增强，不扩展采集职责。Dashboard日柱图、Records日柱/折线接入；多日K线/二维热图/导航预览/月记录维持既有交互。用户追加紧凑普通设置下拉及已内置字体菜单，同版验收发布。候选检查与未执行真机边界见[联合交接](HANDOFF_0.2.61.md)，最终交付以发布回执为准；下文历史矩阵不代表新增功能均有真机证据。

2026-10-13 V5当前部署增量：Mac实际安装桌面0.69.0；DXP4800与Lucky公开构建及鉴权stats均为core78/runtime5、4设备，已只读取得真实history/shared数据。真实样本观察到waitingForInput/nativeSessionId/parentSessionId；liveActivity/t3ThreadId在该样本未出现，不能据此推断不支持。详细字段、Android解析及网络验收见HANDOFF_V5_NETWORK.md及HUB_API_CONTRACT.md。下文core74是2026-10-12历史快照，不代表本轮当前部署；未观察的生产事件仍保留E。

0.2.56统一增量：标准字号精确Token与缩写适配、选择菜单间距、已有health公开构建信息及缓存展示、来源许可更正已整合；继承0.2.55混合排序。构建状态不是软件SemVer或生产字段验收，仍保留本表部署E及真机边界。验证/来源见HANDOFF_0.2.56.md及两项专项交接。

0.2.55增量：Auto Review摘要参与普通会话、关系/Grok摘要的统一混合排序；固定桌面0.69排序goldens、Release会话专项及正式签名模拟器升级通过。见HANDOFF_0.2.55.md；生产字段、真机与其他功能边界仍按各自证据。

0.2.54增量：P1-10主页精确总量与近似值挤压、TOTAL裁切和百分比拆行已修复，保留字号、自动分行/增高，真实系统配置0.9/2.0浅深及375dp窄屏合成验证通过。新版真机待复核。P1-09两阶段测试/工具完整整合，统计/Hub合同/SSE产品行为未变，详见HANDOFF_0.2.54.md。

审计日期2026-10-12。A=Android已支持；B=Android缺失但Hub已有所需数据；C=Android缺失且当前Hub不提供；D=仅桌面无需移植；E=证据不足。一个发布功能可拆为不同 wire/本地部分；不把目标Hub支持等同当前部署已有。

上游固定 [v0.68.0…v0.69.0](https://github.com/Javis603/token-monitor/compare/v0.68.0...v0.69.0)，16提交；目标 `3f106ae3d4712b26cced8272f0d488eaab0de1e6`。下表上游路径基于该提交，Android路径基于当前源码；定向 diff 在 `work/audit-069-source/relevant.diff`。

**2026-10-12历史入口边界：**Lucky公开health报告 coreRevision74/buildId匹配v0.68.0注册表，目标v0.69.0核心为78。v0.68 normalizer没有liveActivity/waitingForInput/nativeSessionId/parentSessionId/t3ThreadId；因此若当前服务确为注册表对应代码，它会丢弃这些新字段。未读鉴权stats、未核验部署源码（NAS SSH拒绝连接），这些项当前分类为E，并记录“可能C”；不能据health确认实际字段。B仅在已有v0.68字段足够时使用，或明确注明“目标v0.69 Hub能力”。

| 项目/拆分 | 分类与当前状态 | 实际证据 | 影响/优先级/下一步 |
|---|---|---|---|
| 新显示字段 liveActivity、waitingForInput | A客户端可选消费；生产部署E | 固定上游12状态参考；SessionDisplay支持30秒租约和Codex输入等待 | P1生产联调；批准原因无可靠字段，不推断 |
| nativeSessionId、parentSessionId、parentClient、t3ThreadId | A客户端可选消费；生产部署E | SessionMetadataParser保留受限可选字段；12分组参考 | P1生产联调；账目key只加一次，不改Hub总量 |
| 等待回复/批准（有用量会话） | A回复/通用等待；批准原因E | 上游 `sessionLive.js:36-118`：liveActivity 30秒租约，unknown/未来/归档处理；Codex waitingForInput 有10分钟边界，结束优先 | P1，状态不能靠最近Token或turnEnded猜成批准等待；旧Hub缺字段保持旧行为 |
| 尚无Token的本机会话、桌面局部实时活动 | D桌面采集；若要求远程同等展示则C（包括目标Hub） | `docs/API.md:358` 明确zero-token rows/local overlays不上传；`syncDisplayStats.js:18-45` 本地native覆盖 | 不移植采集；远程可达范围需产品确认/Hub协议另立任务 |
| Codex子代理/续接分组 | A客户端关系分组；部署E | `sessionRows.js` 新关系树；`tests/shared/sessionRelationshipWire.test.js` wire契约；Android `SessionListDisplay.grouped:77` | P1，父缺失、循环、跨client、重复rollout、人工fork、分页/明细/总量各作定向验收 |
| T3 Code跨provider续接与委派关系 | A客户端展示；部署E | `t3SessionMetadata.js`、`usage.js:165-180`、renderer关系分组；t3ThreadId不替换记账key | P1，先相同App身份，再parent，人工fork独立；冲突值不能强行合并 |
| Cursor Grok Bot基本分组 | A基本分组 | SessionRelationshipGroups按sand-subagent前缀或正数grok-bot模型分组 | 历史增强仍受限，不读取本地文件 |
| Grok Bot跨历史补充身份 | C远程缺数据/桌面部分D | `syncDisplayStats.js:24-30` grokBotSessionIds是Electron-only历史overlay；`usage.js` 不携带该字段 | 不能声称Android完整复制历史识别；需要Hub明示能力再补远程增强 |
| Antigravity标题/重命名显示 | A展示读取，E当前生产端标题/策略 | Android `HubData.kt:50`、`SessionMetadataParser` 已读取受限title；上游 `providers/antigravity/sessionMetadata.js` SQLite摘要+撤销/重命名watch | P1仅联调标题启用、重命名、撤销；桌面读取SQLite属于D，Android不实现 |
| Antigravity原生生成速度 | A通用counter消费；E当前生产端时序数据 | 上游vendor pin增加原生duration；Android `LiveTokenRate.kt` 连续设备counter差分，`SessionListDisplay.rate` session duration比值 | P1确认agent产出timedTokens/timedOutputTokens/timedDurationMs及capability；缺数据显示无读数 |
| Antigravity推理Token纳入总量/历史 | A消费Hub权威total；生产端计算D，当前修正是否生效E | 上游 `history.js` 将antigravity加入disjoint reasoning集合；vendor更新；Android按Hub totalTokens读取 | 不在Android重复加reasoning；P1验证生产端升级后总量/历史/components一致 |
| Antigravity单可见模型组同时显示5-hour/Weekly | A，第二阶段已实现；真机仍未验证 | 上游 `limits/providerPresentation.js:271` 单组两周期分支；Android `LimitDisplay.compactWindows` 分别选session/weekly，HomeQuotaLayout标签含模型与周期 | 15组输入的定向逻辑证据、Compose矩阵和边界见 [第二阶段交接](HANDOFF_ANTIGRAVITY_DUAL_WINDOWS.md)；现有Hub数据足够，不需Hub升级 |
| Cursor turn结束指示 | A通用turnEnded消费；生产端及时检测D，当前数据E | Android `HubData.kt:54`三态、`SessionDisplay.activity`；上游Cursor metadata新增完成检测 | 定向fixture确保显式true/false/缺失；不重新实现采集 |
| 设备名称统一 | A共享名称resolver | Home/设备列表、详情、rate使用DeviceDisplay.name：本地别名→Hub displayName→id→hostname；空白与内部占位值不显示 | P2真机核对各入口一致；相关单元与UI回归见本轮交接 |
| 导航Lucide图标 | A静态资源已对齐，不依赖Hub | 上游4129b5e修改model/session/status/tool/trends SVG；Android五项资源按固定v0.69源码更新 | P2仅资源/许可/尺寸更新；保留八入口及48dp触摸验收 |
| Review/关系/Grok/普通会话混合排序 | A，0.2.55已对齐 | 上游groupSessionRows最后统一按时间、Token、费用、名称比较；固定源blob核对及10组goldens通过，36 Release单元/15 UI专项 | P2-04完成；只改最终排序，不改成员/聚合/统计或远端字段；真机未验证 |
| Hub API/SSE协议 | A旧事件消费；客户端支持可选字段；部署仍E | diff未修改 `src/hub/server.js`；shared usage及Worker副本变更；事件仍snapshot/stats/freshness，stream v2 | P1验证生产产出，不能称“服务文件未改所以协议无变化” |
| Home history重试与revision失效读取 | A客户端策略及当前NAS只读验证；旧r10归因E | 新校验器按周期检查4次预算/4000ms等待，记录脱敏目标变化、请求终态和桥接开始/结束；12校验器+7桥接+8Android定向通过，两轮75秒真实NAS观察通过。短间隔新revision由受控页面/逻辑fixture验证 | P1-09验证工具修复完成；真实观察没有复现1.685秒，旧运行无身份不能追溯，见 [P1-09交接](HANDOFF_P1_09_HISTORY_DIAGNOSIS.md)。不扩大为真机、公网或长期性能验收 |
| Cursor活动驱动云端用量刷新 | D生产端 | 上游collector/source watch、自刷新节流；Android只看Hub | 不移植，不启动后台轮询 |
| Windows Edge Dock恢复置顶、Electron43.7.9、headless安装文档 | D | 050f983、ad3f9aa、8891608 | 无Android开发任务 |

每项当前部署E不是实现任务许可。后续先解决证据缺口；如确认字段被当前Hub丢弃，改记C并将Hub/Agent升级放入独立授权范围。Android不得补本地采集。

## V4核查入口

用户授权按现有可选v0.69合同补客户端消费，生产字段存在性仍为E；客户端fixture支持不等于已部署。等待批准原因、零Token local overlay、Grok跨历史身份和Windows Dock受限，详见HANDOFF_DESKTOP_069_UI.md阶段A。

V4实现与实测见 [本轮交接](HANDOFF_DESKTOP_069_UI.md)。A仅表示客户端行为，不证明生产Hub上传新字段。历史auto-review末尾差异已在0.2.55按固定0.69完整混合排序修复，见HANDOFF_0.2.55.md。
