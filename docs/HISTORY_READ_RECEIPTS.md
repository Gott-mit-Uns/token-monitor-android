# 历史读取与有限重试合同

适用：Android `0.72.0-01` / code76；检查时间：2026-10-17；来源：本分支基线中的 HistoryReadCoordinator / HubViewModel 及旧版本实施记录。历史运行结果不在本文持续追加，见 [VALIDATION](VALIDATION.md)。

固定桌面v0.68.0，提交`5d2db368d8313415763860d594de00e46a663418`。原始app.js的loadHomeHistory与homeOverview.js的结果/重试函数使用本次请求结果，以及开始时冻结的预览状态；forceHistory在stats成功后重置有限周期。原始捕获见原版本本地历史函数证据（evidence/theme-history-actions/reference-history.txt，本checkout未提供），实施前约定见[计划](https://github.com/Gott-mit-Uns/token-monitor-android/blob/cfab5438ed5dac6537c68affb385517ccfa44c0e/docs/archive/history/HISTORY_READ_RECEIPT_PLAN.md)。

既有请求拥有连接修订+业务签名、cycleId、requestId和实际start次数。旧非空内容只负责显示，不能证明新手动读取成功。historyRenderRevision仍是接受并应用数据的显示事件，与请求回执分别管理。

- 本次成功非空，或成功空且开始时没有日预览：接受响应并停止自动重试。
- 本次失败，或成功空且开始时有日预览：保留旧显示；有预览时等4秒，经原串行队列继续，最多首次加三次。
- 无预览的实际失败不自动重试。同目标取消即使无预览也可恢复等待，但不退还次数；legacy空签名且已requested的抑制规则优先。
- 详情刷新和加载历史只有实际接纳start才新建周期、计第一次。loading、后台或鉴权拒绝不消费或恢复预算。
- HOME刷新只在stats成功后发布QUEUED；主趋势普通刷新保持只读stats。QUEUED仍由可见页面的原串行队列派发，不直接请求网络。

## 生命周期、绑定与保存

预算由ViewModel持有；页面和Activity重建只重建等待时点。隐藏或后台取消等待，恢复后重新等完整4秒。相同Hub地址切换、连接重新验证保持预算；新绑定、修订或legacy业务签名改变才换目标。freshness、重复终态和其他API的loading不改变期限。

后台、网络切换、修订变化和SSE401/403结束匹配的活动请求；提交前复核ticket、绑定、签名和认证。SSE与history保存入口均在存储锁内复核有效性，认证失败后排队的记录不能通过另一入口写入。网络结果已接受但本地保存失败，保持RESOLVED并显示保存错误，不重新下载。

响应尚未接受时，后台取消并拒绝迟到结果。响应已接受且本地原子保存正在执行时，后台可以完成这份记录，不新增网络或退还次数。已经进入原子写入的操作，与仍在排队且尚未通过有效性检查的保存有不同边界。

缓存增加可选historySignature，与body同处现有AES-GCM/AtomicFile记录，不改变v1版本。不保存请求身份、预算或timer。冷启动只有解析成功且签名匹配才给CACHED；旧记录仍可显示，首次有效读取后补元数据。显式空签名与缺失元数据分开。

SSE非认证断开的重连timer若遇到长时间loading，47会在读取/保存释放后恢复连接；尚未到期的重连等待继续保留，不另开history重试协程。
