# Hub 数据容量实现与验收

**状态：0.2.48-dev / code57已封存，294单元、203普通API36及另外7项公开容量专项通过。** 大响应HTTP/gzip、设备历史、SSE、真实加密联合缓存、实际ViewModel与HubScreen、连续大帧及后台冷恢复分别取得当前应用包的通过记录。当前开发包更新为48；32MiB是当前拒绝越界的保护，样例通过不能据此认定任意同大小载荷安全或流畅，整体目标仍未完成。

实施范围与原始容量缺口见 [HUB_CAPACITY_PLAN.md](HUB_CAPACITY_PLAN.md)。业务参照固定为桌面 v0.68.0，提交 `5d2db368d8313415763860d594de00e46a663418`。客户端保持只读职责，未修改生产 Hub 服务，也未增加采集或远端写入。

## 候选容量与读取语义

| 边界 | 当前候选实现 | 计量对象 |
|---|---:|---|
| 新 stats HTTP 路径 | 32 MiB | HTTP 解压后的 UTF-8 输入；gzip 的 Content-Length 不替代此检查 |
| history 与 devices HTTP 路径 | 32 MiB | 解压后的原始输入，包括最终未保留的设备字段 |
| 新 SSE 路径 | 每事件 32 MiB | 原始事件字节，包括字段前缀、注释、未知字段和行结束符；逐事件重置 |
| SSE 事件类型 | 128 字节 | event 字段去掉至多一个前导分隔空格后的 UTF-8 字节 |
| JSON 嵌套 | 128 层 | 保留和跳过的 JSON 分支均受检查 |
| 缓存磁盘记录 | 10 MiB | 版本头、IV、密文和 GCM 认证标签组成的完整文件 |
| 缓存展开记录 | 32 MiB | 解密、解压后含全部字段的 JSON envelope，包含 JSON 字符串转义开销 |

32 MiB 为 `33,554,432` 字节，10 MiB 为 `10,485,760` 字节。输入、保留 stats JSON、缓存展开记录和最终磁盘记录是独立边界，不能用其中一种大小证明其他边界已满足。合法设备原始输入也不保证聚合响应或联合缓存一定落在客户端范围内。

[HubInput.kt](../app/src/main/java/dev/bing/tokenmonitor/HubInput.kt) 在读取中按实际字节检查容量和请求有效性。[HubHttp.kt](../app/src/main/java/dev/bing/tokenmonitor/HubHttp.kt) 的内部 `readStats` 返回投影正文和本地产生的模型索引；`readDeviceHistory` 将 retained-history 投影前移到传输读取处。原有 `read(...): String` 保留 8 MiB 兼容入口。原有 `HubStream.open` 保留 8 MiB、保留设备明细的兼容入口，应用的新路径使用 `openProjected`。

[HubSseFramer.kt](../app/src/main/java/dev/bing/tokenmonitor/HubSseFramer.kt) 使用固定 8 KiB 输入块、一个有界 payload Buffer 和有界事件类型缓冲，不先创建整条 data 行字符串。支持 LF、CRLF、裸 CR、多 data 行、跨块 UTF-8 和位于 data 之后的 event 字段；空 event 默认 message，空 data 字段可以形成空数据事件。未知事件同样完成状态清理，未完整结束的 EOF 事件不发布。完整事件校验后才同步交给消费者，回调返回后输入失效，未读数据被丢弃；超限错误不带正文。

**裸 CR 的计量定义：** CR 本身立即结束行；终止空行的 CR 会立即发布事件，不等待后续输入或 EOF。非空行 CRLF 的 CR 和 LF 均计入当前事件；最终空行 CR 后的可选 LF 仅被忽略，排除这最多一个字节的分隔开销。LF 或裸 CR 终止空行时，其完整终止字节计入上限。

## 数据保留与模型索引

[HubJsonProjection.kt](../app/src/main/java/dev/bing/tokenmonitor/HubJsonProjection.kt) 只裁剪 `devices[*].periods.*.sessions` 和 `devices[*].periods.*.projects` 两类重复设备明细。顶层所有周期的 sessions 和 projects 完整保留，包括 allTime；设备汇总计数、费用、模型与工具映射、时间窗口、health、capabilities、throughput、revision 和 omission 字段继续保留。严格 UTF-8、JSON 完整性、重复字段和嵌套深度均在输入阶段检查，跳过的内容仍受输入边界约束。

计数保留为原始整数和 Long，复制数值时不经 Double 中转；费用保留原始十进制语义，未定价量继续独立展示。不会为适配容量而删除顶层会话、项目或日期，也不会重新计算 Hub 费用。原生解析复用投影后的 JSON root。

被裁剪分支可能含只有该分支出现的模型 ID。投影器按设备位置、周期、sessions/projects 分支和原 collector 字段优先级记录位置，在 [ObservedModelIds.kt](../app/src/main/java/dev/bing/tokenmonitor/ObservedModelIds.kt) 原本遍历该分支的位置合并；不会把读取过程中发现的 ID 直接追加到结果末尾。索引最多保留 16,384 个去重 ID，单 ID 长度最多 256 个 String 字符，继续执行既有顺序和长度规则。

该索引仅由本地读取器生成，不信任 Hub 正文中的同名内部字段，也不向 Hub JSON 追加内部字段。最终 observedModelIds 可选写入受 GCM 认证的缓存 envelope；读取时校验长度、去重和规范形式。null 表示旧缓存需要按旧正文重新收集，明确空列表表示结果确实为空。联网与冷恢复使用相同的最终模型列表，以保持模型别名分组。

[DeviceHistoryStream.kt](../app/src/main/java/dev/bing/tokenmonitor/DeviceHistoryStream.kt) 流式保留所有 daily 记录、perClient/perModel、组件、费用与未定价字段，跳过设备的其他大字段。available=false、缺失历史与明确空 daily 的区别继续保留；selected daily 对象要求有效日期。设备 retained-history 仍沿用既有 daily 投影，聚合 history 保留完整 daily 与 monthly；没有按日期或模型数量截断。

## 后台处理与生命周期

[HubStream.kt](../app/src/main/java/dev/bing/tokenmonitor/HubStream.kt) 在网络回调线程完成事件读取、投影与 freshness 合并，保留完整统计明细和本地模型索引。[HubViewModel.kt](../app/src/main/java/dev/bing/tokenmonitor/HubViewModel.kt) 使用容量为 1 的解析入口 channel 和单个消费者；生产者发送时等待空位形成背压，不为每帧启动独立解析协程。原生 Snapshot 解析在 Default，HTTP、冷启动模型读取与缓存编码在后台执行，Main 提交前再次检查前台、generation、请求和认证状态。

SSE 缓存保存使用一个 conflated channel 和单个保存 worker：一份本地写入进行中时，最多保留一个待保存的最新记录。**普通 EOF 重连继续复用同一保存队列和 worker**，不能因网络 generation 更换而丢掉已经接受、尚在慢保存之后等待的最终帧。普通 EOF 的消费者先排空已完整读取的帧；网络重连不等待磁盘写入。保存入口仍以 foreground、binding、auth 和 cacheSequence 校验有效性，并由 storageMutex 串行进入存储。

离开前台、换连接、网络范围改变和认证失败仍停止网络与旧消费者；暂停、绑定切换和 401 取消待保存队列与 worker。已进入同步 AtomicFile 写入的操作仍按原子提交或回滚收尾。queued history/save 在认证失败后不能再进入存储，保存完成不能覆盖认证错误。requestToken、bindingToken、cacheSequence、historySignature 与历史读取回执的隔离继续保留；网络回执和自动读取预算不写入缓存。

这些队列边界限定积压数量，不能替代内存峰值与取消延迟测量。输入缓冲、保留正文、JSON root、原生Snapshot和此前可见模型仍可能在转换期间同时存在。下方实际ViewModel与界面测量已完成；Java采样接近192MiB上限，还需按性能计划定位并优化，不能把32MiB守卫当作普遍安全容量。

## 缓存格式与认证

[HubStore.kt](../app/src/main/java/dev/bing/tokenmonitor/HubStore.kt) 新写入 v2，顺序为流式 envelope → 单个 gzip member → AES-GCM → AtomicFile。使用版本对应的 AAD 和既有 Keystore key；显式调用 GCM doFinal，gzip 尾部与认证标签全部写入成功后才 finishWrite，失败执行 AtomicFile 回滚，保留前一个有效记录。磁盘密文和展开记录分别计量，不先创建完整转义 envelope 或整个明文 byte array。

v1 继续读取，不因读取旧缓存而迁移写盘。v2 在读取时校验单个 gzip member 的头、CRC、ISIZE 和结尾，拒绝额外 gzip member、尾随数据、截断、篡改、未知版本和解压越界。END_DOCUMENT 检查将输入读到认证和 gzip 的完整结尾，全部校验成功后才返回记录、构建保留模型；不发布未认证或部分恢复的结果。Widget 保持本地只读职责，不触发缓存迁移写入。

historySignature 保留 null、非空 revision 与显式空字符串的区别。observedModelIds 与其他可选详情放在同一个认证 envelope。GCM 提供者自身的缓冲，以及联合正文和模型的同时保留，仍属于低堆验收范围。

## 公开 fixture 与业务参照

[公开来源说明](../evidence/hub-capacity/reference/README.md)和 [capture-hub-capacity.cjs](../tools/capture-hub-capacity.cjs) 固定原始 Hub JSON 输入读取器、归一化、聚合、历史和 SSE 格式函数。设备输入经原始 1 MiB 读取限制，24 份均合法；源文件指纹与固定提交核对。时间固定为 `2026-10-09T08:00:00.000Z`，设备、会话、项目、模型与日期均是公开合成数据；大正文仅在运行时内存中生成。

48 的 [fixture 服务回执](../evidence/hub-capacity48/focused/source-server-v1/capacity48-fixtures.json)复放同一 source oracle，并通过 loopback-only 服务提供 stats、gzip stats、history、原始 devices、SSE 和小型状态响应。测试客户端仅对自己的请求改写合成端点，不放宽生产地址或局域网权限规则；不使用生产 Hub、凭据、正文或服务配置。

| 公开场景 | 16 设备传输边界 | 8 设备联合缓存边界 |
|---|---:|---:|
| 每台每周期会话与项目 | 300 / 300 | 180 / 180 |
| 每周期顶层会话与项目 | 4,800 / 4,800 | 1,440 / 1,440 |
| 模型 | 320 | 160 |
| 历史日与月 | 31 / 2 | 370 / 13 |
| 原始 stats UTF-8 | 21,667,533 B | 6,579,790 B |
| 投影 stats UTF-8 | 10,962,285 B | 3,356,446 B |
| 原始 history UTF-8 | 933,702 B | 5,553,856 B |
| 原始 devices UTF-8 | 11,591,495 B | 7,572,641 B |
| 原始 SSE snapshot 完整事件 | 21,667,634 B | 6,579,891 B |

每个会话使用 `5,000,000,123` tokens、`0.015625 USD` 及 17 个未定价 tokens；today、month、allTime 使用 1、9、保留日数的倍数。逐条检查全部 session/project 身份、计数、费用、未定价、模型和客户归属，历史逐日逐月检查所有模型，未使用浮点容差。该参照不扩展原始 JavaScript 安全整数范围，也不证明任意设备数量都能读取。

## 当前应用包的7项公开容量检查

最终三组回执的应用APK均为`adc71a866aa8b0c5f661a16fc37e151c65ec77441361cd7db137fe4a93c30307`；各轮测试APK、执行日志与小型数值报告另有独立指纹。以下数量不纳入普通安卓全量计数。

| 回执 | 通过数 | 当前包覆盖 |
|---|---:|---|
| [传输](../evidence/hub-capacity48/focused/transport-final/run-summary.json) | 3 | 两组完整HTTP与gzip解压后SHA/字节、原生全部业务、设备历史与WEEK/7D/30D、SSE完整帧/freshness及取消 |
| [联合缓存](../evidence/hub-capacity48/focused/cache-final/run-summary.json) | 2 | 两组完整数据、真实v2新实例冷读取、Widget复用已解析Snapshot投影、零写入/密文不变、精确费用与模型别名 |
| [实际VM与页面](../evidence/hub-capacity48/focused/viewmodel-final/run-summary.json) | 2 | 实际HubScreen周期/4800会话分页/项目与设备/完整历史Dashboard，真实Activity STOP、后台新VM冷恢复零网络；4完整大帧与慢保存/后台排队取消 |

[HTTP报告](../evidence/hub-capacity48/focused/transport-final/transport-http-report.json)验证两组原始与gzip解压输入SHA、完整原生业务及低1字节的解压边界拒绝；没有第二棵完整原始JSON基线树。[历史报告](../evidence/hub-capacity48/focused/transport-final/transport-history-report.json)检查496/2960设备日、31/370聚合日、2/13月、10560/61280历史模型归因和三个快捷周期。[SSE报告](../evidence/hub-capacity48/focused/transport-final/transport-sse-report.json)检查完整业务、freshness和取消后源流计数0。

所有当前容量测试均在`Runtime.maxMemory=201326592`字节，即192MiB下运行，未使用largeHeap或缩小fixture。

| 真实冷缓存报告 | retained stats UTF-8 | v2密文 | 真实cold Store读取 | Widget已有模型投影 | 10ms采样Java使用量最高观察值 |
|---|---:|---:|---:|---:|---:|
| [16设备](../evidence/hub-capacity48/focused/cache-final/capacity-cache-transport-16-devices-public.json) | 10,962,285 B | 340,217 B | 513.77 ms | 1.89 ms | 200,827,616 B |
| [8设备/370日](../evidence/hub-capacity48/focused/cache-final/capacity-cache-combined-cache-8-devices-public.json) | 6,579,790 B，原始未投影 | 298,750 B | 308.99 ms | 0.04 ms | 86,118,912 B |

第二组刻意独立保留原始stats6,579,790字节和history5,553,856字节，两者分别小于旧8MiB但联合超过旧10MiB；使用真实压缩加密后新实例完整恢复。第一组保留顶层全部明细、仅移除设备重复分支。真实Store cold load一次，之后Widget仅读该同一认证记录的已有Snapshot；这两个计时分开记录，不能把Widget投影时间当完整磁盘读取时间。

[实际UI报告](../evidence/hub-capacity48/focused/viewmodel-final/capacity-viewmodel-ui-public.json)全部导航和业务、496设备日、31日/2月与后台冷VM恢复通过。Activity STOP到源流关闭1170ms，后台新VM恢复668ms，零网络读取且密文不变。**大数据交互性能仍有明确缺口**：主线程心跳最大延迟2513ms，Java使用量采样接近192MiB上限；session+翻页4347ms、project+滚动/点击2665ms、device+滚动/点击2003ms为组合测试动作计时，不是单函数成本或FPS。[慢保存burst报告](../evidence/hub-capacity48/focused/viewmodel-final/capacity-viewmodel-burst-public.json)观察5个不同修订、2次有效写入，磁盘阻塞期间仍提交；后台关闭525ms，之后排队保存取消且状态/密文稳定，主线程心跳最大延迟5ms。

采样以`totalMemory()-freeMemory()`分别读数，略高于max的瞬时组合值不能作为硬堆越界证明；应按“接近上限”解释。native分配和PSS另行保留于各报告，不能简单相加当Java堆。心跳延迟没有精确覆盖函数区间，不能直接归因给JSON或绘图；按[后续性能计划](HUB_CAPACITY_PERFORMANCE_PLAN.md)加阶段时标、分配/GC与Main工作证据后优化。当前32MiB边界仍是应用拒绝越界的保护，以上样例通过不证明任意同大小载荷安全或流畅。

[早期9项Store回执](../evidence/hub-capacity48/focused/store-v2/run-summary.json)和[早期16项历史回执](../evidence/hub-capacity48/focused/history-regression/run-summary.json)保留各自当时包的范围；新增的第10项跨加密/解压块检查和第17项普通EOF最终缓存检查，由最终普通完整回归取得当前包证据，不把早期数量改写为最终结果。

## 联合缓存失败回执与诊断

大联合缓存的失败记录完整保留，不能把已写出 v2 文件或测试进程退出码为 0 写成缓存容量通过。

| 轮次与回执 | 实际结果 | 当前解释与范围 |
|---|---|---|
| [第一轮](../evidence/hub-capacity48/focused/cache-manual-first-failure/run-summary.json) | 2 项均失败，在缓存保存前的业务前置断言失败 | 测试错误假设 Device.periodDetails 包含全部设备周期；这轮没有建立生产缓存容量结论 |
| [第二轮](../evidence/hub-capacity48/focused/cache-manual-second-failure/run-summary.json) | 两组完成来源业务检查与 v2 写入，均在 cold-widget-store-read 失败 | 当时原因未确定，回执保持原样；整次冷恢复与 Widget 检查均未通过 |
| [第三轮诊断](../evidence/hub-capacity48/focused/cache-manual-address-failure/run-summary.json) | 两组仍失败，明确为 IllegalArgumentException | 展开 envelope 的字段已读取，构建 SavedHub 时缓存中的 HTTP loopback 地址被既有 HubAddress 私有 IPv4 规则拒绝；诊断纠正第二轮的未知原因，没有建立生产加密格式故障 |

当前调用顺序在 readEnvelope 构建 SavedHub 时执行地址规范化，随后 load 才检查 END_DOCUMENT。因此该地址失败轮次不能当作完整结尾校验通过的证据。修正仅将**测试缓存绑定地址**改为保留的 HTTPS `.invalid` 地址；传输仍使用测试 loopback 服务。生产地址校验未放宽。修正后第四轮2项完整冷恢复通过，见上方cache-final；完整失败轮次与原因澄清均保留。

第三轮失败时，16 设备的已写 v2 文件为 340,212 B，8 设备为 298,742 B；这些是失败轮次中的磁盘大小，不是完整缓存验收。该轮按 10 ms 采样的 Java 使用量最高观察值分别为 201,319,136 B 和 62,722,592 B，均不代表修正后的完整冷恢复峰值。16 设备观察值接近 192 MiB 上限，需要完成后续联合路径测量，不能以很小的压缩文件认定低堆安全。

## 失败保留、最终回归与剩余范围

实际VM/UI首轮burst通过，页面导航和STOP后安静期已检查，但测试在STOP后取Compose Rule的Activity引用失败，因此没到冷恢复。保持完整fixture，仅修正测试持有原生Activity引用并释放旧Compose collector后，第二轮2项通过。[首轮失败记录](../evidence/hub-capacity48/focused/viewmodel-first-failure/run-summary.json)和报告保留，没有归为生产生命周期失败。

[最终封存记录](../evidence/hub-capacity48/final/build-and-package-checks.json)绑定完整运行27249成功、294单元、203普通API36、原始JUnit/日志、应用与普通测试包双哈希，以及另外7项同应用包容量回执。10项v2检查（含512KiB跨加密/解压块）和17项历史模型检查（含慢保存后的普通EOF最终记录）均通过。Lint96 Warning/1 Hint/0 Error；663对资源一致；新Gson许可已补齐，三个旧附件保持、许可说明更新；签名/权限同47，57个APK归档与前五阶段870份证据指纹匹配。普通测试包排除24个live/permission/perf/capacity可选类。仅清理自有模拟器与测试资料，未测实体手机/Lucky/旧API，不将旧真实Hub/升级或Window报告换绑本包。

后续仍需优化大UI的内存和主线程成本，运行API26–28兼容路径，以及实体手机LAN/离家HTTPS/蜂窝/VPN/后台/锁屏/省电与实际Lucky公网TLS/SSE。同样的数据规模和完整业务必须保留，不能通过扩大堆、删除顶层明细、截断历史或减少模型实现性能通过。用户已说明手机与Lucky目前不可用，不重复请求或探测未知地址；可独立完成的性能工作继续推进，整体目标不标完成。
