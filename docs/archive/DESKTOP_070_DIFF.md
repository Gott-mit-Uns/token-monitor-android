发布回执：0.2.62/71已发布；本表可移植客户端项已实现并完成Release门槛，源端依赖/平台不适用保持。详见GITHUB_PUBLICATION与Release VALIDATION；生产新providers及真机未验证不改为通过。

# Desktop v0.70.0 增量适配清单

审计日期：2026-10-14。本文记录实施前的源码差异和验收条件；分类中的“本轮可移植”不是测试通过声明。最终实施与发布状态以本轮 HANDOFF 和验证记录为准。

## 固定来源与边界

- Desktop v0.69.0：`3f106ae3d4712b26cced8272f0d488eaab0de1e6`。
- Desktop v0.70.0：`ac4eeb2c4aea239dd94b67f074c73f69aafc46f8`。
- 比较对象为上述两个提交的 `git diff`，共 116 个文件，4883 行新增、436 行删除；不以 main 作为版本来源。上游对象仓库为 `../token-monitor-upstream-070`；该目录没有 checkout，审计使用 Git 对象读取，没有修改它。
- Android 实施前基线：`7e85cb1`，正式版本 `0.2.61`。下述 Android 行号来自该基线，后续修改可能移动行号。
- Android 继续是 Kotlin + Jetpack Compose 的 Hub 查看客户端；不上手机采集器，不向手机复制供应商凭据，不修改生产 Hub/Agent/NAS。
- 上游源码永久链接使用 `https://github.com/Javis603/token-monitor/blob/ac4eeb2c4aea239dd94b67f074c73f69aafc46f8/<path>#L<line>`；下表路径和行号均指这个固定提交。

## 差异矩阵

| 增量 | 分类 | 固定源码依据 | Android 对应与实施边界 | 验收条件 |
| --- | --- | --- | --- | --- |
| Token 数字默认逐位滚动，保留 count | 已实现（0.2.62；原本轮可移植） | `b1fde18` / `4ab624b` / `9eb43a5`；`src/electron/tokenNumberAnimation.js:11–23,123–145,169,207–209,241–265`；`src/electron/main.js:574,2594` | `HeadlineNumberMotion.kt` / `HeadlineNumberMotionUi.kt` 已有数值递增、系统减少动效和生命周期收束。增加本地可持久化模式及原生滚动表现；不复制 DOM/WAAPI。 | 模式切换中断后落在准确目标；旧 count 保留；增减方向、位数变化与分隔符稳定；缺失值不变 0；减少动效与后台静态；总量帧不触发整页重组。 |
| 长列表中的数字动画预算 | 已实现（0.2.62；原本轮可移植） | `4ab624b` / `9eb43a5`；`src/electron/renderer/breakdownRenderPolicy.js:12–36`；`src/electron/renderer/app.js:1768–1793,1858–1909` | `BreakdownMotionUi.kt` / `SessionBreakdownMotionProjection.kt` / `SessionListUi.kt` 已有行数预算与分页。滚动仅应用适合的可见读数；保留既有紧凑布局和行身份。 | 长列表不可给所有历史行建立逐帧动画；离屏/后台停止；999→1,000 等位宽变化不跳动；大字体及紧凑主题不裁切。 |
| 周期切换动画降负载 | 已实现（0.2.62；原本轮可移植） | `9eb43a5`；`tokenNumberAnimation.js:169,182–193,261`；`app.js:1814–1823` | 上游 headline 仍逐位；breakdown 周期切换使用整读数两层，减少每字动画。Android 可按原生预算选择实现，不能声称物理曲线逐像素一致。 | 快速 Today/Month 切换不叠加无限层；统计值和最终周期一致；帧观察给出范围清晰的性能证据。 |
| T3 大历史标题读取优化 | 依赖 Hub／Agent | `7abdb33`；`src/shared/t3SessionMetadata.js:155–182` | Agent 将每批 IN 列表改为一条 `json_each(?)` 关系，减少重复 SQLite 扫描；手机不读取 T3 数据库。现有 `SessionMetadataParser` 仅接受 Hub 的 title。 | Agent 需部署该读取优化才改善源端标题时间；Android保留 title→projectLabel→sessionId 回退；不可宣称手机解决了源端标题扫描。 |
| 无父子/别名的会话跳过分组开销 | 已实现（0.2.62；原本轮可移植） | `7abdb33`；`src/electron/renderer/sessionRows.js:534–548` | `SessionRelationshipGroups.kt:19–69` 基线对所有独立会话仍建立 byKey/aliases/links。可增加独立会话路径；遇到父链接、重复 native/T3 身份及 Grok Bot 保留完整算法。 | 同一输入改动前后顺序、标题、成员、Token、费用、未定价总量相同；孤立父/跨客户端/冲突父/循环/别名继续正确。 |
| 近期列表先分组选择，再做展示投影 | 已实现（0.2.62；原本轮可移植） | `7abdb33`；`src/electron/renderer/edgeDock/presentation.js:277–364,377–392`，Home 复用该纯展示入口 | `SessionDisplay.kt:65–80` / `SessionListDisplay.kt:59–93` / Home 近期入口。减少对未选中历史行的模型名、格式、上下文等展示计算；身份和会计总量必须先完整分组。 | 用相同公开大数据比较结果与性能；上限外 running/waiting 保留；租约过期重新判断，不能缓存活动状态直到下一次推送；不新增后台计时器。 |
| 外观设置分组调整 | 已实现（0.2.62；原本轮可移植） | `a41c025`；`src/electron/renderer/index.html:390,494–496` | `VendorColorSettings.kt` 将 live dot/tool icons 放在厂商颜色设置下；title icon 在 Main 选项。可迁到通用图标/指示器分组，动画放外观设置；不改已有布尔值含义。 | 旧缓存、旧设置升级后保留选择；中英标签、触控范围、大字体折行和浅深色可用；字体/图表规范保留。 |
| CatPaw 工具标签及已有用量展示 | 已实现（0.2.62；原本轮可移植） | `6b5d729`；`src/shared/clientCatalog.js:81`；`src/shared/usage.js:306`；`docs/providers/catpaw.md:14–16,29–31,47–57` | `DesktopMarks.kt` / `DesktopVendorLabels.kt` / `ToolDisplay.kt` 添加已知工具身份。手机展示 Hub 已输出的 `client=catpaw`、sessions、models、Token、cost、unpriced；不生成模型映射和价格。 | CatPaw normal/unknown model、未定价、缺失 title、未知工具回退；统计只按 Hub 聚合；旧缓存仍可读；不能展示不存在的 CatPaw 额度提供商。 |
| CatPaw 本地采集、模型映射与历史重建 | 依赖 Hub／Agent | `6b5d729`；`src/shared/providers/catpaw/paths.js:8–11,61–87`；`collector.js` 源 fingerprint/重放分支；`docs/providers/catpaw.md:25–56` | `catpaw` 为 opt-in、forkOnly、默认不追踪；仅已验证 macOS/Windows assistant 来源，无 Linux/WSL 采集。需要对应 Tokscale pin 和 Agent 启用。title/project/context 源端不提供。 | 实际 Hub 输出须只读确认；未启用/缺源时不把缺失计成 0；不从数字模型 ID 猜 edition/模型/价格；本轮不修改生产追踪设置。 |
| MiniMax 按量余额 | 本轮可移植（实际来源仍依赖 Hub／Agent） | `18ce08e`；`src/shared/providers/minimax/limits.js:227–242,255–271,398–410`；`src/electron/renderer/limits/windowsView.js:1605–1620` | 源合同为 `kind=billing, metric=credits, label=Balance, remaining=available_amount, currency=CNY或USD`，账户标签 Pay-as-you-go。`LimitProviderLayout.kt:322` 基线通用分支只走 rate windows；需要显示 credits 金额。安全投影已有 remaining/currency。 | CN/CNY、EN/USD 分开显示；余额缺失/非法不生成 0；不能把 remaining 金额解释为百分比；旧 Token Plan 的 session+weekly 保留；未知币种明确回退；不得人民币/美元合计或转换。 |
| MiniMax API模式配置与查询 | 依赖 Hub／Agent | `18ce08e`；`src/shared/providers/minimax/account.js:27–36,54–65,96–101`；`limits.js:224–271,385–410` | `minimaxBillingMode=api` 查询按量余额；默认 token-plan；供应商 API key 保留在 Agent。手机只展示，不提供供应商凭据录入。 | 实际 Hub 余额输出/含义需只读证据；缺失 amount 时 upstream windows=[]、status=unavailable；手机呈现可用状态，不自造余额或选择计费模式。 |
| Codex 压缩及归档会话 | 依赖 Hub／Agent；显示字段已实现 | `136e87f`；`src/shared/providers/codex/transcript.js:7–47`；`src/shared/sessionFiles.js:64–105,108–125`；`sessionIdentity.js` / `sessionActivity.js` / `sessionContext.js` / `sessionDetail.js` | Agent 解 `.jsonl.zst` 多帧，输入32MiB/输出64MiB/4096帧预算；同会计 ID plain优先、live优先于archive。Android `SessionMetadataParser` 已接受 archived/deleted/sourceDeleted/nativeSessionId/parent/t3；`SessionDisplay.activity` 归档为idle。 | 新旧 Hub/归档 flags/缺失元数据可读；同 native identity 多 accounting rows 展示合组但各计一次；手机不读压缩文件、不按 archived 重算总量。 |
| Hermes 推理计费与公共输出修正 | 依赖 Hub／Agent；手机只显示来源 | `6d00083` / `136e87f`；`src/shared/history.js:94–109`；`scripts/verify-vendored-tokscale.js:69–95`；`scripts/vendor/tokscale.json` pin `fa5f0c13324838d1a988fda317597f9b90da7ad0` | 新 Tokscale 把原 output400/reasoning150 拆成 output250 + reasoning150；Token Monitor公共输出重新合成400，总量1650，费用仍1.25。旧 parser 不可只加公共normalizer，否则重算。手机使用 Hub output/total/cost，不重复加 reasoning。 | Fixture旧/新来源总量相同、output公共400、费用1.25；mobile不改费用、不推算缺失推理桶；Agent与公共normalizer须成对升级。 |
| DeepSeek Harness 模型来源修复 | 依赖 Hub／Agent；显示能力已实现 | `136e87f`；Tokscale fork `e5011807`；`scripts/verify-vendored-tokscale.js:54–61`；`tests/shared/tokscaleCompatibilityFixtures.test.js:9–28` | 仅精确（trim/不区分大小写）responseModel=keepalive哨兵回退到source/header模型。keepalive-v2是正常模型，sourceModel=keepalive也保留。Android `HubModelNames.kt:7–55` 显示上游模型/显式别名，不能按字符串猜来源。 | deepseek-reasoner、keepalive-v2、普通keepalive、未知ID均不被手机重写；模型归组不改Token/费用；明确Agent pin依赖。 |
| StepFun 过期额度身份续期 | 依赖 Hub／Agent | `478ec91`；`src/shared/providers/stepfun/limits.js:96–130,150–170`；`src/shared/providers/stepfun/refresh.js`；`main.js:864–883` | 401/403或登录拒绝后上游续期并仅重试一次，成功后CAS持久化；accountKey基于稳定refresh部分，避免每30min换身份。Android不拿Oasis token，不续供应商登录；Hub鉴权401/403仍禁止换入口绕过。 | 同accountKey额度更新保留本地选择；unauthorized展示清晰；失败保持上一完整缓存；不得把供应商续期逻辑当手机Hub鉴权回退授权。 |
| Cursor 手动长同步 | 依赖 Hub／Agent | `136e87f`；`src/shared/providers/cursor/auth.js:19–22`；`selfSync.js:28–34`；`collector.js:982–986`；`tests/shared/collectorCursorSyncBudget.test.js` | 上游显式sync预算1830000ms、后台150000ms；动预算在Tokscale，上游手动sync可更长。Android现有只读快照/SSE是另一合同，不扩大手机请求超时或开供应商同步。 | syncing/旧完整快照/freshness的显示仍正确；后台不保持SSE；一次有效连接；源端长同步不被手机猜成丢失或叠加多个快照。 |
| 会话SQLite归档流式读及原子发布 | 依赖 Hub／Agent | `5927070`；`src/shared/usage/sessionUsageArchiveStore.js:41–49,121–138,247–254,302–312` | 服务端 `.all()` 改 iterate cursor，finally释放cursor；catch-up先暂存，成功后替换，避免部分归档泄露。Android缓存架构无需SQLite归档迁移。 | 手机只接受完整快照；半截/失效响应不能污染已存缓存；不因标题优化改统计权威。 |
| Edge Dock 数字滚动/Windows taskbar排除 | 平台不适用 | `1c94a09` / `9ed188a`；`src/electron/renderer/edgeDock/railNumberRoll.js`；`src/electron/edgeDock/controller.js:345–356,537–548` | 原生Android无Edge Dock或Windows HWND。可借鉴数字动画语义；不添加桌面Dock/悬浮系统窗口。 | 构建权限不增加悬浮窗；保留已有Widget缓存刷新，不把Dock移植当本轮功能。 |
| Antigravity WSL SQLite提示 | 平台不适用 | `a4453d7`；renderer i18n WSL三方提示；`docs/wsl-sqlite-setup*.md` | Android不安装WSL/Node SQLite、不配置桌面扫描；现有设备状态仅展示Hub输出。 | 不增加手机WSL设置或日志目录入口；设备服务端诊断缺口列为依赖。 |
| Hub build registry/Worker副本和发布文档 | 本轮可移植（registry展示）／依赖Hub（运行代码） | `6d00083` / `ac4eeb2`；`src/shared/hubBuildRegistry.json`、`worker/src/shared/{history,usage,clientHealth,hubBuildRegistry}`；README、release模板、package版本 | Android `HubBuildInfo.kt` 可更新固定registry，使识别准确；Worker修复只在Hub运行。设置reference改0.70.0并说明部分对齐；不得用reference替代Android版本。 | 老Hub/新marker/未知build均正确；不把未知build认成健康或已对齐；交付文档列清仍依赖Agent/Hub的变更。 |

## 动画语义与原生适配合同

上游 `normalize()` 只有明确 `count` 保留旧模式，其他值归为 `roll`。每字650ms；live变化跨度150ms，period每em约32ms、wave封顶250ms，总生命周期900ms。相邻布局用250ms easeOut。未变前缀保留；变化后缀包含相同数字和分隔符；尾部M/%等单位从尾部定位保留。增加从下进入、向上离开，减少反向。快速retarget限制同位保留层数；metadata-only更新不重新开始同目标数字。

上游减载点包括可见数值上限40、离屏收束、period breakdown两层整体reading、保留headline逐位。Android应实现对应可感知语义和性能边界，使用已有Compose帧时钟和叶节点读状态，允许适合原生字体渲染的实现；不宣称与Web采样blur/scale物理通道逐像素一致。此前count初始/周期/中断语义仍须验证。

## 数据依赖和实际输出确认

CatPaw只有tool usage，没有CatPaw limits provider；assistant两个edition由Agent数据根判断，而不是手机模型数字判断。未定价的routing/unknown模型不自动替换成便宜模型，`rateMultiplier`不是token单价。源端无title/project/context应保持缺失，不推算。

MiniMax的`remaining`在credits窗口中是可花费金额。`available_amount`不是cash_balance；CN=CNY、国际=USD，互不合计。没有金额时upstream返回unavailable及空windows；客户端不能把它画成0余额或0%。按量模式与token-plan查询由Agent负责。

本审计只核验固定源码和Android基线，没有读取生产Hub私有响应，也没有验证生产部署是否包含上述变更。实际Hub字段/来源确认须在本轮另行以只读、脱敏、限字段方式记录；若生产没有CatPaw或MiniMax credits，允许加入可选合同的展示兼容，但发布说明必须写明实际来源尚未验证，不能声称已采集到生产余额。禁止为补来源修改生产配置或复制供应商密钥。

## 性能比较的最低证据要求

1. 同一固定公开fixture、设备/运行时、locale、timezone、clock和模式，比较基线与最终源码。
2. 在独立会话、native/T3重复身份、真实父子、缺失父、冲突/循环、Grok Bot、review和running/waiting超cap等分布下，先对完整投影及总量断言相等，再测时间。
3. 记录source输入条数、实际展示条数、完整分组条数、展示投影条数与次数；不能把提前截断会计输入造成更快计为优化。
4. 避免用时依赖绝对性能门槛冒充跨设备收益；分别记录pure projection和Android UI帧数据。JVM/Node测量不能替代实体手机的顺滑度或功耗验收。
5. 首次失败必须保留，测试修复不能删除断言/降低标准；最终报告区分通过、失败、模拟器、实体机和生产网络未验证。

## 全量diff归类覆盖

116个变化文件归入上述矩阵：renderer/app、数字模块、breakdown policy、sessionRows、settings HTML/CSS、i18n归移动UI；limits windows/presentation/account panels归MiniMax；catalog/source/collector/health/history/usage/vendor presentation/anchor/WSL归CatPaw与服务端合同；Codex各reader/sessionDetail/sessionFiles/sessionMetadata及DSH scanner归固定Tokscale兼容；StepFun/Cursor reader与accountSettings/main接线归服务端；session archive store归原子流式读；EdgeDock controller/render/CSS/HTML/presentation/rail模块归平台专属和共享纯投影；Worker生成副本/build markers归服务端；对应tests为各项回归依据；README、docs、release template、package manifests为文档及正式版本元数据。没有把构建包下载、生产部署或物理设备验收记作源码审计的结论。
