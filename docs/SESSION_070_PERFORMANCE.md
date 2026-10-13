# Desktop 0.70 会话展示与性能适配

日期：2026-10-14。Android 基线：正式 0.2.61（versionCode 70）的会话投影。上游固定为 `ac4eeb2c4aea239dd94b67f074c73f69aafc46f8`（v0.70.0），只比较 v0.69.0 → v0.70.0，不用 main。

## 差异与所属层

| 上游变化及源码依据 | Android 当前路径与本轮处理 | 验收条件 |
| --- | --- | --- |
| [sessionRows.js:534–548](https://github.com/Javis603/token-monitor/blob/ac4eeb2c4aea239dd94b67f074c73f69aafc46f8/src/electron/renderer/sessionRows.js#L534) 对独立会话先检查是否存在父关系或重复 native/app 身份，避免建立别名和逐项走父链 | `SessionRelationshipGroups.group` 加同语义快速路径；Grok、重复身份、未知/缺失/跨 client 父关系继续完整算法 | 与 Android 0.2.61 冻结实现逐 row/group/member 等值；Token、金额、未定价总量不变；100 行分页不变 |
| [t3SessionMetadata.js:157–182](https://github.com/Javis603/token-monitor/blob/ac4eeb2c4aea239dd94b67f074c73f69aafc46f8/src/shared/t3SessionMetadata.js#L157) 用 `json_each(?)` 单关系查询取代分块 IN，减少历史会话 JSON 身份重复扫描 | 属于 Agent 的本地数据库读取。Android 已读取 Hub 提供的 `title` / `projectLabel` / `sessionId`，继续既有标题隐藏和降级；本轮不复制 SQLite、日志或采集 | Agent/Hub 支持并输出标题后 Android 展示；缺失标题仍按既有降级，完整快照撤销标题不能留旧字。生产 Agent 数据库性能未验证 |
| Android 自身 Home 近期列表原先对全量时间反复解析再排序，仅展示最近 5 条和额外 running 条 | `SessionDisplay` 先去重、一次解析有效时间；Home 用有界候选堆，再仅对 5 条候选与 running/waiting 条排序；完整 source API 仍保留全量稳定排序 | month 优先于 today，同时间保持输入顺序，Review 排除，额外 running/waiting 不受 5 条上限；状态过期、0/负 cap、未知状态与缺数据不变 |
| Android 自身标题列表每个 ID 重建 3 个正则、仅为模型个数却排序模型键，分页先复制剩余所有行再截 100 行 | `SessionListDisplay` 复用正则、直接取模型键个数，分页只复制可见 100 行；已排序列表在关系处理后通过线性检查直接返回 | 标题/副标题/活动/ID/模型标签与旧实现完全一致；时间→Token→费用→名称排序不变；page clamp 不变 |

Home 保留 Android 已有单会话模型；未搬入桌面 `groupedSessionRows` 的 Home 分组 DTO。本轮对齐该入口适用的活动选择和减少无关历史展示计算原则，Sessions 页则继续先对完整会计输入分组再分页，不能声称桌面 Home 分组入口全部对齐。

Android 只做已提供元数据的显示与排序；没有添加网络请求、采集、数据库依赖或统计重算。没有持久缓存新字段，现有标题与时区/语言/数字语义继续由 Hub 和既有设置决定。

## 明确的行为适配

固定 [edgeDock/presentation.js:382–386](https://github.com/Javis603/token-monitor/blob/ac4eeb2c4aea239dd94b67f074c73f69aafc46f8/src/electron/renderer/edgeDock/presentation.js#L382) 的 includeRunningBeyondCap 会同时保留 running 与 waiting。Android 0.2.61 只保留 running，本轮修正 Home 额外 waiting 会话的可见性。独立 Golden 明确断言旧结果为最近 5 条 + running、新结果为最近 5 条 + waiting + running；30 秒 live 租约到期后，同一个快照再次按时间判断并移除过期的旧活动，Archived/Review/unknown 不补成活动。未删除或降低原 76 组 Home golden。

## 比较方法

`SessionPerformance070Test` 使用同一批不可变合成数据分别传给旧实现与新实现。旧代码冻结在 test-only `LegacySession070.kt`，来自修改前正式 0.2.61 的 `SessionListDisplay` / `SessionRelationshipGroups`；Home 旧 source/recent 选择原样保存在测试中。测试引用相同 Activity/模型元数据规则，没有以近似算法替代旧路径。

- 规模分别为 2,000 与 10,000 会话，打乱输入且包含相同时间；month/today 重复 ID、缺标题、项目名/ID 降级、未知模型/client/kind/live state、Archived、Review、Grok、重复 native identity、正常/缺失/跨 client parent。
- source/recent 比较完整 `UsageRow`；recent 使用固定 0.70 的 running/waiting 选择规则（其他数据语义与 0.2.61 相同），在 now、live 到期与 running 到期三个时间上，分别检查 -1、0、1、5、100、超过全量的 cap。
- raw/group/page 比较全部 `SessionListRow` 内容、成员角色、排序、费用、Token 及未定价总量；标题隐藏两种状态，负页/首页/中间/末尾/越界页。
- 基准每阶段预热 5 次，之后 15 次交错配对 old→new 与 new→old，用 monotonic 纳秒时钟；报告中位数及 nearest-rank P95（15 样本的最大值）。结果以 JVM 单元测试实际执行为准，不用不稳定耗时阈值取代逻辑验收。
- stages：source、recent、title-projection（全量标题/统计/时间投影）、independent-group、related-review-group（关系、Review/Grok 混合）。Fixture 构造、解析与断言不计入计时段。recent 性能配对使用不含 cap 外 waiting 的同一数据，保证旧新结果完全相等；waiting 的预期行为差异单独验证。

## 实测结果

第三轮完整 Release 源码 gate 成功；本专项 `SessionPerformance070Test` 4 方法通过（0 failures / 0 errors），包含固定 0.70 waiting 差异 Golden。实际环境是 macOS 主机的 JDK 17、Gradle `testReleaseUnitTest` JVM。以下是该同实现第三轮实测；后续发布冻结的同源 gate 状态见统一发布验收，不把本表称为实体手机或 Android 设备帧时间。

本轮原始报告冻结于 `evidence/desktop-070/session-performance/third-unit.xml`（JUnit UTC timestamp `2026-10-13T22:08:39.683Z`）；第一次会话专项当时 3 方法通过的报告仍保留 `first-unit.xml`，新增 waiting Golden 的通过以第三轮为准。

单位毫秒；15 个计时样本、5 次预热，P95 使用 nearest rank：

| 会话数 | 阶段 | 中位数旧 | 中位数新 | P95 旧 | P95 新 |
| --- | --- | ---: | ---: | ---: | ---: |
| 2000 | source | 25.633 | 2.543 | 49.864 | 5.712 |
| 2000 | recent | 15.956 | 1.520 | 21.856 | 1.805 |
| 2000 | title-projection | 10.815 | 6.879 | 15.711 | 11.352 |
| 2000 | independent-group | 0.867 | 0.811 | 1.783 | 1.619 |
| 2000 | related-review-group | 3.227 | 1.585 | 4.913 | 4.108 |
| 10000 | source | 104.881 | 7.806 | 160.340 | 13.897 |
| 10000 | recent | 103.021 | 6.770 | 135.520 | 18.441 |
| 10000 | title-projection | 37.854 | 26.563 | 47.493 | 42.478 |
| 10000 | independent-group | 2.867 | 2.198 | 4.502 | 2.919 |
| 10000 | related-review-group | 14.390 | 5.496 | 17.816 | 7.545 |

同一批输入的条数（单次投影/选择；benchmark recent 不含 cap 外 waiting，waiting 额外展示由独立 Golden 验证）：

| 规模 | month+today 输入 | Home 有效去重 | Home 展示 | raw 展示投影 | 独立列表分组数 | 关系/Review/Grok 分组数 | 分组展开成员数 | 首页面行数 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 2000 | 2032 | 2001 | 43 | 2000 | 2000 | 1947 | 2000 | 100 |
| 10000 | 10032 | 10001 | 43 | 10000 | 10000 | 9732 | 10000 | 100 |

`rawProjected` 为完整列表，没有通过提前截断会计输入获得收益；关系、Review 和 Grok 的全部输入成员都保留。Home 选出的 43 条作为展示输入，不能把它写成只有 5 条；本基准未测 Compose 实际重组次数；running/waiting 的保留本来就可能超过 cap。每个计时样本各执行一次相应阶段，Fixture 解析、构建及断言不计时。

10,000 条的客户端标题/展示投影中位数 37.854 → 26.563ms，近期选择 103.021 → 6.770ms。各阶段本次中位数与 P95 均下降；独立分组 2,000 条的中位改善较小（0.867 → 0.811ms），不将这次 JVM 差异推广为所有手机/数据分布的保证。

原始 stdout：

```text
SESSION_070_BENCH size=2000 stage=source samples=15 warmup=5 oldMedianMs=25.633 newMedianMs=2.543 oldP95Ms=49.864 newP95Ms=5.712
SESSION_070_BENCH size=2000 stage=recent samples=15 warmup=5 oldMedianMs=15.956 newMedianMs=1.520 oldP95Ms=21.856 newP95Ms=1.805
SESSION_070_BENCH size=2000 stage=title-projection samples=15 warmup=5 oldMedianMs=10.815 newMedianMs=6.879 oldP95Ms=15.711 newP95Ms=11.352
SESSION_070_BENCH size=2000 stage=independent-group samples=15 warmup=5 oldMedianMs=0.867 newMedianMs=0.811 oldP95Ms=1.783 newP95Ms=1.619
SESSION_070_COUNTS size=2000 sourceRows=2032 homeEligible=2001 homeDisplayed=43 rawProjected=2000 independentGroups=2000 relatedGroups=1947 relatedMembers=2000 pageDisplayed=100
SESSION_070_BENCH size=2000 stage=related-review-group samples=15 warmup=5 oldMedianMs=3.227 newMedianMs=1.585 oldP95Ms=4.913 newP95Ms=4.108
SESSION_070_BENCH size=10000 stage=source samples=15 warmup=5 oldMedianMs=104.881 newMedianMs=7.806 oldP95Ms=160.340 newP95Ms=13.897
SESSION_070_BENCH size=10000 stage=recent samples=15 warmup=5 oldMedianMs=103.021 newMedianMs=6.770 oldP95Ms=135.520 newP95Ms=18.441
SESSION_070_BENCH size=10000 stage=title-projection samples=15 warmup=5 oldMedianMs=37.854 newMedianMs=26.563 oldP95Ms=47.493 newP95Ms=42.478
SESSION_070_BENCH size=10000 stage=independent-group samples=15 warmup=5 oldMedianMs=2.867 newMedianMs=2.198 oldP95Ms=4.502 newP95Ms=2.919
SESSION_070_COUNTS size=10000 sourceRows=10032 homeEligible=10001 homeDisplayed=43 rawProjected=10000 independentGroups=10000 relatedGroups=9732 relatedMembers=10000 pageDisplayed=100
SESSION_070_BENCH size=10000 stage=related-review-group samples=15 warmup=5 oldMedianMs=14.390 newMedianMs=5.496 oldP95Ms=17.816 newP95Ms=7.545
```

## 验证边界

本专项是同数据结果与客户端纯投影 CPU 时间比较。桌面 SQL 标题获取耗时、真实 Hub 大快照传输、手机首帧/滚动帧时间、电量和实体手机流畅度均未在此基准验证。Compose 长列表/大字体、网络恢复、缓存和正式升级由本轮统一 Release 验收报告记录；不以本专项 JVM 耗时替代设备结果。

未修改 Compose 结构、行高、字体、趋势图、页大小、用户设置、网络和 SSE。关系、Review、Grok 会话仍以独立 Hub accounting key 计数，每个成员保留一次，Android 不推测压缩/归档 transcript 的缺失数据。
