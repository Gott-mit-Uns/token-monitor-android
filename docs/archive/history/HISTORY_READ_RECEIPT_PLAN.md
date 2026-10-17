# 手动历史刷新与请求回执计划

状态：**0.2.47-dev 已实施并封存**。259项单元、192项最终普通安卓检查通过，包含16项实际ViewModel/回环HTTPS/SSE/加密缓存专项及5项实际页面专项。实际变更与证据边界见 [HISTORY_READ_RECEIPTS.md](../../HISTORY_READ_RECEIPTS.md)。下方保留实施前的问题与约定；容量修复仍是独立未实施项，见 [HUB_CAPACITY_PLAN.md](../../HUB_CAPACITY_PLAN.md)。

## 已确认的问题与参照

`HomeHistoryReadPolicy.kt:27–40` 用当前显示的 loadedDays 判定 completed，并用全局 loading 结束推断请求完成。手动刷新失败时保留旧非空历史，会被误判为成功；仅重置预算也无效，下次 observe 仍会看见旧显示。其它 API 的 loading 变化同样不能证明这次 history 请求结束。

`HubViewModel.kt:417` 的 history 读取目前直接替换成功返回值；成功空结果会替换旧显示。`DesktopHubScreen.kt` 的 ChartLoad 和详情刷新直接调用 loadHistory，没有明确手动预算边界。

46候选新增的 `historyRenderRevision` 只在接受 history 并应用到显示时递增，用于独立 Dashboard 的渲染事件；它没有请求身份、预算周期或失败/取消终态，不能代替此处回执。当前 `readPart` 入口也没有 `authBlocked` 硬门槛；SSE 在 history 进行中收到401/403时，须终止匹配的读取身份，并在保存前再次核验权限，防止后到的成功响应越过认证状态。原有 `HistoryRevisionModelTest` 的 history自身401并未覆盖这个并发路径。

固定桌面 v0.68.0，提交 `5d2db368d8313415763860d594de00e46a663418`：

- `app.js:5131–5132` 在请求开始时冻结 requestSignature 和 previewHadDays。
- `app.js:5150–5184` / `homeOverview.js:446–451` 只用本次 fetchedHistory、resolved 与冻结的 previewHadDays 判结果。失败，或空结果且开始时有预览，保留旧 history；成功非空，或成功空且开始时无预览，接受结果。
- `app.js:5172–5182` 最多三次额外重试，每次等待 4 秒；timer 检查目标仍当前且未被后来接受。
- `app.js:6862–6877` 的 forceHistory 仅在 getStats 成功、更新 stats 后重置预算与 timer。它不清旧 history，也不清 homeHistoryRequested。
- `homeOverview.js:420–424` 对已 requested、空 legacy signature 的账户不再次自动读取；不能借预算重置引入零账户轮询。

原始 finally 没有按最新 signature 拒绝旧成功响应。安卓现有修订、绑定与取消拒绝是应保留的平台隔离措施，不能称为原 finally 的逐字复刻。公开原始函数证据见 [reference-history.txt](../../../evidence/theme-history-actions/reference-history.txt)，现有行为待办也见 [THEME_HISTORY_ACTIONS_PARITY.md](../../THEME_HISTORY_ACTIONS_PARITY.md)。

## 最小 API 与状态

保留 HubUiActions 现有无参方法与构造兼容，增加区分用途的入口：

```kotlin
// 无参 loadHistory() 继续表示自动读取。
fun loadHistory(manual: Boolean): Boolean
// 返回值仅表示是否实际进入现有 readPart；被门槛拒绝返回 false。
fun refreshSaved(forceHistory: Boolean)
```

生产 ViewModel 必须实现 accepted 返回值及回执。兼容默认转发可保留既有简单 fake 的编译能力，但新验收 fake 必须模拟真实 start/finish 回执，不能以转发调用次数证明修复。

HubState 末尾只追加默认 null 的 historyReadReceipt，其类型可见性与 HubState 一致。回执最少包含：

| 字段 | 作用 |
|---|---|
| requestId | 唯一本次读取身份，可使用现有 ticket；STARTED 才分配，CACHED/QUEUED 没有网络请求身份，不得复用旧请求身份。 |
| cycleId | ViewModel 内单调递增的预算周期；明确手动边界或新目标才产生新周期。 |
| key | 当前 connectionRevision 加 homeHistorySignature，后者保留 trim revision / 规范 SHA-256 fallback；freshness 不参与。 |
| attempt | 本周期实际 accepted start 的次数，范围 0–4；由 ViewModel 维护，不能因 policy 重建恢复预算。 |
| phase | CACHED、QUEUED、STARTED、RESOLVED、FAILED、CANCELLED、STALE、AUTH_BLOCKED。 |
| previewHadDays | 在实际 STARTED 时冻结；不能使用 finish 时预览替代。 |
| fetchedDays | 本次已解析响应的 daily 数量；失败为 null，不得从旧显示推断。 |

预算、requestId 与网络 timer 不写入 HubStore。为证明缓存确实属于目标，SavedHub 增加默认 null 的可选 historySignature 元数据，与 history/body 在同一 AtomicFile 记录中保存；不改变现有加密记录版本。冷启动仅当该签名与已解析 snapshot 的 homeHistorySignature 一致时构造可信 CACHED 身份。旧记录缺元数据仍可读取、用于旧显示，但不能冒充当前目标 accepted；首次有效读取后补齐元数据。显式空签名与缺元数据 null 区分。

CACHED 只用于当前 key 的初始完成判断。进入明确手动周期后，旧缓存与旧 history 只用于显示，不再完成本轮周期。manual 失败或 preview-backed 空结果保留旧 historySignature；接受新结果才更新它，现有修订/绑定失效清理 history 时也清理该元数据。

## 实际读取与预算规则

1. 自动 root action 调用 loadHistory(manual=false)，返回 true 后关闭本次 dispatch eligibility；attempt 只由 STARTED 回执消费。requested 不能同时再次扣次数。被全局 loading、foreground、认证或会话门槛拒绝的动作不算读取、不恢复预算。
2. ChartLoad 与详情刷新调用 manual=true。实际接受的 manual start 清旧 timer/completed，开启新 cycle，并计为 attempt 1；后续自动读取继续同一 cycle，最多再三次。用户在等待期间再次明确刷新，可以开启新的有限周期；自动 start 绝不能伪装手动边界。
3. HOME 的原 forceHistory 刷新通过 stats 成功分支产生 QUEUED 手动周期，attempt 0；不在点击瞬间或 stats 失败时恢复预算。QUEUED 仅表达读取意图，网络仍由已有 root 串行队列发起。主趋势概览的普通 stats 刷新不隐式读取完整 history。
4. 保留 binding 内已 requested 的状态；forceHistory 不清它。空 legacy key 且已 requested 时不排自动历史读取。显式“加载历史”的单次动作与这条自动规则分开。
5. 只消费与当前 key、cycle、requestId 匹配的新回执。重复回执、同 key freshness 和其它 API loading 变化不得重置 4 秒期限或预算。

| 本次结果 | 显示与缓存 | policy 后续 |
|---|---|---|
| RESOLVED，fetchedDays > 0 | 接受新 history/body，按现有保存路径持久化 | completed，清 timer。 |
| RESOLVED，fetchedDays = 0，开始时无 preview | 接受明确空结果，可替换旧显示 | completed，不重试。 |
| RESOLVED，fetchedDays = 0，开始时有 preview | 保留当前 key 的旧 history/body，不把空响应写成新的有效缓存 | 结束后等 4 秒；attempt < 4 才 eligible。 |
| FAILED，开始时有 preview | 保留旧 history/body | 同上，最多四次实际读取。 |
| FAILED，开始时无 preview | 保留旧显示 | 不自动重试。 |
| CANCELLED / STALE | 不接受或保存该响应 | 清该请求 awaiting；旧目标失效。仍为同目标的取消保留已用预算，恢复资格后重新等完整 4 秒。 |
| AUTH_BLOCKED | 保留旧数据，显示认证错误 | 停止该周期；认证重新验证前不可重读。 |

只保留当前绑定、当前 key 的旧显示；修订或绑定变化继续执行现有失效规则，不把旧连接数据带到新目标。网络成功与本地保存成功分别处理：保存失败沿用 error_details_save，不把已接受的网络结果降成失败并再次下载。

## 串行与平台隔离

- `HubViewModel.kt:371–388` 的 readPart 继续作为唯一读取入口，保留全局 loading；不新增调用网络的 retry coroutine。policy 只产出 ready，root 仍在原 automatic parts 列表中选择一次动作。
- 当前 readPart 只直接检查 foreground/loading/streamEligible。新 history 入口必须加 **authBlocked 硬门槛**；401/403 后不能仅依赖 UI live=false 禁止手动或自动调用。
- 实际 start 冻结 requestToken、bindingToken 与 key；finish 前同时检查它们及当前 requestId。保留 `HubViewModel.kt:389–397` 的修订拒绝，旧响应不能提交数据、写缓存、完成新周期或重置其预算。
- 所有终止路径必须收尾回执。后台取消、网络切换、ticket 失效和鉴权失败不能留下 STARTED 永久 awaiting；统一 helper 只结束自己仍匹配的请求，绝不覆盖后来的回执。
- 继续保留 `HubViewModel.kt:190–205` 的后台暂停、停止 SSE、requestToken 失效，以及 `179–187` 的 bindingToken/cacheSequence/storageMutex 保存隔离。背景期间不读取；隐藏或后台取消等待，不退还已用次数，回前台按同 key 重新等待完整 4 秒。
- 普通同 key 重连不恢复预算；真正的修订/绑定改变才切目标。可信缓存命中可停止初始自动读取；manual cycle 开始后不会再被旧 cachedDays 截停。Activity 重建沿用 ViewModel 回执次数，不能再从 attempt 0 开始；进程重启重新验证本地缓存，不持久化网络 timer。

## 验收判据

全部使用公开 fake 数据，旧 display 必须保持非空才能证明不再依赖 loadedDays。未来新增独立 policy、实际 ViewModel 与 HubScreen 检查，不能只断言 helper 字段：

1. 同 key 旧非空 history，明确手动请求失败；旧内容持续显示，4 秒后读取第二次。随后成功空且 previewHadDays=true，也保留旧显示与缓存并继续重试。
2. 四次实际请求耗尽后无第五次；新的 accepted manual start 恢复一次初始加三次重试。loading/后台/authBlock 拒绝的点击不消费次数、不恢复预算。
3. previewHadDays=false 的成功空结果接受并停止，失败也不自动重试；已 requested 的 legacy 空 key，stats forceHistory 不引入自动轮询。
4. 同 key SSE freshness、重复 finish、其它 API loading 开关不推迟或提前 deadline。另一 API 占用全局 loading 时不并发；释放后仍通过同一 root 队列读取。
5. 非空缓存避免初始重复读取，但 manual start 后缓存不完成周期。Activity 重建保持 accepted attempt 数量；后台立即取消等待/读取，恢复后从完整 4 秒开始。
6. held response 期间改变 history revision / legacy fallback / binding；旧 response 与旧 timer 不接收、不保存、不消费新周期。401/403 后即使 fake UI live=true，ViewModel authBlock 门槛仍禁止读取。
7. stats forceHistory 成功才建立新周期；stats 失败不恢复预算。network history 接受但保存失败时停止网络重试、保留存储错误与旧磁盘记录。
8. 实际 HubScreen 验证旧内容、加载状态、手动按钮、详情返回与调用次数；实际 ViewModel/HTTP fake 验证回执、绑定与缓存，覆盖 start/finish 之间 preview 变化而仍使用冻结值。

该计划仅处理手动历史读取身份与有限重试，不增加采集、服务器写入、后台网络或无界轮询。
