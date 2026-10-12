# P1-09 — 历史请求间隔的本地定位

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
