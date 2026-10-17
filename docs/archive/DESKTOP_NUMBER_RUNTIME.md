# 桌面总量数字复刻与运行测量

本页运行指标属于 0.2.42-dev；0.2.43 只验证普通数字行为，未测 FPS。默认数字色与显式主题覆盖已在0.2.43修正，见 [本轮范围](https://github.com/Gott-mit-Uns/token-monitor-android/blob/cfab5438ed5dac6537c68affb385517ccfa44c0e/docs/THEME_HISTORY_ACTIONS_PARITY.md)。下文测量数值与旧包哈希保留原归属。

用户已撤销此前单独提出的“主页 Token 变化时需要动效”要求。数字变化功能继续服从整个应用的统一目标：参照桌面 v0.68.0 本身已有的行为开发安卓 Hub 查看端。此后不把额外动效要求作为独立验收项目，也不要求每次封存固定导出或强调 33 帧。历史逐帧文件保留为历史证据；32ms 测试时钟采样不能证明实际运行帧率。

用户反馈效果不好、帧率低。本次工作修正了实际每帧执行范围，并记录真实 Android 帧时钟下的窗口指标。它没有证明实体手机的呈现 FPS 提高，也没有证明用户看到的卡顿已经完全解决。

## 原始桌面依据与保留行为

固定参考版本为 v0.68.0，提交 `5d2db368d8313415763860d594de00e46a663418`。相对于安卓项目根目录，参考目录为 `../work/token-monitor-reference/desktop-v0.68.0`，原始文件未修改。

| 要点 | 桌面源码 | 当前安卓实现 |
| --- | --- | --- |
| 单一过渡与缓出 | `src/electron/renderer/app.js:1656–1704`；一个可取消的 requestAnimationFrame 过渡，曲线为 `1-(1-t)^4` | `HeadlineNumberMotion.kt` 使用相同曲线；`HeadlineNumberMotionUi.kt` 使用实际 Compose 帧时钟。没有新的装饰动画或动画库。 |
| 更新时长 | `app.js:6642` 普通变化 1000ms，周期主动切换 800ms | 保留普通更新 1000ms、周期变化 800ms；Android 系统时长比例与本机减少动态效果偏好仍生效。 |
| 连续更新 | `app.js:6638` 从当前小数读数继续；相同目标的重绘不重启过渡 | 保留小数起点续接和相同目标不重启；旧过渡不能覆盖后来的目标。 |
| 简写、费用 | `app.js:6634–6651` 简写和费用使用新目标，完整数字逐帧变化 | 简写和费用继续来自当前目标周期，没有跟随中间帧改写数据。 |
| 字体与端点宽度 | `styles.css:3347–3358` 使用显示字体、500 字重、1.05 行高及 tabular-nums；开始时先按较宽端点拟合，完成后重新拟合 | 显示字体、字重和行高保留；本次补入 `fontFeatureSettings="tnum"`，按较宽端点缓存自然宽度。字体是否支持该特性仍取决于实际字体。 |
| 数字精度 | `app.js:905` 对 JavaScript Number 四舍五入并固定使用 en-US 分组 | 安卓保留精确 Long 端点，BigDecimal 插值保留 12 位小数，非负数显示采用 HALF_UP。当前界面 locale 限于 zh/en，完整整数分组形式相同；这不是对 JavaScript 大整数精度限制的复制。 |

原始桌面 `fitTotalNumber` 把缩放下限设为 0.5，并向下取整像素字号；安卓当前按可用宽度计算，没有该下限和取整步骤。这仍是移动布局中的具体差异，需要结合长整数和窄屏显示继续核对，不能直接称作像素一致。

## 本次修改的执行范围

`rememberHeadlineNumber` 仍在 `DesktopHubScreen` 根部持有同一个动画对象、周期记录、时间线和生命周期观察器，但现在返回 `State<HeadlineFrame>`，根部不再读取每帧值。只有 `DesktopTotal` 读取 `headlineState.value`，使计数帧对组成状态的读取集中在总量区域。进入设置、趋势等页面以及进入后台时，原有收束规则继续由根部持有的对象处理；没有把整个动画移动到可被条件销毁的总量组件里。

`DesktopTotal` 现在按 locale 缓存整数格式器；按目标总量、简写开关、单位偏好和 locale 缓存简写文本；按较宽端点、文本样式、密度、系统字号比例、文本测量器和字体解析器缓存自然宽度与简写宽度。可用宽度变化仍重新计算拟合比例，端点或字体变化仍重新测量。逐帧完整数字文本仍需更新和排版，并未宣称数字渲染已不发生任何工作。

这次同时补齐桌面的等宽数字特性。缓存与状态隔离没有改变曲线、时长、精确整数、连续续接、减少动态效果或后台收束规则，也没有增加 Hub 读取、SSE 连接、后台服务或定时网络工作。

## 测量方法与范围

测量入口是 `app/src/perfTest/java/dev/bing/tokenmonitor/HeadlineRuntimeBenchmarkTest.kt`，通过 `tokenMonitorMotionPerf` 显式启用，使用 debug APK 和同一受控的 `TokenMonitor_API36` AVD。公开 fixture 包含 80 个模型、120 个会话，主页启用模型和会话模块；输入记录数量不代表屏幕同时渲染 120 行。固定本机日历时钟、英文、深色，并允许正常数字过渡。测量没有读取真实 Hub 数据。

每个 APK 先显示初始画面，固定运行两次不记录的数字更新，再记录三次 10 亿与 20 亿之间的交替变化。两次预热会触及文本、布局和执行路径，但不能据此证明 ART/JIT 已经完全稳定；这是固定预热流程，不是经过收敛验证的性能基准。三次采样、debug 构建、顺序运行以及模拟器和宿主负载也限制了结论范围。

正式采样的 fixture 在 UI 更新前构造，更新时在 UI 线程记录 `System.nanoTime()`。`Window.OnFrameMetricsAvailableListener` 在独立 HandlerThread 回调中立即复制数值。采样前后排空已经进入指标 Handler 队列的回调，再按 VSYNC 时间戳筛选 **1.2 秒**窗口；Hub/Total 的 SideEffect 计数在更新后观察约 **1.4 秒**。因此，提交次数与窗口帧数来自不同观察区间，不能要求一一对应。

Hub 与 Total 次数是这两个函数范围成功提交的执行次数，既不是整棵 UI 树的重组总数，也不是绘制或屏幕呈现次数。`windowDrawCadenceHz` 按已收到的窗口帧时间戳首尾跨度计算，它表示该采样区间的 Window 绘制节奏。没有 SurfaceFlinger 呈现测量，也没有实体手机测量。

`TOTAL_DURATION` 记录窗口帧生成并提交到显示子系统的总耗时，`DRAW_DURATION` 是绘制 DisplayList 的阶段耗时；二者均不是“总量组件独占耗时”。`DEADLINE` 使用系统为当帧分配的实际预算，不采用固定 `1/refreshRate` 替代。另记录实际与预期 VSYNC 差值及 unknown delay，以保留 UI 迟响应信息。定义依据：[Android FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics)。

回调丢失数表示指标报告丢失，不是可见画面丢帧。`metricsComplete=true` 的定义仅为该次观察中 `metricsCallbacksDropped==0`；它不保证全设备呈现数据完整，也不保证回调尚未进入 Handler 队列时的全部生产端工作已交付。回调交付和报告复用规则见 [Android Window.OnFrameMetricsAvailableListener](https://developer.android.com/reference/android/view/Window.OnFrameMetricsAvailableListener)。

## 当前逐次记录

以下数据来自 `evidence/desktop-number-runtime/before.json` 与 `after.json`，均含 `deadlineMs` 和 `metricsComplete`。完整小数和其他统计保留在原始 JSON，表中仅作三位小数显示。报告刷新率均为 60.000004Hz；每次窗口为 1200ms，提交次数观察为约 1400ms。

| APK | SHA256 |
| --- | --- |
| 隔离前 | `51df015ff55b4e710fb45d6f88a764902fc899435fecc0dc3e89cff99ecad6ff` |
| 隔离后 | `1ee37d81f16ced3d8999e93b589bfb3fd8caf1cd93784aa698a116e1d9543409` |

| 状态/次序 | Hub 提交 | Total 提交 | 窗口帧数 | VSYNC 跨度 ms | Window cadence Hz | draw p95 ms | total p95 ms | deadline p50/p95 ms | 超过当帧 deadline | 丢失报告 | metricsComplete |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 前 1 | 57 | 57 | 57 | 1050.000 | 53.333 | 5.171 | 54.822 | 50.000 / 66.667 | 7 | 0 | true |
| 前 2 | 61 | 61 | 61 | 1033.333 | 58.065 | 6.010 | 49.958 | 50.000 / 50.000 | 4 | 0 | true |
| 前 3 | 56 | 56 | 56 | 1050.000 | 52.381 | 6.593 | 60.198 | 50.000 / 66.667 | 9 | 0 | true |
| 后 1 | 1 | 63 | 61 | 1033.333 | 58.065 | 3.020 | 18.903 | 33.333 / 33.333 | 1 | 0 | true |
| 后 2 | 1 | 63 | 61 | 1016.667 | 59.016 | 5.084 | 43.524 | 33.333 / 50.000 | 3 | 0 | true |
| 后 3 | 1 | 63 | 61 | 1033.333 | 58.065 | 5.759 | 28.339 | 33.333 / 33.333 | 2 | 0 | true |

本组直接证明：记录的三个过渡中，Hub 范围从随帧提交的 57/61/56 次变为每次 1 次，Total 继续提交 63 次。它支持状态读取已被隔离，不能推广为页面其他范围完全没有重组。

完善测量方法后的本组三次，cadence、draw p95 与 total p95 均优于对应的隔离前记录。首轮 `before-initial.json` / `after-initial.json` 则未呈现同样的总耗时或 cadence 改善：其三次 cadence 为前 58.065/55.161/59.032Hz、后 55.161/54.194/57.049Hz；total p95 为前 47.644/51.601/37.425ms、后 66.313/62.927/52.886ms。初版缺少当前的 UI 线程起点、Handler 队列交接和 deadline 完整性字段，不能与新版混合汇总成统一分布。两批次没有形成一致的总体性能结论；不能只挑较好的批次宣称用户卡顿已完全解决。首轮报告保留为方法改进前的记录。

当前较强的结论是：每帧重复执行整个 Hub 根部的问题已得到具体修正，并由独立的提交次数观察支持。窗口耗时改善是本组局部观察，仍需在后续真实场景与实体设备上检验；它不证明 Xiaomi 手机 FPS 提高。

## 尚未修改的源差异与后续核对

首次可用数据在安卓直接静态显示。原始普通桌面主窗口初始总量为 0，默认会从 0 开始过渡；跳过首次动画是浮动窗口启动时的显式选项。安卓的首次读取静态规则目前保留，应按统一桌面目标结合缓存恢复和连接建立过程重新评估，不把旧的单独动效要求作为决策依据。

原始 `animateNumber` 立即以 `performance.now()` 建立起点，第一个 requestAnimationFrame 已推进数值。安卓目前先等一帧建立时间线，再从下一帧推进，因此仍有约一帧的额外启动延迟；现有确定性检查包含这一行为。本次没有自行修改时序。后续应同时核对首次显示和启动延迟，再更新对应规则与检查，不能以现有检查通过证明这两处与桌面完全一致。

0.2.42 额外核对到默认颜色差异：原始 CSS 的 `--number` 为 `#f3fbf7`，仅自定义 text 覆盖时由 `themePresets.js:175` 镜像；当时安卓总量默认继承 onSurface `#eef5fb`。该用途差异已由0.2.43独立数字色与显式 text 覆盖修正，旧测量报告继续保留0.2.42的范围。

还需继续核对字体实际支持、极长整数的拟合下限，以及完整主页、真实前台数据变化和实体设备呈现。手机暂不能连接的状态仍然有效，不重复索取连接；实际 Lucky HTTPS、手机直连局域网 Hub 等验证也不由这份公开模拟器测量替代。
