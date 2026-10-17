# 历史读取回执与有限重试

状态：0.2.47-dev / code56 已封存。259项单元、192项最终普通API36检查全部通过，其中包含16项实际ViewModel/HTTPS/SSE/加密缓存专项及5项实际HubScreen专项。应用包、测试包和原始报告均绑定最终成功运行；校验详情见[最终记录](../evidence/history-read-receipts/final/build-and-package-checks.json)。

## 桌面依据与结果接受

固定桌面v0.68.0，提交`5d2db368d8313415763860d594de00e46a663418`。原始app.js的loadHomeHistory与homeOverview.js的结果/重试函数使用本次请求结果，以及开始时冻结的预览状态；forceHistory在stats成功后重置有限周期。原始捕获见[历史函数证据](../evidence/theme-history-actions/reference-history.txt)，实施前约定见[计划](archive/history/HISTORY_READ_RECEIPT_PLAN.md)。

47的请求拥有连接修订+业务签名、cycleId、requestId和实际start次数。旧非空内容只负责显示，不能证明新手动读取成功。historyRenderRevision仍是接受并应用数据的显示事件，与请求回执分别管理。

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

## 验证与范围

- 12项协调器与8项policy单元覆盖身份、四次预算、4秒边界、接受、取消、认证、缓存、旧回执和重建。全体单元为259项。
- 16项实际ViewModel集成只使用模拟器内部127.0.0.1的公开HTTPS/SSE服务与真实加密HubStore。覆盖held response+SSE401、排队保存+SSE401、保存失败、缓存元数据、新旧绑定、forceHistory和长读后SSE恢复，未访问NAS或Lucky。
- 5项真实HubScreen保留旧非空历史，验证刷新/加载按钮、有限重试、串行占用、隐藏/后台、Activity重建与返回。页面使用真实HubScreen及受控action/state与时钟；Activity重建验证页面不重置已有回执预算，真实ViewModel预算由独立模型集成支撑。未宣称生产Activity与真实Hub网络下的端到端重建，也不是实体手机时序或FPS测量。
- 首次页面focused在暂停时钟时等待菜单导航同步；线程堆栈已定位，随后主动中断。只将该策略测试的已组合导航节点改为公开语义动作；刷新、加载和返回仍实际点击。中断记录保留在initial-review，不计为通过。

- 首轮完整检查191/192：旧TrendsNavigationScreenTest模拟源未发布新回执，严格匹配门槛下无法派发历史读取。只补齐测试的稳定来源签名及QUEUED/STARTED/RESOLVED状态后，该类单独2项及完整192项通过；生产包字节不变。首次失败、单独复核和最终运行各自保留。
- 最终Lint无错误，签名/权限/资源/附件核对通过；56份APK归档校验通过，模拟器公开数据清除、测试包卸载。0.2.46既有证据保持原哈希，不覆盖首次失败。
- 首次封存的安装哈希检查发现普通回归结束后应用已被移除，因此未发布final。随后在同一专用模拟器重新安装最终相同APK，再核对已安装字节、签名与版本并清理；已不存在的测试包直接核对缺席。该操作不计为覆盖升级，初次检查日志保留。

证据目录：[history-read-receipts](../evidence/history-read-receipts)。Dashboard多配置截图及Window性能仍属于0.2.46，主窗口Window仍属于0.2.45；本轮不将其改写为新包测量。大Hub容量、旧API图形/图片回退、实体手机和实际Lucky成功链路仍未确认。
