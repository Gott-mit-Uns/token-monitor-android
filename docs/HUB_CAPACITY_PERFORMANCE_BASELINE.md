# 容量性能测量基线（49）

**当前推荐包仍为 0.2.48-dev。49 工作源码为只有测量点的 0.2.49-dev / code58 基线；尚未实施容量性能算法优化。** 294 项单元、203 项普通 API36 回归及 10 次独立进程的大容量界面测量通过。三次失败试跑原样保留，未计入统计。整体目标与容量性能修复继续进行。

基线应用 SHA256 为 `6851741eec2b459cb025b66e3b5ec7f2cd92c59c53123c4b02018a9ea224b6e7`；10 次测量使用同一测试包 `63125a2b0e637dae99defa4a50f0c6947fed63bd145932f0fa055360e962384f`。普通回归测试包为 `520650ed3f2d763c1bc09e59b060167fb6a2d5288674dc7326d7c10e86a6f468`。[源码及构建回执](../evidence/hub-performance49/baseline-build4/build-receipt.json)、[294 单元原始结果](../evidence/hub-performance49/baseline-build/unit-results)、[203 普通回归回执](../evidence/hub-performance49/ordinary-regression/run-summary.json) 分开保留。

## 来源、运行条件与重复方式

沿用 48 的原始桌面来源函数及完整公开样例：16 台设备，每周期 4,800 会话、4,800 项目、320 模型，31 日和 2 月历史。原始 stats 为 21,667,533 B，生产投影正文 10,962,285 B，完整 SSE 事件 21,667,634 B。每次验证原始 stats 摘要，并分别验证两组、每组四个完整 SSE 帧的摘要；只还原根公开时间戳后比较，其他业务字段不变。每周期逐条核对会话、项目、费用、未定价与 Token 组成；核对所有模型归属、设备标识和全部历史日期。来源未缩减，生产边界未扩大。

设备为当前任务拥有的 API36 模拟器，实际 Java 堆上限 201,326,592 B（192 MiB），没有 largeHeap。每次强制结束前一应用进程，再单独运行一个方法；[trace 进程与 cookie 证明](../evidence/hub-performance49/tools/perf49-trace-scopes.json) 确认 10 个不同的应用 PID、每条 trace 一个 Hub 阶段发出者、一个对应 Main TID，所有观察到的 Hub cookie 唯一且有效。

空首页／reduced motion 使用 empty-run4–8，首次启动的默认首页配置／normal motion 使用 default-run1–5。默认首页是 limits、models、sessions、activity 四个模块。正常动画使用生产 scale=1 与原时长，但测试注入 Compose mainClock，断言前推进 1,100 ms 虚拟时间；这覆盖动画计算与端点，不能证明真实手机的动画节奏。八个 full frames 的业务 Token 相同，本次没有测连续变化的 Token 动画中断。

实际 HubScreen 在完整 stats/history/model names/device history 读取后挂载，再接收第二组四个大帧；随后执行周期、会话分页、项目和设备行内展开、Dashboard、一次受控 freshness、单独缓存落盘、Activity STOP、后台静默与冷 VM 恢复。虚拟帧时钟下状态更新可能合并，本次未逐一断言每个中间 revision 已呈现，真实 Android Window 的连续更新与导航仍需独立测量。缓存落盘明确在 STOP 前单独等待完成；冷恢复核对该版本、密文不变与零网络读取。这个测试不要求 STOP 保存尚未落盘的排队帧。

本地 TLS 中转按 8 KiB 流式转发大帧；连续 freshness 在导航期间暂停，保留固定 SSE comment 检测取消，再给一次业务更新 credit。这个控制用于分离工作，**不能与 48 的持续 freshness 复合导航计时直接比较，更不能解释为性能改善**。实际公网 Lucky、实体手机、持续 freshness 与导航同时进行、全部六个首页模块、实际 active widget 和旧 API 运行均未由本基线确认。

## 五次重复的结果

[完整统计及逐次结果](../evidence/hub-performance49/baseline-summary/summary.json) 保留每个独立样本。下表的 P50 为中位数，P95 使用 nearest-rank；每组 n=5，所以 P95 等于本组最大值。阶段时间是 inclusive elapsed，嵌套阶段不能相加；别名时间已经包含在部分会话准备时间内。

| 指标 | 空首页 P50 / P95 | 默认首页 P50 / P95 | 口径 |
|---|---:|---:|---|
| 完整受测工作窗 | 34.435 / 38.258 s | 34.550 / 36.519 s | 含完整业务校验、测试操作与单独冷释放 GC |
| 最大 Main 心跳排队延迟 | 779 / 885 ms | 784 / 816 ms | 100 ms 心跳，非绘制帧时长 |
| Sessions 原始全表准备 | 510 / 569 ms | 495 / 527 ms | 每轮一次，4,800 行，Main 上执行 |
| 模型别名规划 inclusive 总时间 | 158 / 175 ms | 186 / 197 ms | 每轮分别 4,831 / 4,836 次；当前 aliases 为空且 grouping=off |
| 一次 freshness 全文序列化 | 592 / 661 ms | 618 / 708 ms | 后台，输出整份统计正文 |
| 一次 freshness Snapshot 重建 | 244 / 251 ms | 238 / 263 ms | 从受控 freshness 对应的唯一保留 span 计算，后台执行 |
| Sessions 首页复合动作 | 1,137 / 1,318 ms | 1,136 / 1,214 ms | 点击、准备、测试同步及断言；不是纯函数时间 |
| 100 行下一页复合动作 | 313 / 378 ms | 318 / 333 ms | 同样包括同步与布局；page 函数总时间不足 1 ms |
| 一次 freshness 复合动作 | 1,157 / 1,286 ms | 1,155 / 1,331 ms | credit、源等待、处理与完整业务核对 |
| freshness 后缓存落盘等待 | 1,578 / 1,884 ms | 1,574 / 1,667 ms | 单独记录，未并入 Main 接受时间 |
| 后台冷 VM 恢复 | 863 / 870 ms | 840 / 868 ms | 无网络、完整统计与历史、缓存版本核对 |

Java 使用量采样中位数分别为 201,325,408 / 201,325,424 B，仍贴近 192 MiB 上限；最大读数组合为 201,340,496 B。`totalMemory()` 与 `freeMemory()` 是分别读取，略高于 max 的组合值不证明硬堆越界。native 分配采样峰值中位数约 105.1 / 106.3 MB，另保留最终 PSS；它们不是 Java 堆，也不能直接相加。

过程级近似 ART 分配计数中位数约 6.26 / 6.27 GB，包含应用、业务验证与测量器；GC 累计运行计时约 10.96 / 10.93 s。这不是单次暂停，也没有证明分配调用点或 GC 后对象存活。强制 GC 仅在旧 UI/VM 释放后的 `EXPLICIT_COLD_GC` 窗口执行，未用于导航、连续帧或 freshness 测量。

## Main 与 GC 的实际区间

[empty-run4 操作窗口 SQL](../evidence/hub-performance49/tools/perf49-empty4-action-analysis.sql) 和 [原始结果](../evidence/hub-performance49/tools/perf49-empty4-action-results.txt) 使用报告的 System.nanoTime／elapsedRealtimeNanos 数值锚点定位，不按文件开始时间猜测。下面是该单次 trace 的区间归因，不能当作五次分位数。

- Sessions 首页操作包含 Main `Compose:recompose` 661.366 ms、`SESSION_RAW` 492.793 ms和 4,816 个嵌套 alias plan，inclusive 合计 144.198 ms。最长 Choreographer callback 为 59.847 ms，Main 心跳延迟约 695 ms；post-handshake ScopedPause union 为 0.556 ms，没有 Main allocation-wait 记录。主队列阻塞和绘制 callback 是不同区间。
- 项目来源行操作中，Main 重组最大 150.413 ms、四次 `PROJECT_ROWS` 合计 100.780 ms；最长 callback 6.714 ms，但首次 callback 约在动作开始后 502 ms到达。ScopedPause 0.132 ms，没有 Main allocation-wait 记录。行准备不覆盖整个滚动／展开操作。
- 受控 freshness 中，后台 stringify 585.604 ms、Snapshot parse 226.297 ms、Main accept 0.207 ms。worker allocation-wait union 377.310 ms、ScopedPause union 1.163 ms；没有 Main allocation-wait 记录。后台等待与阶段工作有重叠，不能相加为 CPU 时间。

ART collector、mutator suspend handshake、post-handshake suspension 与 allocation wait 分开查询；同类多线程等待按区间 union，避免把 thread-time 总和当作用户经历的 wall time。[Android ART suspend 实现](https://android.googlesource.com/platform/art/+/master/runtime/thread_list.cc)、[GC completion wait 实现](https://android.googlesource.com/platform/art/+/c6371b52df/runtime/gc/heap.cc)；近似运行计数的定义见 [Debug.getRuntimeStats](https://developer.android.com/reference/android/os/Debug#getRuntimeStats())。

## 轨迹限制与失败记录

10 条 trace 均保留 `config_write_into_file_no_flush=1` 配置诊断，以及一次 FrameTimeline packet-loss 统计。只读 raw protobuf 检查未发现 ftrace bundle 的 lost-events/error 或 ftrace 中途 dropped marker，压缩未检查包为零；每条 FrameTimeline 中途丢失通知都早于 probe 起点。通知时间并不定义全部被遗漏 payload 的范围，因此总体 trace 仍保留丢失限制；本次没有使用 FrameTimeline 计算 FPS或完整帧统计。[逐条质量记录](../evidence/hub-performance49/baseline-summary)、[检查工具](../evidence/hub-performance49/tools/perf49-trace-quality.py)与[TracePacket 定义](https://github.com/google/perfetto/blob/main/protos/perfetto/trace/trace_packet.proto)可复查。

所有 trace 均没有 perf/CPU stack samples；一个未结束的 Main scheduling state 单独保留，未计入已结束区间。阶段调用量与源码位置可确认重复工作，但不能据此推出未测的 Period.equals 调用量、精确分配调用点或对象存活关系。普通 Choreographer 首次回调也不代表首次可交互帧。完整 runner/trace 约 35–85 s，受测工作窗约 34–38 s；窗外准备／收尾时间尚未精确归因，未当作应用纯工作时间。

失败试跑 1 为测试把 cacheWrite 再从 inputMiss 扣除，已按原桌面算法校正；2 假设存在 Sessions 查询控件，桌面和安卓均没有该主列表输入，移除该操作并明确 `sessionQueryUiMeasured=false`；3 使用合并语义树寻找被合并的展开子行，改为 unmerged tree，同时核对父行的展开状态。三轮原始日志、报告、trace、对应测试包与源码摘要均保留在 [baseline](../evidence/hub-performance49/baseline) 和 build1–3，未改生产行为以迎合断言。

## 接下来需要验证的优化

[性能计划](HUB_CAPACITY_PERFORMANCE_PLAN.md) 的 coarse baseline 与 Main/GC 区间已取得，精确分配／存活与采样调用栈仍是未完成项。下一步先收紧 full frame 树驻留和 freshness 业务重建，再复用有界别名 resolver、按实际可见模块生成首页会话来源、缓存静态会话表与限制分页复制。Sessions 全表是 Main 工作的明确目标；隐藏首页仍执行两次来源派生也是实测可避免工作，但仅约 9 ms 的中位总量不能解释全部延迟。

所有候选都要与这份冻结输入和构建配对比较，并补持续 freshness／导航、正常 Token 变化、保存阻塞与后台／认证取消场景。继续保留完整周期／会话／项目／模型、精确费用、全部日期和 v2 认证恢复。当前仅完成测量基线，不能将“容量性能问题已解决”作为结论。
