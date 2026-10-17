# Hub freshness typed 复用：第 50 阶段

状态：B1 的实现、316 项单元、206 项普通回归、7 项完整容量路径、3 项独立 freshness 集成，以及 10 次独立进程测量已通过。lint 无 Error/Fatal，记录 97 Warning 和 1 Hint；本阶段只修正 freshness 对完整业务模型的重复重建。没有修复用户反馈的导航、语言和页面动效，见 [48 界面反馈](USER_FEEDBACK_048.md)。推荐安装包暂保持 48，下一构建优先补这三项界面缺口。

## 改动与业务边界

0.2.50-dev / code59 的应用 SHA256 为 `1a953fe776d8902a330562e6d056e7e1f153a39726d728be4468353d40900f89`。HubStream 标明成功 merge 后的 freshness 类型；同一 SSE generation 的单个消费者保留最后一次成功 typed Snapshot，freshness 只更新根时间以及各设备的 updated/received/stale，复用 periods、usage、sessions、projects、models、parts、历史及观察模型 ID。只有 typed 设备字段变化时才复制设备列表和对应设备对象。

复用基底不会来自 HTTP、缓存或旧 stream。新 full frame typed 解析失败会清空基底，下一帧按原完整解析回退；generation、前台、binding、auth、EOF 消费完毕以及保存 sequence 的原有保护继续有效。StreamMerge、投影 JSON 树、完整正文 stringify、缓存格式、GCM/gzip/EOF 与保存队列未改动，32 MiB 输入/展开及 10 MiB 密文边界未放宽。

22 项新单元差分检查沿用“旧 merge 后完整 parse”的结果作为对照，包括连续/sparse freshness、null/类型转换、重复与特殊设备 ID、忽略业务及 revision 字段、投影后的基底和引用复用。3 项新集成覆盖真实 ViewModel 消费/保存/后台冷恢复、EOF 后新流的基底隔离，以及 full frame typed 失败后的恢复。不得将单次受控 freshness 的零完整重建扩展为 full frames 或冷缓存也不解析。

## 同来源重复测量

采用固定桌面 v0.68.0 提交 `5d2db368d8313415763860d594de00e46a663418` 的原始来源生成器，完整 16 设备 stats 为 21,667,533 B，每期 4,800 sessions / projects、320 models，31 日/2 月历史全部保留。自有 API36 AVD 的 Java 堆上限为 201,326,592 B（192 MiB），没有 largeHeap；另有 8 设备/370 日联合缓存回归。来源 fingerprint、8 个不同 full frame 的业务摘要、完整费用/未定价/parts、设备与历史，以及冷恢复均检查。

49 与 50 各有空首页和默认四模块首页各 5 次独立进程运行，共 20 个成功样本。P50 是中位数，P95 使用 nearest-rank，n=5 时等于最大值；这些是两组重复样本，**不是随机配对试验**。受控 freshness 在导航阶段等待显式 credit，默认动效由虚拟 Compose 时钟运行，均不能代表不限流生产更新、真实 Window 节奏或手机 FPS。

| 采样指标 | 空首页 49 → 50，P50 / P95 | 默认四模块 49 → 50，P50 / P95 |
|---|---|---|
| 受控 freshness typed 模型工作，ms | 完整重建 244.079 / 250.713 → 小补丁 0.088 / 0.165 | 完整重建 237.955 / 263.379 → 小补丁 0.065 / 0.078 |
| 完整受控 freshness 动作，ms | 1156.956 / 1286.024 → 1325.635 / 2365.385 | 1154.818 / 1331.284 → 1102.518 / 1212.748 |
| 全 probe 耗时，ms | 34435 / 38258 → 34925 / 41860 | 34550 / 36519 → 35200 / 37442 |
| 最大 Main 心跳排队延迟，ms | 778.967 / 884.860 → 816.141 / 844.451 | 783.877 / 815.623 → 799.602 / 847.278 |
| JSON stringify inclusive 总量，ms | 591.915 / 660.876 → 632.140 / 1422.630 | 617.908 / 708.233 → 602.817 / 709.061 |
| 全 probe 完整 Snapshot parse 调用次数 | 11 / 11 → 10 / 10 | 11 / 11 → 10 / 10 |
| 全 probe MODEL_IDS 阶段次数 | 23 / 23 → 22 / 22 | 23 / 23 → 22 / 22 |

受控事件由完整 typed 重建改为字段补丁，**这一个阶段**的重复工作明显减少；两列比较的工作范围不同。全 probe 与完整 freshness 动作没有显示一致的整体加速，空首页还保留了较慢样本。原 stringify/cache 路径和页面派生仍占工作量，不能将局部补丁耗时解释成“整体快了数千倍”。MODEL_IDS 同时记录模型收集与缓存元数据校验，保存校验仍可出现；测试检查观察 ID 引用复用，没有要求该总阶段全部为零。

50 的 Java 采样峰值仍接近 192 MiB，空/默认 P50 为 201,325,440 / 201,325,408 B；native 采样峰值 P50 为 103,585,584 / 110,708,944 B。整个 probe 的 ART 分配计数增量 P50 为 6,193,600,512 / 6,227,912,672 B，GC 总计时增量 P50 为 11,489 / 10,904 ms。它们包含测试断言、显式冷 GC 和 process 工作，不是精确对象存活、应用独占分配量或 stop-the-world 停顿。10 ms 堆采样读取 total/free 非原子；100 ms Main 心跳是排队测量，不能当首帧或输入延迟。

## Trace 与验收范围

10 个 trace 均识别出唯一 Hub process async track owner，以及 tid=pid 的 Main，进程彼此独立；Hub 阶段身份与 cookie 完整对应。没有采样调用栈或精确分配/存活资料。不能由 process async span 推断其整段都在 Main 执行，inclusive span 与动作耗时也不能相加。

原始 protobuf 检查均完成，没有压缩包未检查、ftrace lost-event bundle 或 ftrace error；每个 trace 都有一个内部 FrameTimeline dropped 标记。`empty-run2` 与 `default-run2` 的标记分别位于 probe 原点后约 7.467 s / 6.026 s，其余 8 个在原点之前。标记时间是通知位置，不是遗漏范围；这些 trace 不提供完整事件覆盖或渲染 FPS 证明，不能沿用 49 的“全部通知在原点前”描述。

首次 unit 构建的期望大小写修正、首个测量 pilot 对 MODEL_IDS 校验范围的修正均保留原失败日志与原 APK。首个 pilot 不进入成功统计；两处修正只改测试，成功主 APK 没有随测试修正变化。50 的测试快照与 49 原封存证据分开保存，旧 48/49 与 dist 不得回写。

证据入口：[比较报告](../../evidence/hub-performance50/comparison.json)、[候选统计](../../evidence/hub-performance50/candidate-summary/summary.json)、[Trace 原始包质量](../../evidence/hub-performance50/trace-quality-summary.json)、[Trace process 范围](../../evidence/hub-performance50/trace-scopes/summary.json)、[构建回执](../../evidence/hub-performance50/candidate-build/build-receipt.json)。本阶段没有真实 Hub、NAS/路由器修改、实体手机或 Lucky 公网测试。

后续先修 [用户反馈的三项界面缺口](USER_FEEDBACK_048.md)，之后继续 B2/B3 与页面准备等容量性能工作。原要求的完整安卓伴侣与桌面体验尚未完成，本阶段的局部优化不能代替它们。
