# 趋势主窗口复刻计划

状态：**43基线评审，44已实施并完成公开验证**。本文件保留2026-10-11在43阶段的只读画面、源码评审与迁移方案；该阶段只新增计划。44已恢复主窗口并移动历史入口，实施与最终验证见[TRENDS_MAIN_PARITY.md](../../TRENDS_MAIN_PARITY.md)。以下“当前”及源码行号保留原43基线，不作为44仍存在差异的断言。

目标参照固定桌面端 Token Monitor v0.68.0，源码位于 `../work/token-monitor-reference/desktop-v0.68.0`，固定提交为 `5d2db368d8313415763860d594de00e46a663418`。安卓端继续作为 Hub 数据查看端；这一轮工作恢复桌面主窗口的趋势信息层级，保留已有详细历史查看能力。

## 已实际查看的画面与比较边界

已用图像查看工具检查：

- `evidence/theme-history-actions/source/desktop-full-trends-zh-CN.png`：固定原始桌面渲染器的中文主窗口趋势页，DAY 选中，显示总量与费用、近 7 天单色柱形预览、日期轴和四张统计卡片。
- `evidence/button-colors/final/visuals/desktop/dark/desktop-trends-dark.png`：上一包安卓深色趋势页，DAY 选中，历史详情独立选中 30 天，显示历史用量标题、范围筛选、工具/模型、图表模式、堆叠图与图例。

两张图使用不同的公开测试数据，源图的实时状态与安卓图的离线状态不同，窗口尺寸和手机系统留白也不同。不能据总量、柱高、工具数量、图表颜色或绝对像素位置判断数据错误。安卓截图只有主页面顶部可见范围，不能凭这张图宣称其下方历史摘要、活动图和日/月列表缺失。主窗口与历史详情的入口层级、headline 是否存在，以及单色预览和详情控件的区别，可以结合当前源码确认。

上一包截图有“读取活动与趋势”，但当前 `DesktopHubScreen.kt:430` 已将 `ChartLoad` 限制为历史缺失或存在错误时显示。它不再作为本轮未修复的问题。新一轮截图生成后应补充实际画面复核；以下三个结构结论不依赖该旧按钮。

## 三个具体差异与实施动作

### 1. 趋势主窗口缺少共用的总量与费用区域

原主窗口 `index.html:1555` 的 `total-panel` 包含 TOTAL TOKENS、完整计数、可选紧凑数值和费用。`app.js:6677` 切换到 Trends 时只切换下方内容面板，继续保留共用 headline；计数与费用仍来自当前 DAY/MONTH/TOTAL 或固定周期选择。固定周期未就绪时，`app.js:6613` 显示“—”和不可用提示，并在绘制趋势内容前返回。

安卓当前 `DesktopHubScreen.kt:282` 的 `hasHeadline` 明确排除 `HubPage.TRENDS`，而 `:150` 的 headline motion 可见判定也排除 Trends。现有 `DesktopTotal`（`:492`）已提供计数、费用、紧凑数和 Token 组成明细，可直接复用。

下一轮在**主窗口趋势概览**恢复 `DesktopTotal`，同时核对 headline 状态可见性，使桌面已有的数字变化行为自然覆盖 Trends。用户已经撤销此前额外提出的主页动效要求；此处只沿用桌面共用 headline 的语义与现有实现，不增加新的特殊动效。详细历史界面是否显示此区域应按独立详情入口处理，不把历史范围的“已知用量”混入当前 period headline。

### 2. 主窗口被独立历史分析界面替代，图形与周期也随之改变

桌面主窗口的祖先是 `app.js:4959` 的 `renderTrends`，其内容为范围说明和 ↗、单色 spark 柱形预览、首尾日期轴及四卡片。点击图表或 ↗ 调用 `openDashboard`（`app.js:12809`），再打开独立历史仪表盘。详细仪表盘的工具/模型、Bars/K-line 和历史范围控件来自 `dashboard.html:26`、`dashboard.js:42` 与 `:358`，并非主窗口 Trends 的控件。

安卓当前 `DesktopHubScreen.kt:430` 直接放入 `HistoryTrend`；后者在 `HistoryTrend.kt:20` 独立默认选择 30 天，并在 `:26` 起显示历史标题、范围筛选及详细图表。这解释了画面中大量筛选按钮占据主窗口、图表变成工具堆叠图、顶部 DAY 与历史 30 天同时存在的现象。

下一轮新增独立的主窗口趋势投影和预览组件，忠实移植 `usageCharts.selectPreviewSeries`（`:386`）、`patchTodayBar`（`:402`）、`sparklinePreview`（`:311`）及 `sparklineSvg`（`:486`）的几何和呈现规则。顶部现有 period 是概览的唯一周期选择；详情自己的历史范围只属于详情。主窗口 spark 不显示工具/模型堆叠、K-line、图例或历史覆盖说明。

| 当前 period | 主窗口 spark 数据 | 范围说明 | 四卡片的周期行为 |
| --- | --- | --- | --- |
| `today` / DAY | 先取 preview daily 尾部，再按本地今日构造连续 7 个日历日；缺少的日使用 source 的零柱规则，今日替换为实时 today total | 近 7 天 | 活跃时间与峰值单日只取今日；活跃天数与连续天数仍取 lifetime summary |
| `month` / MONTH | 按 preview daily 最后一条日期所属月份筛选；不能直接改成手机当前月或滚动 30 天 | 本月每日 | 活跃时间与峰值单日取本地当前月 daily；活跃天数与连续天数仍取 lifetime summary |
| `allTime` / TOTAL | 直接使用 preview monthly | 历年每月 | 四项均使用 preview lifetime summary |
| `week` / `last7` / `last30`，固定周期 ready | 仍使用 preview monthly；原源码专门保留长周期趋势上下文，不缩成所选固定周期的几根日柱 | 历年每月 | 活跃天数与连续天数取 preview lifetime summary；活跃时间与峰值单日取 ready fixed snapshot summary |
| 固定周期 loading / unavailable | 先显示 headline “—”和当前不可用说明；不继续绘制看似完整的 spark 与四卡片 | 不渲染有效范围预览 | 不从缺失 fixed 数据构造零值或回退为另一个周期的有效卡片 |

表中的范围说明使用源中文文案（`i18n.js:4253`）：`trends.range.year` 对应“历年每月”，英文为“By month”，不能因 key 名包含 year 而改成“近一年”。`selectPreviewSeries` 本身直接返回 Hub preview monthly，没有在客户端再截取 12 条。纯函数移植要保留这一点。DAY 的填充规则也只用于桌面主窗口预览；详细历史仍保留已记录、未知和日期缺口的区分。原实现的空数据边界是：preview 没有点且实时 today total 为 0 时为空；实时今日非 0 时可由 `patchTodayBar` 构造 7 日预览。

绘图按 source 的 300×120 坐标投影、`gap = 0.3`、相同最大值缩放；以 `LocalDesktopChartColor` 作为单一图色，普通柱透明度 0.5、最后一柱 1.0，DAY 零值标记透明度分别 0.24/0.32。日期轴只放首尾日期（`styles.css:6571`），不混入详情的整日期和 Y 轴。预览主体占可用空间，四卡片在下方；不把原桌面截图的绝对高度硬编码为手机高度。

现有 `DesktopTrendPreview`（`DesktopCharts.kt:126`）是首页 45 日 area line，`HomeCharts.project`（`HomeCharts.kt:49`）也选择尾部 45 日；`DesktopHistoryChart` 是历史详情的堆叠/蜡烛/折线图。它们可共享格式、主题或命中辅助，但都不能直接当作这个单色 spark 的完整替代。

### 3. 四张卡片的字段来源、排列和文字层级不一致

桌面 `app.js:4981` 调用 `homeOverview.activityStatsForPeriod`（`homeOverview.js:352`），最终只展示以下四项，按行排列为“活跃天数 / 连续天数”和“活跃时间 / 峰值单日”。桌面先显示数值，再显示标题；数值为 16px、600、tabular numerals，标题为 10px muted，卡片圆角 8px、内边距 8px/10px、间距 8px、轻量 overlay 背景（`styles.css:6572`）。主窗口范围说明约 11px，首尾轴约 10px。安卓使用现有 desktop 字体/缩放语义实现这些层级，不继续以 Material 大标题和多个 FilterChip 作为主窗口骨架。

| 四卡字段 | `today` / `month` | `allTime` | fixed ready |
| --- | --- | --- | --- |
| activeDays | preview summary.activeDays | preview summary.activeDays | preview summary.activeDays |
| currentStreak | preview summary.currentStreak | preview summary.currentStreak | preview summary.currentStreak |
| activeTimeMs | 按今日或当前月筛选 daily 后求和 | preview summary.activeTimeMs | fixed snapshot.summary.activeTimeMs |
| peakDayTokens | 按今日或当前月筛选 daily 后取最大 | preview summary.peakDayTokens | fixed snapshot.summary.peakDayTokens |

当前 `FixedPeriodActivitySummary`（`FixedPeriodStatus.kt:31`）四项全部来自 `fixed.activity`，并按“标题左、数值右”的四行显示。它会把活跃天数和连续天数也收窄到 fixed range，不能直接复用为桌面四卡。当前 `DesktopHistorySummary`（`:18`）是八项 lifetime 概览，标题在上、数值在下；它应继续保留在详细历史中，不当作主窗口四卡。

时间格式移植 `app.js:1380`：毫秒先四舍五入为分钟，小时为 0 时显示 `Nm`，完全为 0 显示 `0m`；不能简单以现有 `0h 0m` 和向下取整格式替代。峰值单日沿用源 `formatCompact` 与现有 `DesktopCompactTokens` 的 locale/单位偏好，活跃天数与连续天数使用本地整数格式。

## 下一轮具体迁移顺序

1. 新增纯数据投影，例如 `TrendsMainProjection`：输入为 `Snapshot.historyPreview`、当前 period、ready fixed 视图、实时 today period 和可注入的本地日期；输出选中序列、轴标签、范围标签、四项数值与空/不可用状态。主窗口保持源 stats preview 的数据来源；不会为“复用数据”直接换用详细历史的 30 天摘要。
2. 新增主窗口概览组件，例如 `DesktopTrendsMain`：绘制 source 单色 spark 与四卡，并提供图表及 ↗ 两个可触控、可访问的详情入口。复用现有主题、desktop 字体、compact formatting、currency 与 `DesktopTotal`。不要改首页 `DesktopTrendPreview` 的 45 日含义。
3. 在 `DesktopHubScreen` 内将 Trends 分为“概览 / 详细历史”局部导航状态。默认进入概览；点击 spark/↗ 打开详细历史；系统返回先退出子明细，再从详细历史回概览，再按已有主页面返回规则离开 Trends。历史详情不新增为可见视图菜单中的并列主视图，也不改用户已保存的主视图顺序。
4. 把现有 `HistoryTrend` 放到独立详情区域，保留它的选择状态和滚动位置。详细历史可以按原 Dashboard 的“概览 / 趋势”分层调整，但第一步只迁移入口，不删除已实现的有效查看能力。详情范围和主窗口顶部 period 互不覆写；离开详情后 headline 与主窗口 period 仍保持原选择。
5. 核对固定周期未就绪提示、缺失历史读取入口、刷新和已有串行读取策略。主窗口预览有数据时先显示预览；详情确需完整历史时沿用当前 `HubViewModel.loadHistory`、`rememberHomeHistoryReadPolicy` 和缓存/修订规则。进入详情、返回、展开某一天不能各自隐式重复请求；刷新仍是用户明确发起的查看动作。

需要保留并移动到详情的现有功能：

- 7/30/90/365/全部历史范围，以及工具/模型归因、Bars/K-line/Line、图例和图表命中明细（`HistoryTrend.kt:20`、`DesktopHistoryChart.kt:34`）。
- 已知用量、已知费用、未定价 Token、记录天数/日历天数/已知天数及日期缺口说明（`HistoryTrend.kt:32`）。
- 八项 lifetime 活动摘要（`DesktopHistorySummary`），Token/费用活动视图、日历展开和选择某日（`HistoryTrend.kt:44`）。
- 日/月记录切换、列表分页，以及选中记录的费用、Token、工具/模型明细（`HistoryTrend.kt:56` 起）。

其中前三组可复用现有 `HubHistory`、`HistoryRow`、`HistorySummary`、`HistoryRanges`、`DesktopHistoryChart`、`DesktopHistorySummary`、`DesktopActivityPreview` 与 `HistoryActivity`。主窗口四卡可复用整数、compact 和 duration 的格式能力，但不复用 fixed 四行组件的字段来源。现有 `FixedPeriods.evaluate` 继续计算 fixed 总量、活动时间和峰值，不把设备数据重新合并一遍。

## 新增验证与验收边界

纯函数对照测试应运行固定原始 JS 模块作为参考，而不是把 Kotlin helper 的公式再抄成测试期望。公开 fixture 与参考结果可保存独立的新证据；记录固定提交、原模块 SHA-256 与调用参数，不改已有证据。

- 对照 `selectPreviewSeries`：DAY、MONTH、TOTAL，跨月 preview 的最后日期与手机当前月不同，monthly 不擅自截断，缺少 daily/monthly，空序列。
- 对照 `patchTodayBar`：7 个连续本地日期、稀疏记录、当前日存在/不存在、今日实时更新、跨月/跨年、空历史+零实时、空历史+非零实时；注入本地今日以保证复查。
- 对照 `sparklinePreview`/零标记：0、单柱、多柱、最大值缩放、gap=0.3、最后一柱、数值与坐标边界。未知值在主窗口的 source 行为与详细历史的未知说明应分别验证。
- 对照 `activityStatsForPeriod`：lifetime activeDays/currentStreak 与 fixed 活动值刻意不同；today/month 只收窄 activeTime/peak；TOTAL 全四项 lifetime；fixed ready 使用正确字段；loading/unavailable 不展示有效概览。
- 对照 `formatActiveDuration`：0、低于半分钟、半分钟边界、跨小时舍入；峰值格式测试结合已有 compact 单位、语言与字体偏好。

真实 `HubScreen` 的 Compose 页面验证必须使用公开可复查 fixture 和计数型 `HubActions`，不只测新组件截图：

- 默认进入 Trends 概览即可看见当前 period 的 headline/费用、范围说明、单色 spark 和四卡；不会先落入 30 天详细历史。DAY/MONTH/TOTAL 与三个固定周期按上表变化，fixed 未就绪提示不与有效四卡同时出现。
- 对 headline、范围、卡片字段和可见图表类型做真实语义/文本断言；固定周期 fixture 要包含不同的 lifetime 与 fixed activeDays/currentStreak，以抓住错误字段复用。
- 分别点击图表和 ↗ 打开详细历史；检查范围、工具/模型、模式切换、某天图表明细、日历明细、日/月列表和分页仍可使用。返回先关闭明细，再回概览；原主窗口 period、详情筛选与滚动状态恢复正确。
- 验证刷新调用目标与次数，读取中按钮状态、失败后重试、缺失历史入口、连接切换及后台/恢复行为；只读导航和展开明细不得新增网络写入或无界重复读取。测试记录实际 action 次数，不将“零网络读取”当作用户点击刷新后的预期。
- 对同一公开 fixture、相同语言和相同选中 period 捕获深色/浅色概览，以及详情打开、明细和返回状态。核对卡片值在上/标题在下、图色/透明度、紧凑数、长数适配、系统留白和触控入口。桌面与安卓数据、尺寸或展开态不同的图必须明确标注，不能以不同柱高判断复刻失败或成功。

这轮计划只覆盖趋势主窗口与详细历史的迁移。验收通过之前，不宣称 43 的趋势整页已一致，也不把模拟器页面验证写成手机体验或帧率已经改善。

## 当前 43 实际画面确认

2026-10-11 已重新用图像查看工具检查固定 source 原图，以及本轮新生成的以下文件：

- `evidence/theme-history-actions/final/visuals/desktop/dark/desktop-trends-dark.png`
- `evidence/theme-history-actions/final/visuals/desktop/light/desktop-trends-light.png`
- `evidence/theme-history-actions/final/visuals/actionstate/dark/` 中的 `action-state-enabled-dark.png`、`action-state-disabled-dark.png`、`action-state-background-dark.png`、`action-state-connection-dark.png`
- `evidence/theme-history-actions/final/visuals/actionstate/light/` 中对应的 enabled、disabled、background、connection 四张 PNG。

本次查看时其余视觉矩阵仍在生成。这些是当前生成文件的画面检查，**文件位于 final 目录不等于证据已封存**；本段不代替根任务后续的完整清单、构建对应、测试结果和封存记录，也不宣称整个视觉矩阵已经检查完毕。

两张新趋势图都选中顶部 DAY，同时详细历史范围为 30 天。深色与浅色呈现相同的历史主层级，可确认：

1. 顶部直接进入“历史用量”，没有源主窗口的 TOTAL TOKENS、完整总量/紧凑数和费用区域。缺口 1 仍存在；不能将详情中的“已知用量 244,530,000 Token”当作当前 DAY headline。
2. 主内容仍显示 7/30/90/365/全部范围、柱状图/K 线/折线、按工具/按模型，以及 Codex/Claude Code 的堆叠图和图例。源原图则显示“近 7 天”、↗、单色 spark 和首尾日期轴。缺口 2 仍存在，入口迁移尚未实施。两图数据和选中窗口不同，不比较柱高或总量是否相等；浅色图也不用于与深色 source 逐像素核对颜色。
3. 源原图在主窗口底部直接显示四张“数值在上、标题在下”的 2×2 卡片。当前两张安卓初始截图的可见内容延续到历史覆盖说明、已知费用和分隔线，没有该主窗口四卡布局。缺口 3 的主窗口结构仍存在；下方 lifetime 历史摘要超过截图可见范围，不能据此宣称该摘要缺失。固定周期的字段来源差异来自前述源码核对，本轮 DAY 截图本身没有验证 fixed ready 数值，仍须下一轮的字段对照测试。

读取按钮的旧问题已在实际画面中排除：两张加载完历史的新图均不再显示“读取活动与趋势”，历史标题已上移。这个结论仅适用于本次正常且历史已存在的场景；缺失历史或错误状态仍需要有效读取/重试入口，不应为了画面相似而全部移除。

已查看的八张 actionstate 图中，订阅读取按钮的 enabled 状态保留可读正文和轮廓，disabled 状态文字及轮廓降低透明度；background 与 connection 的处理中按钮也呈禁用外观，深色/浅色均能区分。这些图证明相应生成场景的可见状态，不替代点击是否被阻止、action 调用次数、连接结果或趋势详情返回流程的测试，也不改变本文件“迁移尚未实施”的状态。

最终范围补充：0.2.43-dev 已在 evidence/theme-history-actions/final 封存，APK SHA-256 `29c2b9ecffebb0b35c32388a2cfe755725a037d41a82cabbdbce5049ab72322d`，9个本地对照选择全部加载，14份视觉报告和70张原生PNG通过。上述三个趋势主层级差异仍未实施修复；封存结果不改变其待办状态。
