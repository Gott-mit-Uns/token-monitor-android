# 下一阶段：独立 Dashboard 整页复刻

状态：**第46阶段整页已实施并封存为0.2.46-dev / code55**。八项摘要、年度热图、Top 5、独立趋势投影、主进程模型别名presentation及原有过渡已接入；244单元、173普通安卓、八配置Dashboard画面及九轮趋势Window诊断通过。最终实施、性能等待成本和实际设备限制见 [DASHBOARD_PARITY.md](../../DASHBOARD_PARITY.md)。下文保留原始计划与审查基线，旧长页描述不再表示当前界面；手机/Lucky、手动历史回执及容量修复仍在整体目标中。

本阶段复刻桌面 v0.68.0 的完整 Dashboard 行为。第44／45阶段的主窗口趋势、headline、四卡及420ms柱形过渡属于另一套来源规则；Dashboard采用独立的容器、数据投影和动效，不将其规则接入主窗口。

固定原版根目录：`../work/token-monitor-reference/desktop-v0.68.0`，参考提交 `5d2db368d8313415763860d594de00e46a663418`。下文 `renderer/*` 指原版 `src/electron/renderer/*`；原生文件名指本项目 `app/src/main/java/dev/bing/tokenmonitor/*`。行号来自本次读取的当前文件，实施时若文件移动须重新核实。

## 页面边界与目前差距

原 `renderer/app.js:12809–12817` 从主窗口趋势图或 ↗ 打开独立 Dashboard；`src/electron/main.js:7124–7182` 创建或重新加载独立窗口；`renderer/dashboard.html:21–57` 定义“总览／趋势”两个标签。`renderer/dashboard.js:40–45` 默认总览、30 范围、按工具、柱状图、费用热图。再次打开已有桌面窗口会 reload（`main.js:7125–7130`），不能从源码推导 Dashboard 的所有选项永久保存。

Android 已有独立历史入口、返回、滚动状态和完整历史读取：`DesktopHubScreen.kt:79–85,149–155,218–245,255,305,466–480`。但入口内容仍为 `HistoryTrend.kt:26–70` 的单一长页：范围、已知 Token、图表、覆盖说明、摘要、热图、日／月记录依次排列。已查看第 44 阶段封存的 `evidence/trends-main/final/visuals/desktop/dark/desktop-history-dark.png`，首屏与这一顺序一致；这张 Android 图片不是原 Dashboard 的渲染证据，也不是下一阶段候选图。

下一阶段保留独立详情入口及主窗口返回关系，换成 Dashboard 专用容器与局部状态。默认打开总览；趋势范围、图表模式及维度不得借用或改变主窗口 DAY／MONTH／TOTAL／fixed 状态。Dashboard 内切换标签或临时打开设置时保留当前页面状态；关闭后重新打开的默认状态要与原版 reload 规则对应并通过实际导航测试。Android 后退行为、48dp 触点及小屏自然滚动属于平台适配。

## 完整页面范围

| 区域 | 原版依据 | 下一阶段内容及原生边界 |
| --- | --- | --- |
| 页面壳与标签 | `dashboard.html:11–57`；`dashboard.css:40–84`；`dashboard.js:41–45,542–559,766–783` | 独立标题、返回／刷新、总览／趋势两个标签。总览内容居中，趋势控制条与大图构成单独页面。隐藏另一标签的内容，不能把两页继续串成长页。 |
| 八项摘要 | `usageCharts.js:291–308,632–677`；`dashboard.js:257–276,515–538`；`dashboard.css:115–119` | 按顺序显示总 Token、总费用、活跃天数、当前连续天数、活跃时间、峰值日 Token、常用模型、消息数。直接读 `history.summary`，不能用当前趋势范围重新算 lifetime 摘要。使用 compact Token／money、分钟 half-up、display font、tnum、统一背景和分隔。手机按宽度改为二列／四列并保持可读，不硬塞八列。 |
| 活动热图 | `dashboard.js:492–514,693,742–746,785–790`；`usageCharts.js:109–126,243–279`；`dashboard.css:65–68,106–113` | 本地今天为终点，从 11 个月前月首开始，Sunday 起始列；热度根据全部 daily 的原始 Token／费用重新计算。默认费用，接入已有 `preferences.heatmapMetric` 并保存切换。格子按可用宽度在 9–22 的源范围内调整、间隔 4；窄屏允许横向查看。选日展示真实值、费用和未定价说明。 |
| 模型／工具 Top 5 | `dashboard.js:391–448`；`dashboard.css:121–129` | 汇总全部保留的 `history.daily`，分别取正值 Top 5。横条长度以本列最大值为分母；百分比以全部 daily 总 Token 为分母。模型在前、工具在后；宽屏两列，手机可上下排。颜色、紧凑数值及百分比共同保留。 |
| 趋势控制及图表 | `dashboard.html:26–42`；`dashboard.js:302–312,338–388,551,773–783`；`usageCharts.js:129–240,472–475,523–585` | 按工具／按模型、柱状图／K 线、7／30／90／365／全部范围。K 线隐藏维度和图例。大图有 compact 纵轴、四个间隔共五条刻度、抽样日期标签、正确柱顶圆角／K 线颜色。所选范围默认完整放入绘图区，不沿用现有每条至少 7dp 的横向长图作为默认布局。 |
| 图例与选中信息 | `dashboard.js:315–352,607–637,797–817`；`dashboard.css:97–104,131–136` | 柱图图例按分项总量降序，compact 值及一位百分比，独立有界滚动；过暗品牌色使用 Dashboard 的可见性提升。触屏点选对应原 tooltip：当前维度的单日分项、实际 K 线日期范围与 OHLC、热图日期与费用。保留 Android 可访问的详情关闭操作。 |
| 空、缺失及失败 | `dashboard.html:59–60`；`dashboard.js:358–359,395–396,542–559,643–662` | 区分真正无日／月历史、仅 monthly、数据读取中、读取失败仍有旧值、字段未提供及未定价。空状态仍可返回和刷新。原 JS 的数字兜底与 Android 的 unknown／重复日处理分别记录，不能将未提供字段伪造成已知零值。 |

现有 `DesktopHistorySummary.kt:17–37` 已拥有八项字段、费用精度和未定价说明，主要需要 Dashboard 排版和源格式，不能删掉有效成本说明。原生 formatter 可复用 `DesktopCompactTokens.format`、`UsageCurrency.label` 的 compact 路径及 `TrendsMainProjection.activeDuration` 的分钟舍入；必须以源对照确认输出，不能仅按方法名称认定完全等价。

热图可复用 `HomeCharts.kt:22–50` 的 `project(history, null, localToday, "year")`、月首和 Sunday 网格：明确传 `null`，不补入主页 live 今日值。现有 `DesktopCharts.kt:40–42` 却以最大历史日期作为 today，82–84 行使用固定 9dp／12dp 节距；`HistoryTrend.kt:46–52` 另建默认 Token 的局部偏好。这些都须由 Dashboard 专用接入修正，不全局改变主页热图。可复用现有选日详情，但 `HomeCharts.hitCell:53–59` 的固定尺寸换算不能直接用于可变尺寸图。

`HistoryActivity.kt:20–33,60–83` 的 Monday／53 周大触点日历属于已有记录查看扩展，不作为原 Dashboard 活动热图的几何来源。

## 数据投影与记录查看必须隔离

拟新增 Dashboard 专用纯投影、图表几何和可保存的页面状态，再由专用 Composable 消费。文件与 API 名称可在实施时确定；以下语义不能通过直接复用旧 helper 被改变。

| 规则 | 真实来源 | 当前原生差异与正确边界 |
| --- | --- | --- |
| last N records | `usageCharts.js:472–475`；`dashboard.js:358` | 原 `clampDaily` 为升序数组 `slice(-N)`，不是回溯 N 个日历日。`HubHistory.kt:54–67` 的 `HistoryRanges.summarize` 是日历范围，继续服务记录明细；Dashboard 将解析器的降序列表转为明确升序后取最后 N 条，不自动填充缺失日期。 |
| attributed sum | `usageCharts.js:134–174` | 柱总量、峰值、key 排序、图例均来自所选 `perClient`／`perModel` 分项之和。`HistoryAnalytics.kt:28–39` 会补未归属并处理超额归属；`DesktopHistoryChart.kt:53–54,93–100` 又按 row.tokens 峰值缩放。Dashboard 不能直接调用完整 stacks helper；保持源柱图与图例口径，将 unknown／缺归属说明放到详情或记录视图。 |
| alias 与排序 | `usageCharts.js:20–65,146–150`；`dashboard.js:402–435`；`main.js:7185–7198`；`modelAliasPresentation.js:82–96,280–298` | renderer 工具仅折叠 `antigravity-cli→antigravity`，模型 key 在 renderer 内不再次折叠。完整 IPC 在获取 raw Hub history 后先应用共享模型别名与分组；duplicates 观察完整 daily、monthly、favorite 与 clientModelCosts 模型 key。别名计划活跃且原 favorite 非空时，以全部 retained daily 重排 favorite。柱图同总量用源 localeCompare；Top 5 按独立源排序规则。不能把 raw renderer fixture 当成整个 IPC 契约。 |
| OHLC 与桶范围 | `usageCharts.js:177–240`；`dashboard.js:365–371,617–624` | 桶按最新实际日期向后划分，但 key／endKey、open／close是桶中实际最早／最晚记录。`HistoryAnalytics.kt:16–23` 用日历桶边界查询，稀疏数据可能得到 null；不能直接作为 Dashboard oracle。桶大小由 source span 与绘图区可用宽度推导，左右留白要先扣除。 |
| 图表窗口与坐标 | `dashboard.js:298–299,360–387`；`usageCharts.js:523–585` | 源最小测量宽 320、高 200，趋势图 padding 为 top10/right14/bottom24/left52，bar gap .3、candle gap .4。纵轴数值与横轴日期占独立留白。手机可按实际可用高度自然滚动，但不能以固定 180dp 无数值网格宣称源一致。 |
| Top 5 分母 | `dashboard.js:395–435` | 排名使用全部 retained daily，与 lifetime summary 和趋势范围各自独立。工具／模型排名不补未归属；按本列最大值画条、按 daily 总 Token 显示占比。 |
| lifetime 摘要 | `dashboard.js:515–538`；`src/shared/history.js:504–538` | 保留 Hub summary，可超出有上限的 daily 窗口。不能用当前可见热图或最近 30 条覆盖总费用、总 Token、消息数和活跃时间。 |

原生旧的覆盖说明、完整精度、日／月分页、折线图、重复日保护、未归属说明和大触点日历不是待删除功能。将它们收敛到明确的“记录明细”入口，继续使用既有 `HistoryRanges`／`HistoryAnalytics` 语义；Dashboard 主层级使用自己的 source 投影。记录日期沿用 Hub 日期，不将记录转换成手机时区；“本地今天”只决定源活动热图的日历窗口。

本次只读执行原 `usageCharts.js` 的公开样本已经确认三个差异，结果未形成下一阶段封存证据：

- 输入只有 `2026-10-01、10-05、10-09` 三条时，原 range7 保留三条；现日历 7 日范围从 10-03 开始。
- 单日总 Token1000、工具分项100时，原柱总量／峰值为100，不补900未归属。
- bucketDays3且桶内只有10-05一条时，原 key／endKey均为10-05，open／close相同，不查询10-04／10-06。

## 外观、偏好与平台适配

使用现有统一主题、字体、语言、货币、紧凑单位、vendor overrides及减少动态效果偏好，避免另造一套页面主题。原 `dashboard.js:315–324` 的亮度阈值42／向205提升规则仅属于 Dashboard 的品牌色可见性处理，不能全局修改主窗口供应商颜色。K 线源色在 `dashboard.css:94–95` 为上升 `#4ec77f`、下降 `#f06a7b`；当前 `DesktopHistoryChart.kt:107` 使用另一组色值。

`dashboard.js:792` 的 ◐ 操作切换 `dashboardFlat`、玻璃／实底，**不等于 light/dark**。若 Android 保留这一操作，须有独立语义和设置；不得将其映射为明暗主题切换。桌面的拖窗、最小化、关闭和原生玻璃材质不照搬；Android 使用已有返回、刷新与主题背景策略，并明确这些是平台适配。

窄屏／横屏／大字保证真实内容可见和自然滚动，触点至少48dp；允许摘要换列、排名上下排列和触屏详情替代 hover。适配不改变聚合分母、范围窗口、字段顺序及源图表模式。正常未知值、部分费用、超大整数继续保留精度和可辨认状态，不因追求 JS 数值格式而降级模型精度。

## Dashboard 动效最后接入，仍属于整页验收范围

静态结构、源数据和真实交互稳定后，再接 Dashboard 自己的动效。主窗口 `TrendsBarMotion` 的420ms规则不能直接套用；共享单调时钟、减少动态效果及绘制层读取原则可以复用。

| 原动效 | 来源及规则 |
| --- | --- |
| 堆叠柱／数据几何 | `dashboard.js:50,73–109`：捕获当前屏幕 rect；已有 key 按旧新 width/height/left/top 做几何续接，小位移／比例按源阈值跳过。800ms，原 cubic-bezier；新 key 最多 index18×12ms延迟，entry／series／模式变化的 fromZero见355–388行。不是只变高度的主窗口动画。 |
| K 线 | `dashboard.js:51,112–140`：body从中心纵向缩放、两段wick描线，560ms，最多index18×10ms延迟。 |
| 热图 | `dashboard.js:52–54,142–205,508–513`：720ms横向展开总跨度，单格280ms，按x位置延迟；等待焦点、刷新结束与双RAF，generation防止旧SVG任务污染新图；预绘制隐藏避免整图闪现。 |
| 排名横条 | `dashboard.js:437–448`：捕获当前条宽后续接，800ms，entry从0；不自创入场／退出效果。 |
| 触发与终止 | `dashboard.js:62–70,542–589,643–662,750–783,819–828`：tab entry、维度series、范围／mode update；偏好与resize按各自源标志处理，resize为none。减少动态效果finish已有动画；隐藏时合并渲染／刷新。Android STOP收敛和禁止后台绘制属于平台策略，单独记录。 |

逐帧状态仅在实际 Canvas draw／必要 layer读取；Dashboard owner和静态摘要、轴、图例不随动画逐帧重组。数据渲染事件、entry generation和组件重组分别建模。Dashboard自己的动画状态不得改变主窗口趋势的entry，返回主窗口不得因此重播。先验证源事件与几何，再测运行成本；不新增装饰效果或强制帧画廊。

## 可执行的公开源 oracle 与验收

实施前新建独立公开来源工具与结果目录，例如 `evidence/dashboard-parity/reference/`，不修改已封存的44／45证据。工具调用固定原 `usageCharts.js` 导出函数；完整 `dashboard.js` 的投影／render／handler行为使用有边界的公开 fake DOM、CSS测量和WAAPI／RAF记录。提取函数与handler必须来自原文件，记录提交、完整模块SHA-256、提取范围与指纹；不能仅复写 Kotlin 公式作为唯一 oracle。fake尺寸和动画记录不证明浏览器真实画面或手机FPS。

| 验收层 | 必须执行的范围与通过条件 |
| --- | --- |
| 源 oracle：数据 | 7/30/90/365/all、完整／稀疏／空／仅monthly；lastN与日历N差异；client/model sum、缺归属／超额归属、alias与同总量排序；有缺日的OHLC、不同viewport桶大小、末桶锚点；Top5筛选及两个分母；lifetime大于daily；summary缺字段、零／部分／全未定价、分钟30s与59m30s、compact units／货币。每项有原模块实际输出和对应命名Kotlin用例。 |
| 源 oracle：页面与动效 | 默认Overview与控件状态；切tab／range／mode／series、刷新重入队列、resize none；柱rect续接／新key延迟阈值、K线body/wick、热图generation／焦点／刷新／双RAF／guard、Top5条续接、减少动态效果finish及隐藏合并。执行原函数／实际handler，不以镜像实现代替。 |
| 纯投影测试 | 对应全部源命名case；正常值与源输出一致；BigInteger／BigDecimal范围外精度、null／重复日及不可信字段的Android适配单独断言，不能暗中改为已知零。固定本地日历clock、时区跨午夜、Hub历史落后、月份边界。 |
| 实际HubScreen旅程 | 从主窗口趋势图／↗打开默认总览，8项摘要／全年热图／两组Top5真实显示；切趋势、每种range与client/model、bars/kline，维度／legend显隐正确；点柱／K线／热图并关闭；进入记录明细、日月分页／旧扩展仍可用；系统后退和可见返回回主窗口；设置返回、关闭再打开、重建activity的状态边界；刷新旧值／新值／失败／重试和连接切换。 |
| 网络边界 | 用公开HubUiActions计数fixture，在已有full history时切tab/range/mode/series／选日／动效／返回新增读取为0；首次实际详情读取、用户刷新和history revision变化只走既有历史读取策略，次数有明确预期。暂停／后台无额外读取或帧任务。无新增Hub API、SSE、服务、采集或后台周期任务。 |
| 真实布局／像素 | 中文与英文、dark/light、默认phone、窄屏大字、短横屏、tablet、reduced motion按风险选择矩阵。保存真实Dashboard Overview/Trends两页及必要选中／empty状态；检查全部摘要与caption不裁、compact值不挤掉百分比、Top5比例／色块、刻度／日期轴、K线色、热图日期和可用触点；以实际plot尺寸验几何，不能只assert helper或截图存在。 |
| 真实运动与成本 | 手动同源时钟下实际Canvas像素证明entry／更新／打断／tab／resize／减少动态效果／STOP行为，静态区域提交次数不随帧增长。真实帧时钟测量固定候选、AVD、构建类型、预热、fixture和窗口；报告FrameMetrics draw/total p95、deadline、cadence与完整性。窗口cadence不称为手机FPS，scope提交次数不称为全树重组；少量debug预热不证明JIT稳定。 |
| 回归与交付 | 运行受影响纯测试、现有记录查看／范围／费用／主题／偏好／主窗口导航和动效测试，再执行项目所需完整普通回归与lint。保留第44／45来源、证据和主窗口行为。候选APK与公开视觉结果经校验后封存；文档注明实际执行范围、剩余源差异和平台适配。 |

内部顺序：先冻结源oracle与Dashboard专用数据契约，再完成容器及总览／趋势／记录明细的完整交互，接入独立动效，统一做实际页面、读取次数、像素和运行验证。全部范围完成并验证前，不能把“详情入口已存在”或“若干测试通过”称为 Dashboard 整页完成。

本计划及本轮源码审查不证明实体 Xiaomi 13 Ultra 的呈现、Lucky 公网 HTTPS、安装／传输或手机帧率；后续仅报告实际执行的验证，不复用旧交付表述冒充这些结果。
