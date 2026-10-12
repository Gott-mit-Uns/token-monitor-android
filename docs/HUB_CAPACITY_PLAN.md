# Hub 数据容量修复计划

状态：0.2.48-dev / code57已封存，当前开发包更新为48。完整公开容量路径7项、294单元与203普通API36通过，见[HUB_CAPACITY.md](HUB_CAPACITY.md)。完整UI主线程心跳延迟2513ms、Java采样接近192MiB，按[性能计划](HUB_CAPACITY_PERFORMANCE_PLAN.md)继续定位优化，32MiB拒绝边界不是普遍安全容量。固定桌面v0.68.0提交`5d2db368d8313415763860d594de00e46a663418`；不修改Hub、不增加采集或远端写入。以下保留实施前来源审查和设计约束。

第46阶段后续的实施前只读复核确认：HTTP整包8MiB边界、SSE整行/整树处理、Main线程SSE与冷启动模型解析、v1联合缓存10MiB边界存在。Dashboard的366×80绘图诊断只覆盖已经进入内存的数据，不能替代传输或缓存容量验收；现有图表后台准备和模型别名投影应沿用，不重复实现。

## 已复现的独立边界

以下两组精确字节数是前期只读合成审查的记录，使用固定原始源码的聚合与历史函数、公开设备 ID、模型名及模拟日期；不是第46阶段重新执行的结果。本次复核未定位到旧生成器或独立容量报告，现已新增可复放生成器，结果见下一节；不以旧数字证明当前已测容量。大 payload 不常驻仓库，报告只保存计数、字节数和业务断言。

- **16 台合法设备**：每周期每台 300 个独立会话，最大单设备输入 247,489 字节，小于原始 Hub 1 MiB 接收上限。原始聚合 stats 为 15,713,508 字节，SSE 单条数据行为 15,713,587 字节，超过安卓 8 MiB 读取上限。每台输入合法不保证聚合后响应仍小于限制。
- **8 台设备、370 天历史、每台 20 个不同模型**：最大单设备输入 721,098 字节；stats 为 7,847,152 字节，history 为 4,862,129 字节，分别可落在 8 MiB 内。但同一 SavedHub 中 JSON 字符串二次转义后，明文记录为 14,164,472 字节，计入 v1 版本、IV 与认证标签后为 14,164,501 字节，超过独立的 10 MiB 保存上限。

这些结果证明真实业务形状可以触发边界，不证明任意设备数量均能读取。后续容量测试复用运行时生成器，记录计数与字节数，不保存原始大 payload。

## 第46阶段补齐的可复放来源证据

[公开生成器](../tools/capture-hub-capacity.cjs)直接执行固定源码的原始JSON输入读取器、设备归一化、聚合/历史函数及getStats/getHistory/sseFormat函数体。16个源文件指纹与固定提交逐字节核对；24个输入均被原1MiB读取器接受。ROOT独立执行 `--check`，两份小型报告逐字节复放通过。完整方法和限定见[来源说明](../evidence/hub-capacity/reference/README.md)、[计数报告](../evidence/hub-capacity/reference/source-capacity.txt)。

| 本次公开场景 | 最大单设备输入 | stats / history | SSE单条data行 | 联合v1缓存最小密文字节 |
|---|---:|---:|---:|---:|
| 16设备，每周期每台300会话、20模型、31日 | 411,553 | 21,667,533 / 933,702 | 21,667,616 | 25,183,270 |
| 8设备，每周期每台180会话、20模型、370日 | 758,104 | 6,579,790 / 5,553,856 | 6,579,873 | 13,390,945 |

每个周期逐条验证完整session/project及模型归属、精确计数/费用/未定价；历史逐日逐月验证日期和每个模型。第一组复现8MiB读取边界；第二组stats/history分别小于8MiB，但联合记录超过10MiB。v1字节数来自空凭据字段的内存envelope加29字节认证封装开销，未运行Android加密或网络路径，不属于低堆容量验收。

**新证据否定了下文“每份保留JSON 8MiB”的候选值**：第一组必须保留的顶层periods子树本身已有10,813,468字节，尚未计入任何devices字段。只裁剪设备重复明细仍无法覆盖该合法场景；后续必须据低堆实测调整保留上限或解析结构，不能删除顶层会话/项目来适配旧限制。

## 最小实现范围

路径均相对 `app/src/main/java/dev/bing/tokenmonitor/`。

| 文件或组件 | 改动与必须保留的内容 |
|---|---|
| `HubHttp.kt` 与新增有界 JSON 投影 helper | 在 `ResponseBody.source()` 读取过程中投影 stats，避免先缓存完整响应。只丢弃 `devices[*].periods.*.sessions/projects`；顶层所有 `periods.sessions/projects` 完整保留，包括 `allTime.sessions`。 |
| `HubStream.kt` 与新增字节型 SSE framer | 替换整条 data 行读取；逐块处理事件，并共用 stats 投影。按 UTF-8 字节计量，保留多 data 行、freshness 合并、事件顺序与取消语义；一个事件完整通过校验后才发布。 |
| `FixedPeriods.kt` 与 HTTP devices 路径 | 把现有 retained-history 投影前移到传输读取处，避免完整 `/devices` JSON 树。保留 daily、perClient/perModel、组件字段及 unavailable 与明确空结果的区别，不截断日期。 |
| `HubData.kt` / `ObservedModelIds.kt` | 保留设备计数、费用、模型与工具映射、窗口、throughput、health、capabilities、revision 与 omission。被裁剪区域中的模型 ID 可能参与别名分组，需比较投影前后结果；必要时流式收集有界的模型 ID 索引。 |
| `HubViewModel.kt` | 将 SSE 投影与解析移出 Main，采用串行、有限待处理帧的处理方式；冷启动 snapshot/history/设备历史等模型解析也移出 Main。提交前继续检查 foreground 与 generation；换连接、离开前台、取消请求后不接收旧结果。保留 requestToken、bindingToken、cacheSequence、storageMutex 的隔离。 |
| `HubStore.kt` | 新记录采用 gzip 后 AES-GCM 的 v2 格式；流式编码 envelope，避免再生成一份巨大的转义明文。保留 AtomicFile 回滚、跨实例锁与 widget 本地只读路径。 |

计数保留精确 Long；数值复制不得经 Double 中转，费用保留原始十进制语义和未定价数量。不得重算 Hub 费用、丢弃顶层明细或用部分数据伪装完整成功。

`ObservedModelIds.collect` 当前扫描设备 sessions/projects；若投影时收集额外的有界模型ID索引，该索引必须同时参与联网展示和缓存恢复，验证最终别名分组保持一致。现有 Widget 在 IO 读取完整 SavedHub，必须纳入大联合缓存的低堆验收；只有实测表明它是瓶颈时才引入内部 snapshot 读取投影，保持只读本地职责。

原始 `statsPublisher.js:85–111` 已裁剪设备重复明细，但桌面另有 `stats:allTimeSessions` IPC。Hub `server.js:370–382` 没有对应按需会话或分页接口，安卓不能照搬删除顶层 all-time 会话。`http.js:19–31` 的 GET JSON 支持 gzip，SSE 没有；网络压缩不能替代解压后容量检查。

## 缓存兼容与候选上限

- **v1 继续读取**：保留现有 Keystore key alias；旧记录校验、解密、解析成功后才可在后续成功保存时升级，不因迁移失败覆盖有效缓存。
- **v2 新写入**：版本与 AAD 对应，先压缩后加密；磁盘密文、解压后的记录分别检查上限。拒绝篡改、截断、未知版本及解压越界，失败仍保留旧 AtomicFile 记录。
- **待验证候选**：原始解压输入32MiB、缓存密文10MiB、缓存展开记录32MiB；旧的“每份保留JSON 8MiB”已被上述来源样例否定，需要重新定稿。48已实施上述独立拒绝边界并验证两组完整样例，但32MiB不能作为任意载荷的已测安全容量；gzip压缩后的Content-Length也不能当作解压后的大小。

原始 Hub 聚合规模没有固定设备总数限制，客户端仍需明确容量范围。最终上限必须结合低堆峰值内存、完整业务结果及取消响应实测定稿，不能简单取消边界或不断放大限制。

## 验收判据

1. 运行时生成上述合法 16 设备与 8 设备 370 天样例，分别覆盖 HTTP、SSE 与缓存的独立失败。新路径须保留全部顶层会话/项目、周期计数、精确费用及完整历史；原始固定源码作为业务参照。
2. 对比投影前后实际 HubParser 与界面结果，覆盖 Long 大计数、小额费用、未定价、模型别名分组、窗口、标题策略、限额与 omission。只比较 helper 字节数不算通过。
3. 通过公开 HTTP/SSE fake 验证 gzip、分块与多 data 行、同版本 freshness、连续更新、401/403、换连接和后台取消。无旧响应提交、无后台读取、无无限帧队列，保持原有串行只读行为。
4. 验证 v1 可读、v2 新实例完整恢复、应用与 widget 跨实例读写、篡改拒绝、不可压缩数据越界、解压上限、失败保留旧缓存。恢复后实际趋势、固定周期、币种和 widget 数值正确。
5. 在低堆配置测峰值分配、卡顿及取消延迟，再确定候选容量。小样例成功、压缩比或测试数量均不能替代完整业务与低堆验收。

现有 `HubStoreTest` 在低堆下会缩小边界样例，不能用其通过证明10/32MiB容量安全。SSE保存使用mutex也不等于帧队列有界；需分别测输入背压、解析提交与保存积压。

手动历史刷新已作为47独立实施并封存，259项单元和192项普通安卓检查通过，见[历史读取回执](HISTORY_READ_RECEIPTS.md)。48容量数据路径已封存并通过7项完整公开样例，性能计划全部验收仍未完成，不能以回执检查替代交互性能结论。

## 实施前补充审阅

32MiB输入仅能容纳已有21,667,533字节stats和21,667,634字节完整SSE事件，不证明联合缓存与低堆安全。已记录的最小v1展开记录分别为25,183,241和13,390,916字节，尚未加入全部可选详情与新索引；该段是实施前记录；48已有实际gzip/v2密文及完整冷恢复，见HUB_CAPACITY.md。输入、保留JSON、联合展开记录与最终密文应分别计量，不能互相替代。

ObservedModelIds有固定遍历顺序与16,384个ID上限，后续别名映射还有4,096上限。不能把设备删掉分支的ID简单附到结果末尾。最小可行方案是在投影时按设备数组位置、周期名和sessions/projects分支收集有序ID，随后在原collector相应遍历位置插回，再执行原去重/长度/总数规则。联网与冷恢复共用最终受认证的有序索引；不信任Hub传入的同名内部字段。差分验收增加字段重排、数字键、device-only、cost-only以及两种数量上限附近的输入。

SSE应采用单一串行消费者与明确有界队列，字节上限按事件重置。事件类型可能位于data之后；LF/CRLF/CR、多data行、跨块UTF-8及巨大注释/未知字段均需流式处理。投影、模型解析、编码放在后台，Main只提交校验完的结果；继续保留47的请求、绑定、前台与认证保护。

v2写入按流式envelope→gzip→AES-GCM→AtomicFile完成，gzip尾部及认证标签均成功后才提交；失败回滚。读取全部校验完成前不能发布结果，Widget不得触发迁移写入。GCM提供者自身可能缓冲数据，是否低堆安全仍须实测。保留47的historySignature，包括显式空值；网络回执与预算继续不入缓存。

建议依次验收业务等价投影、SSE背压和后台解析、v2兼容缓存、最后完整联合路径的低堆定容。如果保留字符串、JSON树、Snapshot与旧模型并存仍无法通过，应改进解析结构，不能截断顶层会话或模型来通过测试。

接口复核建议保留现有`HubHttp.read(...):String`与`HubStream.open(... snapshot:(String)->Unit...)`的兼容入口，新增内部`readStats`、`readDeviceHistory`和`openProjected`。`ProjectedHubStats`携带正文及本地生成的分支模型索引；`HubParser`复用一次保留JSON树，在ObservedModelIds的原遍历位置合并索引后得到最终有序列表。不要在Hub正文追加内部字段，也不读取远端同名索引。SavedHub末尾可追加默认null的observedModelIds，保存最终列表于认证envelope；null表示旧缓存重新计算，空列表表示确实为空。47的historySignature原值（含显式空字符串）继续保留。该接口已接入48并通过完整公开容量样例，性能与实际手机范围继续按HUB_CAPACITY_PERFORMANCE_PLAN.md推进。
