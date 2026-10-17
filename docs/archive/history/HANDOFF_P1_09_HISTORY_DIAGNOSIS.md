# P1-09 — 历史请求间隔定位与身份验证

## 续接完成：验证工具修复与真实NAS只读复核

2026-10-12，后续起点 `18e0c0b`；同一分支 `test/p1-09-history-read-diagnosis`。**P1-09的验证工具修复及当前链路复核已完成；旧r10的具体触发原因不可恢复，原FAILED继续保留。** 未修改产品源码、请求节流或Hub协议，不把历史证据缺失推断为客户端缺陷。

### 已确认的问题与最小修复

旧 `check-live-hub.py` 全局要求history总次数1..4、相邻开始间隔至少3.5秒，但产品合同的预算与等待适用于同一目标/周期；新revision允许新周期首次立即读取。新增公开fixture证明：有完整目标变化证据的1.685秒新revision读取被旧聚合断言拒绝；新的校验器接受该合法路径，同时仍拒绝同周期短间隔、3999ms等待、第五次尝试、无依据的新周期、错误/重复请求身份、缺失终态和请求重叠。这是修正验证对象，并将同周期等待检查收紧到完整4000ms，未为取得通过而删除产品断言或修改节流阈值。

仅修改测试与工具：

- `LiveHubTest` 用测试专属StateFlow观察器记录单调毫秒、run-local目标序号、cycle/request/attempt、phase、自动重试资格及revision/连接绑定变化布尔值。签名和实际连接revision值只在内存比较，不输出响应、设备名、地址、凭据或签名内容；不向生产HTTP添加身份头。
- `live-hub-proxy.py` 记录history路由序号、单调纳秒、开始/子进程结束及同时活动history数量。跨时钟不相减；在原生开始数与桥接开始数严格相等且均无重叠后按顺序关联。
- `history_read_evidence.py` 严格白名单解析并检查请求身份、周期预算、终态后等待、目标变化原因和桥接完整结束；缺失身份失败，不退回旧的计数猜测。成功和失败均另存脱敏时间线，私有instrumentation全文不存档。
- `HomeHistoryReadPolicyScreenTest` 新增实际HubScreen受控fixture：同目标失败后1500ms仍无重试，随后新revision在旧4000ms期限前派发首次读取；新周期attempt=1，旧预算不复用，成功后不再读。这里使用受控Compose时钟，不能解释成实体设备或实网时延测量。

### 定向结果与证据

本轮：校验器12项、桥接7项、Android8项通过。Android包括整个HomeHistoryReadPolicyScreenTest的6项及真实ViewModel的revision旧回复隔离/四次预算2项；不是全套回归。上一阶段22单元结果仍归上一阶段，本轮未重复运行。

新建独立 `TokenMonitor_P109` / `emulator-5584` / API36，未使用已有5556或手机。JDK17/SDK37/wrapper离线构建；首轮因新AVD尚未启动完成导致安装未开始，保留 `live-run1-console.txt` 和对应APK双哈希，等待启动完成后运行。一次Python发现方式没有收集连字符命名测试，0 tests不计通过；改为直接执行后12项最终通过。无产品或断言失败被隐藏。

NAS SSH仍拒绝；复用桌面已配置的安全连接直读授权NAS，凭据只留在读取进程内，未复制配置或写入文档/命令。两轮真实Activity/Home/网络/socket factory/加密缓存运行均观察前台75秒，后台停止读取、返回前台恢复通过，均有一条持续前台SSE和返回后的另一条SSE，最终全部结束：

| 运行 | 应用/测试包 | history结果 | 边界 |
|---|---|---|---|
| live-run2 | 初始身份schema，双哈希见apk-run1.sha256 | 2次，开始9.375/78.064秒，2个目标/周期，无重叠 | 尚未区分revision与binding布尔原因；中间阶段证据保留，不当最终schema验收 |
| live-run3-final | 最终schema，双哈希见apk-final.sha256 | 1次，开始8.056秒，1周期，完整RESOLVED，无重叠；无revision/binding变化 | 当前真实链路通过；没有触发同周期重试或跨revision短间隔 |

最终应用Debug验证包SHA256 `24b0f7c2379c6746da5744cb9da3dfc11a9147a0d0efc214042fe0453f4cbfdb`；最终测试包SHA256 `e4f6b69182072a19ce704144afc4a3fd5378084b3fff9e9716d288fde6afd9a9`。两包签名验证通过；设备已安装base.apk的哈希分别匹配。包名/版本仍为0.2.53/code62，Debug验证包不是正式交付包。产品源码和构建版本文件相对18e0c0b无变化，应用APK在两次测试构建中哈希相同。

证据在本机 `evidence/p1-09-history-identity/`：两轮真实读取报告及脱敏身份时间线、首启动失败、定向8项原始报告、12/7项工具结果、构建日志、应用/测试APK双指纹/签名/安装哈希及最终验证APK。没有UI产品变更，采用运行结果和身份时间线作为必要证据，不截图真实私人数据。目录不入Git；来源指纹和提交回执一并保留。

### 结束边界

本轮独立提交测试、工具及任务/矩阵/交接更新。产品仍为已发布0.2.53/code62；未递增版本、生成正式APK、创建Tag、推送或重新发布Release。没有修改NAS、Hub、Agent、Lucky、桌面客户端或路由。旧r10无法补齐原始请求身份，不能认定其确为revision变化，也不能认定其确为重复请求；未复现1.685秒实网路径。真机、蜂窝/Lucky、长时间性能及其他任务保持各自验收边界。本轮到此停止。

## 首轮本地定位记录（历史阶段）

以下记录为18e0c0b提交时的定位范围和阻塞；其中“未修改旧脚本”“未访问NAS”等描述仅指首轮，当前状态以上方续接为准。

2026-10-12，Asia/Shanghai。起点为 main / `a3f03cf`，已发布产品0.2.53/code62；分支 `test/p1-09-history-read-diagnosis`。工作区已有未跟踪 `AGENTS-original.md`，未修改或纳入提交。只处理P1-09，没有访问或修改NAS、Hub、Lucky、路由、桌面客户端或手机。

## 问题与定位结论

旧 `work/s5-lan-home-r10.txt` 保留 FAILED，三次history开始时间为5.653、63.227、64.912秒，后两次相差1.685秒。日志只有路由计数和聚合开始时间，没有revision变化、key/cycle/attempt、响应终态或每次触发原因；结束时activeReads=0不能证明运行中没有重叠。不能据此将失败归因为Hub、SSE重复连接或客户端重试缺陷。

`tools/check-live-hub.py` 的home检查对所有相邻history开始时间要求间隔至少3.5秒；同一处注释允许真实revision变化随时创建新周期。这两个条件存在口径冲突：跨周期的开始时间不足以验证同周期的4秒重试等待。旧脚本和失败断言均保留，未放宽为通过。

当前调用链：`DesktopHubScreen.kt` 用connectionRevision与historySignature创建策略；`HubViewModel.acceptSnapshot` 在signature改变时取消旧活动读取并建立新目标；`HistoryReadCoordinator.target` 对新key生成attempt=0的新周期；`HomeHistoryReadSchedule` 对同周期失败等待完整4秒，对新目标首次读取允许立即派发。`readPart`仍通过foreground/loading/auth门槛串行接纳，旧响应通过ticket/binding及请求回执身份隔离。

因此，本轮能确认当前策略允许合法的跨revision短间隔，并阻止同目标短间隔重试；**不能确认r10实际属于哪一条路径，也未复现其生产失败或证明运行中并发不存在**。本地定位完成，生产归因受阻。

## 最小修改与定向验收

仅在既有 `HomeHistoryReadPolicyTest` 增加两个协调器与调度器组合测试，使用原63.227/64.912秒的相对时间：

- 同目标：第一次失败后，在1.685秒处ready/requested均为false，预算保持1；终态后3999ms仍不可派发，4000ms接纳同cycle第二次读取。
- 新revision：先取消旧活动请求，再换key，新cycle在1.685秒处接纳首次读取；旧成功响应不能结束或覆盖新请求。

使用既有JDK17、SDK37和Gradle wrapper，离线执行 `:app:testDebugUnitTest`，仅选择 `HomeHistoryReadPolicyTest` 与 `HistoryReadCoordinatorTest`。首次运行通过：10+12=22项，0 failures/errors/skipped。没有先构造伪造的产品首失败，也没有用编译代替验收；这两个用例是受控逻辑分支验证，不是原实网运行重放。既有非空断言/Gradle弃用警告保持，不处理无关事项。

本地证据在 `evidence/p1-09-history-diagnosis/`：原失败副本、首轮构建日志、两份JUnit报告、输入源码及报告SHA256清单。没有UI或产品源码修改，因此采用单元结果及原始时间证据，不新增截图。未构建/安装应用或测试APK，未重跑无关套件、Lint、真机或公网测试。旧r10摘要没有绑定APK指纹，本轮不能倒填其来源；本轮测试绑定起点提交及当前源码指纹。

## 提交、交付和停止边界

独立提交包含测试、NEXT_TASKS、FEATURE_MATRIX、0.2.53交接续接入口及本文。提交标识通过 `git log -1` 和本地证据回执查询，不把提交自身SHA写入自身内容。产品版本保持0.2.53/code62；测试与定位文档不触发版本递增、Tag、新APK或Release，本轮没有推送或重新发布。

后续只建议另行安排脱敏的请求身份时间线：请求序号、单调时间、cycle/attempt、revision变化布尔事件、取消和终态事件；不记录签名值、响应、设备名称或凭据。将桥接请求与周期关联后才有依据评估同周期等待、跨revision新读取和并发。原运行缺少这些事件，无法在不重跑生产链路的情况下追溯补齐。本轮到此停止，不自动执行生产验证或下一任务。
