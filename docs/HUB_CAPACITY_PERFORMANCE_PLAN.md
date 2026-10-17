# Hub 容量性能计划：第 49 阶段

> 当前适用边界：Android 小组件已取消，下文 widget 测量与候选仅保留历史依据，不再作为待执行工作。当前性能任务只覆盖应用前台、网络、解析、页面与完整缓存；不得恢复小组件来复跑旧计划。

状态：**粗粒度测量基线与 B1 typed freshness 复用已完成，整体性能优化继续**。0.2.49-dev / code58 是测量基线；0.2.50-dev / code59 完成同一 SSE generation 的 typed freshness 复用，通过 316 项单元、206 项普通 API36 回归、7 项容量路径、3 项独立集成，以及空首页与默认四模块首页各 5 次独立进程测量。具体范围、原始记录与 P50/P95 见 [49 测量基线](HUB_CAPACITY_PERFORMANCE_BASELINE.md) 和 [50 B1 比较](HUB_CAPACITY_FRESHNESS_REUSE.md)。局部 typed 重建减少没有证明整个动作或主线程等待一致改善；精确分配、对象存活与采样调用栈尚未取得。推荐安装包仍为 [已封存的 48](HUB_CAPACITY.md)。后续先补 [48 导航、语言和动效反馈](USER_FEEDBACK_048.md)，之后继续 B2/B3 和页面准备；原目标不变。

目标是在现有 **192 MiB Java 堆预算**内保留完整来源业务，减少大对象并存和主线程全表派生，验证实际页面是否更快、更稳定。不得增加堆或输入上限、缩减 fixture、删除顶层会话/项目/历史、改变费用与未定价语义，或关闭必要数字动效来通过验收。用户额外强调首页Token动效的要求已撤销；此处只保持桌面本来已有的统一过渡、帧状态隔离与减少动态效果规则，不恢复固定帧交付要求。继续使用固定桌面 v0.68.0，提交 `5d2db368d8313415763860d594de00e46a663418`；不修改 Hub 服务、采集、远端数据或读写职责。

## 已测基线与它不能证明的内容

以下报告均记录 `passed=true`、`fixtureReduced=false`、`physicalDeviceMeasured=false`。对应应用 SHA256 为 `adc71a866aa8b0c5f661a16fc37e151c65ec77441361cd7db137fe4a93c30307`，见 [ViewModel 运行摘要](../evidence/hub-capacity48/focused/viewmodel-final/run-summary.json) 与 [缓存运行摘要](../evidence/hub-capacity48/focused/cache-final/run-summary.json)。这些是容量与业务路径回执，不能解释某一次页面停顿的原因。

| 已测路径 | Java 堆上限 / 采样使用峰值 | 其他实测值 | 证据 |
|---|---:|---|---|
| 实际 ViewModel / HubScreen / 后台 / 冷恢复 | 201,326,592 / 201,346,464 B | 最大主队列心跳延迟 2,513 ms；native 采样峰值 60,516,080 B；最终 PSS 241,645 KiB；总耗时 29,975 ms | [实际界面报告](../evidence/hub-capacity48/focused/viewmodel-final/capacity-viewmodel-ui-public.json) |
| 全尺寸连续 SSE，磁盘保存被 gate 阻塞 | 201,326,592 / 201,319,984 B | 最大主队列心跳延迟 5 ms；5 个不同 revision 有序提交；后台后排队写入被隔离 | [burst 报告](../evidence/hub-capacity48/focused/viewmodel-final/capacity-viewmodel-burst-public.json) |
| 16 设备直接缓存、完整冷恢复与本地 widget | 201,326,592 / 200,827,616 B | 来源读取/解析/保存 3,717.13 ms；冷读取 513.77 ms；已解析 Snapshot 到 widget 小投影 1.89 ms；v2 密文 340,217 B | [缓存报告](../evidence/hub-capacity48/focused/cache-final/capacity-cache-transport-16-devices-public.json) |

实际界面报告的复合导航计时为：切 allTime 250 ms、切 month 416 ms、进入 Sessions 并翻页 4,347 ms、进入 Projects 并滚动/打开来源行 2,665 ms、进入 Devices 并滚动/打开末设备 2,003 ms、进入 Trends / Dashboard 1,285 ms。它们包含测试交互、等待布局、滚动及断言，不能直接当作函数耗时、首帧时间或用户输入延迟。

这些报告说明当前样例可完成完整业务路径，且采样使用量接近堆上限。采样器每 10 ms 分别读取 `Runtime.totalMemory()` 与 `freeMemory()` 后相减；两次读取并非原子快照。因此 UI 报告略高于 `maxMemory` 的数值**不构成硬堆越界证明**。100 ms 主队列心跳记录的是排队延迟，未区分应用计算、GC 停顿、布局/绘制和测试框架等待；报告也没有把各阶段时间、分配与 GC 关联起来。burst 同样接近堆上限而心跳只有 5 ms，不能仅凭使用峰值断言 UI 停顿来自 GC，或断言它来自某个列表。

当前 UI 容量用例使用 `homeModules=emptyList()` 与 reduced motion 开启。它覆盖实际页面导航，但没有覆盖默认首页全部模块、正常数字动画、真实手机或 Lucky 公网 HTTPS 的性能。这些范围需要分开验证，不能把模拟器结果扩展到它们。

## 不变的来源业务与运行条件

主要基准仍使用运行时生成的完整 16 设备来源：每设备每周期 300 个独立会话和项目，顶层每周期 4,800 会话、4,800 项目、320 模型；31 日和 2 月历史。原始 stats 为 **21,667,533 B**，生产投影正文为 **10,962,285 B**，完整 SSE snapshot 事件为 **21,667,634 B**。每条会话的 `5,000,000,123` tokens、`0.015625 USD`、17 未定价 tokens，以及 today/month/allTime 的原始倍数均继续精确检查。

另一组 8 设备、370 日/13 月历史的完整联合缓存用例继续作为回归，不能用它替代较大的 16 设备 UI 基准。[来源说明与生成器](../evidence/hub-capacity/reference/README.md)、[容量计划](HUB_CAPACITY_PLAN.md) 和 [48 回执](HUB_CAPACITY.md) 规定的来源函数、摘要与逐条业务检查继续沿用。原始大 payload 运行时生成，不作为报告附件常驻；报告只记录计数、字节、摘要、阶段和业务断言，不记录标题、正文、地址、模型 ID 或凭据。

生产边界继续为 32 MiB 解压后输入、10 MiB 缓存密文、32 MiB 缓存展开记录，维持严格 UTF-8、完整 EOF、重复字段拒绝、递归深度与事件边界检查。所有阶段保留前台、generation、binding、认证、sequence、history receipt/key 与 AtomicFile 保护。优化不得通过绕过原解析、取消检查或费用校验来降低耗时。

## 静态候选：已知调用与需要测量的影响

| 候选 | 当前可确认的源码行为 | 最小调查方向 |
|---|---|---|
| 原始字符串、JSON 树、typed Snapshot 同时驻留 | [HubStream](../app/src/main/java/dev/bing/tokenmonitor/HubStream.kt) 的 `current` 保留完整投影 JSON 树；每 full frame 在入队前解析成树，帧还携带正文与索引；[HubViewModel](../app/src/main/java/dev/bing/tokenmonitor/HubViewModel.kt) 再从这棵树构造 typed Snapshot | 记录每种大表示的生命周期与同时存在数量；调查 full frame 只入队正文/索引、唯一消费者临时构树、解析完释放树 |
| freshness 重做完整模型 | HubStream 的 freshness merge 复用 periods 子树，但随后 `root.toString()` 序列化整份正文；VM 对每帧调用 `HubParser.parse`，[HubData](../app/src/main/java/dev/bing/tokenmonitor/HubData.kt) 重建所有 Period/UsageRow 和模型索引 | 调查 typed freshness patch，只更新既有 freshness 字段，复用 periods、history preview 与 observed IDs；缓存正文按真实保存需求在后台生成 |
| 隐藏首页会话仍派生且重复排序 | [DesktopHubScreen](../app/src/main/java/dev/bing/tokenmonitor/DesktopHubScreen.kt) 118–119 无条件计算 source 与 recent；[SessionDisplay](../app/src/main/java/dev/bing/tokenmonitor/SessionDisplay.kt) 的 recent 再调用 source，source 去重/解析时间/排序 month 与 today | 首页模块可见时才生成 source；recent 使用同一份已缓存 source。该路径不扫描 allTime，不应据此解释 allTime 列表代价 |
| 会话数乘全局别名派生 | [SessionListDisplay](../app/src/main/java/dev/bing/tokenmonitor/SessionListDisplay.kt) 逐行调用 modelLabel → project；[HubModelNames](../app/src/main/java/dev/bing/tokenmonitor/HubModelNames.kt) 每次重建显式表，duplicates/prefix 模式还对整份 observed 做 distinct/group/canonical；[SessionListUi](../app/src/main/java/dev/bing/tokenmonitor/SessionListUi.kt) 可见行再次投影两次 | 一个有界 alias resolver 供全部行与模型明细共用；同一行的 label 从已有 projected models 得出 |
| 会话时钟导致静态表重建 | Screen 的 sessionRaw 使用毫秒级 sessionNow 作 key，raw 全表格式化/排序；grouped、query filter、barMax 未缓存；page 的 `drop(start).take(100)` 先复制剩余尾部 | 分离本地日期/时区决定的静态文案与实时 activity/context/cache；缓存 grouped/query/max，空查询直接使用基表；分页只复制目标 100 行切片 |
| Main 上的结构相等检查 | freshness 重建等价 Period/UsageRow 后，`remember(rawPeriod,currency)`、today Period 与 sessions 列表的 key 可以比较大量明细 | 测量 Period/UsageRow equality；按实际字段或可信 revision/引用作 key，不能用全树 hashCode 替代。Snapshot.updated 改变时首字段通常短路，不能笼统称每次 Snapshot key 都全树扫描 |
| Projects 的归一化/汇总/排序 | [ProjectDisplay](../app/src/main/java/dev/bing/tokenmonitor/ProjectDeviceDisplay.kt) 在 Projects 页把全表归一化、精确汇总并排序；费用比较器重复构造 BigDecimal。路由已 gating，不是隐藏 Projects 全表计算 | 缓存当前选期输入与显示参数；排序键预解析一次；必要时唯一后台任务准备显示表，Main 只选择/提交/渲染 |
| 投影与解析临时容器 | [HubJsonProjection](../app/src/main/java/dev/bing/tokenmonitor/HubJsonProjection.kt) 每个模型候选调用构造 `position + ordinal` 列表，重复 ID 也会构造；HubData 和 SessionMetadataParser 的 mapNotNull/toMap 创建 Pair 与中间表 | 仪表确认后，用可复用路径栈、仅接受候选时复制位置；解析小 map 用保持顺序的直接循环，避免中间 Pair/List |
| 保存后的 widget 完整恢复 | persist 调用 updateAll；只有存在 widget 时，[历史 HubWidget 规格](archive/widgets/widget-spec.md) 才 load 完整 SavedHub；[HubStore](../app/src/main/java/dev/bing/tokenmonitor/HubStore.kt) 恢复完整 Snapshot | 分别测无 widget / 有 widget 的保存峰值。1.89 ms 只测已解析模型的小投影，不能当作完整 widget load 的成本；只有确认瓶颈后才设计内部只读最小读取路径 |

这些是可避免工作或并存表示的具体候选，**不是 2,513 ms 停顿的因果结论**。Java 的 String/StringWriter、JSON 树和容器的实际字节成本应从分配与存活数据测量，不能简单把 UTF-8 长度乘一个固定系数当作峰值。

## 阶段 A：先建立可归因的基线

1. 从冻结的 48 源码建立只有测量点的基线构建，记录应用、测试 APK 与源摘要。不能把带仪表的新构建仍标成原 48 APK；它和各候选构建使用相同设备、192 MiB 堆、完整来源、偏好和测试动作。
2. 为 HTTP/SSE 读取、JSON 投影、retained tree parse、Snapshot 构造、ObservedModelIds、frame 排队、Main accept、alias resolver、home source、session raw/group/filter/page、project rows、保存、widget load 与冷恢复分别记录开始/结束、耗时、线程、输入计数和当时队列深度。只记录公开阶段名与数值；不记录 payload 或 private key。同步记录 GC 次数/暂停、Java/native/PSS 采样和帧/主队列时间，用同一单调时钟关联。
3. 在主线程阻塞区间保留可归因的调用栈/trace，分别检查应用计算、Period.equals/UsageRow.equals、排序/别名、GC、Compose 布局绘制与测试框架。给现有 commit observer 增加阶段工作量统计；commit 次数本身不能证明函数调用量或帧时延。
4. 把当前复合导航拆成“输入发出→首次可交互帧”“Sessions 全表准备”“100 行翻页”“来源／周期变化”“Projects 表准备”“来源行滚动/展开”“freshness 接受→帧”。分别报告 P50/P95/最大值与样本数，保留当前组合业务动作作为回归。桌面和安卓 Sessions 主列表均没有查询输入控件，本基线未测该操作；普通 Choreographer 首次 callback 也不能替代首次可交互帧。
5. 每个基线/候选场景至少进行 5 次独立进程重复，区分首次载入、稳定驻留、连续 full frames、freshness 连续更新、保存阻塞与后台取消。不要依靠强制 GC 在测量区间隐藏峰值；冷恢复为释放前一 VM 所做的 GC 单独标明。采样结果继续写作采样峰值，另报告可获得的 GC 后存活量和大表示并存计数。

先完成该阶段，才能为后续候选指定可归因的收益；失败与不确定结果也保留。最终普通完整回归和可选容量回归分开记录，不用少量 focused pass 替代它们。

## 阶段 B：减少完整树驻留与 freshness 重建

先用分阶段数据确认现有 retained body / tree / typed Snapshot 的驻留窗口，再分别实现、差分验证两个小步：

49 基线已确认受控 freshness 的后台全文序列化和 Snapshot 重建成本，尚无精确对象存活数据。实施收敛为三次可独立归因的改动：B1 先只让 freshness 复用 typed Period／UsageRow、history preview 和 observed IDs，暂时保留现有完整 root 与 stringify；B2 再把 producer 的长期 root 改成仅有允许变化字段的原始基准，完整 root 只供 full frame 的一次解析使用；B3 最后把正文合成移到实际获准保存的 IO 任务。B1 通过只能报告局部重建优化，不能认定完整树驻留已解决。

- full frame 的生产路径只向一槽队列交付投影正文与本地索引，由唯一后台消费者创建一次临时 JSON 树、构造 Snapshot 后释放树；不要在 producer 和 queue 中长期保留完整树，也不要为基线比较在同进程额外解析完整原始 21.7 MB JSON。旧 String 兼容入口继续保留完整设备明细与原业务。
- freshness 作为经过严格校验的专用更新类型，按旧 StreamMerge 的字段范围更新：根 updatedAt/staleAfterMs、limits.updatedAt、匹配设备的 updatedAt/receivedAt/ageMs/stale。只更新可见 typed 字段，保留仍需缓存的原始元数据；复用已接受 periods、工具/模型明细、historyPreview 和 observedModelIds。没有基准 snapshot 的 freshness 仍不发布；不把 freshness 当作完整 stats 或新 revision。

缓存恢复必须得到与旧 merge→parse 相同的结果。缓存原文的 freshness patch 合成只在后台、最新实际保存任务需要时做，仍保持原始数值语义、完整字段、签名和认证；不能为了释放树而把 freshness 元数据留在未认证旁文件，或让 Widget 读取旧设备状态。不要先做宽泛缓存格式升级。若无法在保持原 v2 完整恢复与边界时实现轻量保存，先交付引用复用和可测的树生命周期缩短，再单独设计保存改动。

原始基准保留字段的存在性和 JsonElement 值，按原设备位置维护累计 freshness 状态。累计状态直接引用最近一次 full body，不能链式引用前一个 freshness 状态或保留不断增长的 delta 列表。第一版可在真正保存时用短期树合成，仍需测保存峰值；若再做严格流式重写，要保留数值词法、字段追加／覆盖、UTF-8 字节边界和取消检查，不能通过 Double 或 String.length 改写语义。

Main 捕获不可变缓存草稿，`SavedHub.snapshot:String` 与 `HubStore.save(record)` 继续收到完整实值。正文 materialize 在 `storageMutex` 内、过期 sequence／binding／auth 任务被筛掉后执行，结束后再次验证前台、认证、连接绑定、sequence 和协程取消。保存失败提示改用正文来源身份或序列判断最新性，不能在 Main 合成字符串比较。普通 EOF 后已接受的最后缓存任务保留，不能新增 stream epoch 检查把它淘汰。

差分必须覆盖这些易被简化改变的行为：

- 原始设备 ID 按 JsonElement 相等匹配；数值 `1` 与字符串 `"1"`、缺字段与显式 null 都不同。原设备重复 ID 的所有位置均更新，未知 ID 不插入，顺序不改。
- 一次 freshness 内重复 ID 为最后整条 update 获胜。例如先有 stale、后只有 receivedAt 时，前一条 stale 不得混入；连续事件之间才累计允许字段。
- 缺 updatedAt 保留原值，显式 null 覆盖为空；沿用现有 primitive 转换，不新增只接受 JSON Boolean 的限制。稀疏字段不能重新跑完整 Device parser 并丢掉 hostname、periods、window 或 health。
- 根 limits.updatedAt 只进入原始状态，typed Limit.updated 仍来自每个 provider 的 updatedAt／checkedAt；limits 不是对象时不能凭空创建。freshness 中的新 provider、业务 periods、revision 和 historyPreview 均继续被忽略。
- 即使可见 typed 字段相同，原 accept、savedAt／live 与缓存排队行为仍执行。freshness 基准只属于本次 SSE generation，重连首个 full frame 前不能借用 VM 的旧 Snapshot；full typed parse 失败后不能错误回退。原非对象设备形状的异常行为也须保留。

先继续使用 `StreamMerge` 作为差分 oracle，补引用复用断言与 materialize gate 下的认证／后台／连接切换／新 sequence 回归，再运行完整大来源和配对测量。不要为取得 slim metadata 在 producer 与 consumer 各解析一次完整正文；需要最终只含正文和索引的 full 队列时，应在既有严格投影扫描中收集必要元数据。

验证要求：普通 full stats 与 freshness 字段重排差分相等；未知设备不插入、设备顺序不变；limits 更新时间与 stale 状态正确；cold VM/widget 恢复相同计数、费用、未定价与 freshness；full stats 仍逐条保留 allTime 明细和模型索引；401、换 binding、后台、正常 EOF 的最后帧和排队保存保护不退化。给 freshness 工作量增加断言：不重新构造全部 Period/UsageRow、不重算全局模型索引、不重复准备静态列表。

## 阶段 C：把显示派生变成可复用的有限工作

优先顺序为 alias resolver → 隐藏首页 source gating/复用 → 会话静态表与分页 → Projects/Tools/Models 缓存。每个小步都有结果差分与工作量断言；确认 CPU/分配收益后再做下一步。

- 根据 ModelNames、ordered observed IDs 和对应可信 revision 构造一次有界 resolver，复用显式 one-step alias、canonical 选择与 16,384/4,096 截断规则；不建立无界进程级模型缓存。off/duplicates/prefix、大小写/分隔符、canonical tie、device-only/cost-only、Long.MAX_VALUE 与精确小费用均与旧实现逐项相等。
- 首页 Sessions 关闭时 source/recent 调用为零；开启后每次使用量输入变化只生成一份 month/today source。保留 cap 外仍 running 的会话，不能为了五行展示预先截断来源。时钟变化只更新需要的状态。
- 会话静态行与排序使用数据、别名、标题策略、本地日期/时区和语言作输入，缓存 review grouping、matching 与 max；空查询不复制整表。时间/费用排序键只解析一次，按同样优先级排序。实时 running/context/cache/age 单独使用当前时钟；同一天普通 tick 不重建 raw/grouped，午夜、时区、标题撤回、归档、费用/计数或别名变化必须失效。
- 分页仅复制 `[start,end)` 的最多 100 行；首尾、越界、查询缩短后的 page clamp 和 review 返回保持现状。打开 Sessions/Projects 的 allTime 仍能访问全部来源记录，不以分页截断原模型。
- Projects 保留 Unicode 归一化、同名汇总、计数/费用/未定价、客户归属、费用优先排序与 fallback sessions 行为。预解析 BigDecimal 排序值并缓存纯显示结果；改变期别、源数据、语言或标签时重新准备。Tools/Models 的 alias、ranking 和 barMax 同样每组输入只做一次，避免逐可见行求整表 maximum。
- 如果测量显示全表准备仍超出交互预算，将其移至一个可取消、只保留最新输入的后台准备任务，Main 只发布对应数据 revision 的结果。新增 generation/revision fence，测试快速换期、查询、连接、标题撤回时没有旧表提交。迁移前消除 `SessionListDisplay` 单例 NumberFormat 等可变 formatter 的跨线程共享，按任务或线程持有；不能简单把现有函数并发调用到后台。

保留 headline 的帧状态隔离、数值中断动画、最后一位与 reduced motion 行为。缓存只用于不随动画帧变化的静态数据；不能改成把所有动画帧推进重新放回整页状态。

## 阶段 D：仅在仪表支持时收紧分配

若阶段 B/C 后仍接近 192 MiB 上限或 trace 显示投影/解析的大量短寿命容器导致频繁 GC，再处理已定位的路径列表、Pair/List 中间表、预览 JSON 序列化后重解析以及 widget 完整恢复。路径栈只在候选被接受或前移时复制，仍按原 collector 位置排序，重复 key 与 16,384 边界差分继续通过；直接循环保留对象插入顺序、精确 Long 与费用校验。

不要因为 cache report 显示 widget 小投影快就跳过真实 widget load 测量，也不要在没有 active widget 的场景凭空归因 widget。若必须引入只读 widget snapshot 投影，完整 GCM/gzip/EOF 认证完成前仍不得发布，不能触发迁移写入或网络。

## 第 49 阶段验收与交付

交付按构建摘要与同一来源摘要配对的 baseline/candidate 报告、分阶段耗时/调用量/线程、GC 与存活/分配数据、队列和大表示并存上限，以及完整业务断言。性能变化只归因于 trace/分阶段数据确实覆盖的调用，不能用报告生成时间或测试动作时间推测原因。

在同一 192 MiB 预算下，完整来源的计数、会话/项目、费用/未定价、模型/别名、设备窗口/health、全部日期、固定期别、标题策略、freshness、冷热缓存与 widget 必须相等；非法 UTF-8、越界、截断、重复 key、篡改仍拒绝。继续跑连续 full frames、连续 freshness、保存 gate、Main 导航/查询/快速换期、后台与换连接、401/403、普通 EOF 最后一帧；无无限队列、无旧 generation/binding/auth 工作提交。

性能验收至少证明：隐藏模块不做全表派生；同输入全局 resolver 只构建一次；同一天普通时钟 tick 不重建静态大表；freshness 不重新解析完整业务模型；分页临时列表最多 100 行；对应 Main 工作和分配量在配对重复样本中下降。报告所有峰值与失败，不以平均值掩盖秒级卡顿。若仍有秒级主队列延迟或持续贴近堆上限，只能报告已完成的局部优化与剩余瓶颈，不能标成“容量性能问题已解决”。

实际手机、Lucky 公网 HTTPS、默认首页模块和正常数字动效作为单独路径收据。未运行的范围保持未验证；模拟器 192 MiB 样例成功仍不代表任意设备规模或物理手机流畅。
