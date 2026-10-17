# 桌面原有动效实施记录

状态：**45 已实施、统一验证通过，APK 与证据均已封存。**

本记录说明已实施的来源规则、实际验证及平台边界。开发包 [token-monitor-native-0.2.45-dev.apk](../dist/token-monitor-native-0.2.45-dev.apk) 为 `0.2.45-dev`、version code `54`、`15,126,122` bytes，SHA-256 为 `d9d93a7f5d98a6d3051f23beb67f1843e2e836b062bf448f1f6ca056237431fe`，与统一回归、实屏及当前窗口报告绑定。当前目标沿用[项目 brief](../.ui-craft/brief.md)，按桌面本来已有的功能复刻；旧的额外 Token 动效要求不作为本轮依据。

## 固定来源与可复查证明

桌面来源为 `../work/token-monitor-reference/desktop-v0.68.0`，固定提交 `5d2db368d8313415763860d594de00e46a663418`。原始函数证明已执行 **22 cases / 82 assertions**；这个数量只证明下述受控源码用例，Android 验证使用下文独立的实际报告。

- [来源执行脚本](../evidence/desktop-source-motion/reference/capture-original-motion.cjs)：调用固定提交的完整 `renderTrends()`、原始捕获/趋势动画函数、headline 动画器、连续 early-return/render 分支、同步周期 handler 片段以及减少动态效果收敛链。
- [公开结果 JSON](../evidence/desktop-source-motion/reference/original-motion.json)与[文字报告](../evidence/desktop-source-motion/reference/original-motion.txt)：保留输入、源码片段/文件校验、输出及测量边界。
- [实施前计划](archive/history/DESKTOP_SOURCE_MOTION_PLAN.md)保留来源行号与原先差异；本文记录本轮已写入代码的行为。

| 当前来源文件 | SHA-256 |
| --- | --- |
| `capture-original-motion.cjs` | `5bad836a54a219a743690b87853438b6312199037a4fb6d2b089f363625f0c77` |
| `original-motion.json` | `4972c822f427b9c214a99659089f2f9ffb8510fe6bb97599a25b96b13e385b68` |
| `original-motion.txt` | `ded7fccefdb53dca7c873e75444e82e4134863ec2bc01edd7bd1a6d7ceaeceb6` |
| 固定提交 `src/electron/renderer/app.js` | `8b2620f5c5439237160ce38655eda198e0a6b6a67df0e71b5ebdd0e4a0511025` |

脚本逐文件核实来源字节。RAF 使用有界单调模拟时钟；DOM 尺寸为明确提供的公开几何，WAAPI 记录原函数产生的关键帧、时长、延迟、曲线与结束调用。它不测量浏览器真实布局、WAAPI 中间帧、Android 帧呈现或物理设备 FPS。所有输入均为公开合成值，没有真实 Hub 数据或凭据。

## headline 实施行为

实现位于 [HeadlineNumberMotion.kt](../app/src/main/java/dev/bing/tokenmonitor/HeadlineNumberMotion.kt) 与 [HeadlineNumberMotionUi.kt](../app/src/main/java/dev/bing/tokenmonitor/HeadlineNumberMotionUi.kt)，根接入位于 [DesktopHubScreen.kt](../app/src/main/java/dev/bing/tokenmonitor/DesktopHubScreen.kt)。

| 分支 | 45 实现 | 固定来源 |
| --- | --- | --- |
| 普通首次有效非零总量 | UI holder 从 `currentTotal=0` 创建，普通时长 1000ms，`easeOutQuart = 1-(1-t)^4`。首次 0 没有待播放 tween。 | `app.js:309,1656–1704,6625–6651` |
| 首帧计时 | 在请求首帧前取与帧时间同域的单调起点，首帧直接计入已经经过的时间；不先等一帧建立起点。 | `app.js:1672–1704` |
| 端点预适配 | 启动时先发布较宽端点供既有数字宽度/字号适配，随后绘制插值。内部从 0 开始不意味着必须呈现一帧字面的 0。简写使用目标总量，保留既有 locale/字体/密度的端点测量缓存。 | `app.js:6634–6645` |
| 数据为 null / fixed 未就绪 | 取消当前动画，显示 `—`，将静态基线设为 0。保留私有小数值及尚未消费的一次性抑制标记；后续有效值从静态 0 起播。 | `app.js:6613–6624` |
| 总量已知但详情不支持 | 取消动画，静态保存已知 period 总量；不消费初次抑制。根按保留的主视图，为 derived 周期的 Sessions/Projects 接入此分支，Settings 往返不丢失主视图身份。 | `app.js:6608–6624` |
| 显式初次抑制 | holder 创建时捕获 `suppressInitial`；只有受支持的有效目标才消费 pending 标记并静态显示。源中 Windows 浮球展开重建窗口属于显式例外，不能推导普通冷启动都应抑制。 | `app.js:6627–6632`；`main.js:2246–2251` |
| 周期已就绪 / 异步就绪 | 已就绪的周期主动切换保留 800ms。unready 分支已取消且周期 handler 已同步返回，后来同周期 ready 使用普通 1000ms。 | `app.js:12030–12033,12048–12050` |
| 同目标 / 中途更新 | 同目标不重启；更新目标从当前未四舍五入的小数续接。最终端点使用精确 `Long`，分数尾部有界，保留大数和单 Token 差值。 | `app.js:1656–1704,6625–6651`；Long 精度为 Android 保留的数据语义 |

来源用例在 100ms 调用 `0→1000`：pre-RAF 文本为 `1,000`，内部动画值仍为 0；116ms 首个 RAF 为约 `62.480318464`、文本 `62`；350ms 文本 `684`；1100ms 精确结束为 `1000`。测试使用这些输出验证首帧，未把镜像公式作为唯一来源证明。

共享 `DesktopAnimationClock.nowNanos()` / `LocalDesktopAnimationClock` 定义在 `HeadlineNumberMotionUi.kt`。生产默认 `System.nanoTime()` 与 Android 帧时间同域；虚拟 Compose 测试提供稳定的 `mainClock.currentTime×1_000_000` 时钟实例。虚拟时间不能与默认生产起点混用，也不能通过恢复“等待第一帧才计时”来掩盖不同时间域。

根保留 holder，但不读取其逐帧值；仅总量区域 `DesktopTotal` 读取。减少动态效果立即收敛，Android STOP 收敛并停止帧循环，返回不重新播放已收敛目标。STOP 行为和 Android 时长比例属于平台适配，见下文。

## 趋势实施行为

实现位于 [TrendsBarMotion.kt](../app/src/main/java/dev/bing/tokenmonitor/TrendsBarMotion.kt)、[TrendsBarMotionUi.kt](../app/src/main/java/dev/bing/tokenmonitor/TrendsBarMotionUi.kt)、[DesktopTrendsMain.kt](../app/src/main/java/dev/bing/tokenmonitor/DesktopTrendsMain.kt)。根在条件页面之外保存 holder，输入仍为主窗口 preview 投影，不为动效改用完整历史或增加读取。

- 日期/月 key 保留所有点，包括高度为 0 的点；从当前显示高度与新目标高度的比值开始向 `scaleY(1)` 变化。底部固定，整个圆角矩形随纵向变换缩放。普通柱/末柱透明度、轴、范围和四卡仍按既有源规则，零值标记保持静态。
- 时长为 420ms，曲线 `cubic-bezier(0.22,1,0.36,1)`。已有 key 延迟 0；新 key 或明确进入从 0 开始，延迟 `min(index,14)×14ms`，最大 196ms。既有零值转正仍是已有 key；正值转零立即显示 0；删除 key 不增加退出动画；`abs(scale-1)<0.001` 跳过静止变换。
- 首次进入及真正切换主视图设置 entry pending；只有非空、可见的有效图才消费。空图及 fixed 未就绪不制造柱形，不提前消费。打开独立详细历史、Settings 往返保留主视图；返回不重新发出从零进入。
- `renderEventKey` 使用连接 revision、保存时间、当前 snapshot、period、本地日与 fixed 结果。真实数据/周期变化可从当前高度重新计时，包括进行中的相同端点重渲染；普通 Compose 提交不作为新的数据事件。main view 身份单独控制 entry。
- 300×120 是坐标系，保留第 44 阶段的弹性图高和短屏/大字自然滚动。纯尺寸变化缩放当前几何与变换，不重新进入、不重启期限；来源 `app.js:12587–12589` 的 resize 不重新渲染趋势。
- 返回的 `State<TrendsBarFrame>` 仅在 Canvas 的 DrawScope 读取。holder、根部、卡片、轴与组件测量不订阅逐帧状态；曲线循环不触发 Hub 请求、SSE 更新、服务或后台任务。

主要源规则为 `app.js:1891–1921,4959–5017,6964–6977`；零值矩形及静态标记来自 `usageCharts.js:486–502`，外观来自 `styles.css:6556–6570`。

## 平台边界

减少动态效果沿用 `system/on/off`：on 静态，off 忽略系统减少动态效果，system 跟随系统；切换为减少动态效果时已有数字及趋势立即收敛。Android 的非零时长比例作为原生适配保留。

概览隐藏或应用 STOP 时停止绘制循环并收敛，后台不读取。原桌面隐藏时合并新 render 请求，既有 RAF/WAAPI 交给浏览器时间线，没有相同的显式 STOP 收敛规则。两者必须分别记录。

仅剩不可见的零高 tween 时，保留 key 与逻辑动画状态，停止逐帧 Canvas 发布，在下一次 render 事件推进该时间线；零线持续静态。这是省去无可见结果帧发布的 Android 适配，不能称为实测原浏览器 WAAPI 结束回调时序。

## 实际验证与封存边界

统一回归完成：**182 个单元测试、158 个普通 Android 测试全部通过**，分别见 [unit-tests.xml](../evidence/desktop-source-motion/final/unit-tests.xml) 与 [android-tests.xml](../evidence/desktop-source-motion/final/android-tests.xml)。相关测试类的具体验证范围如下；通过数量不代替来源行为和真实页面断言：

| 测试类 | 主要判据 |
| --- | --- |
| [HeadlineNumberMotionTest](../app/src/test/java/dev/bing/tokenmonitor/HeadlineNumberMotionTest.kt) | quart 样本、调用至首帧 elapsed、首次有效/零、null 与 known detail 基线、中断、同目标、减少动态效果、精确大数。 |
| [HeadlineMotionScreenTest](../app/src/androidTest/java/dev/bing/tokenmonitor/HeadlineMotionScreenTest.kt) | 保留原四个方法名称，真实 HubScreen 更新/中断/800ms 周期、端点高度、系统比例、减少动态效果与后台返回。 |
| [HeadlineInitialMotionScreenTest](../app/src/androidTest/java/dev/bing/tokenmonitor/HeadlineInitialMotionScreenTest.kt) | 真实主页 cold、fixed unready→async ready1000ms、Projects 新已知总量静态与 Home 返回；leaf 验证零/null、抑制穿过 early return、known detail 后续基线。 |
| [TrendsBarMotionTest](../app/src/test/java/dev/bing/tokenmonitor/TrendsBarMotionTest.kt) | 时长/延迟上限、key、当前高度续接、同端点不同事件、零值、新/删除点、entry 消费、减少动态效果、目标坐标舍入。 |
| [TrendsBarMotionScreenTest](../app/src/androidTest/java/dev/bing/tokenmonitor/TrendsBarMotionScreenTest.kt) | 实际 Canvas 像素、静态零线、尺寸延续、STOP/减少动态效果、不可见零值；检查帧变化不提交 holder 或组件。 |
| [TrendsMotionNavigationScreenTest](../app/src/androidTest/java/dev/bing/tokenmonitor/TrendsMotionNavigationScreenTest.kt) | 实际 HubScreen 进入、详情/Settings 返回、数据续接、不同主视图返回及零额外读取。 |

本包实际证据位于 [evidence/desktop-source-motion/final/](../evidence/desktop-source-motion/final)，[build-checks.json](../evidence/desktop-source-motion/final/build-checks.json) 与 [package-verification.json](../evidence/desktop-source-motion/final/package-verification.json) 绑定上述 APK。8 份视觉报告包含 22 次测试方法执行、66 张公开 PNG，另有 [main-journey](../evidence/desktop-source-motion/final/main-journey/actual-navigation.txt) 的 4 张真实 HubScreen 进入/稳定/更新/完成阶段图。静态阶段图使用不同于桌面对照的公开 fixture，不证明 FPS。可从[实际预览](../evidence/desktop-source-motion/final/preview.html)查看图像；本地 gallery 的9个页面选择均加载通过，全部图片可读、无错误或外部请求，见[浏览器报告](../evidence/desktop-source-motion/final/gallery-browser-checks.json)。[证据清单](../evidence/desktop-source-motion/EVIDENCE_SHA256SUMS)共156个文件逐项核实，清单SHA-256为`2ab96639552af60f54890ab0f307aec3c5adc1b57271a7d56de1cb03bd239f7f`。

原始 22/82 证明位于 [reference/](../evidence/desktop-source-motion/reference)，`source/` 按字节保留既有 44 来源副本：42 张桌面捕获、55 份来源资产复用、167 个来源文件指纹。构建/lint 结果为 0 error、92 warnings、1 hint。封存器还核实 640 对语言资源、四份不变 assets、权限与签名、20 个 optional 测试隔离、54 个历史归档及自有测试应用状态清理/恢复，见[统一构建与回归记录](../evidence/desktop-source-motion/final/build-and-regressions.txt)、[归档核对](../evidence/desktop-source-motion/final/archive-checks.txt)和[验证摘要](../evidence/desktop-source-motion/final/validation-summary.json)。运行诊断结束后，普通 test APK 已按字节恢复，应用 APK hash 未变；没有用诊断包替代普通回归产物。

首轮针对性运行 17 项中有 4 个 failure，完整保留于 [targeted-first-run.txt](../evidence/desktop-source-motion/initial-review/targeted-first-run.txt)。其中 fixed live900 的夹具只更新了精简 `DeviceUsage`，完整 `periodDetails.today` 仍为500，计算300历史+500live=800符合当时输入；修正两份解析投影一致性后，Projects 1200 静态与 Home 返回继续由原断言检查。首次 commit 的手动时钟 barrier 和导航菜单/滚动 harness 也作了修正。一次暂停时钟下菜单滚动未完成，主动停止的运行保留于 [targeted-second-interrupted.txt](../evidence/desktop-source-motion/initial-review/targeted-second-interrupted.txt)及[navigation-paused-scroll.txt](../evidence/desktop-source-motion/initial-review/navigation-paused-scroll.txt)，不计为通过。随后一项 entry 断言失败来自 `TokenTheme` 内层有效系统比例0覆盖外层 fixture 的1，记录于 [navigation-system-reduction-fixture.txt](../evidence/desktop-source-motion/initial-review/navigation-system-reduction-fixture.txt)。修正夹具、时钟 barrier 与导航 harness 后，完整158项通过；没有删除断言，也没有为这些失败改动原生生产实现。

当前包窗口报告为 [window-diagnostics.json](../evidence/desktop-source-motion/final/window-diagnostics.json)，使用 **真实 Android Choreographer 时钟**，没有 Compose 虚拟时钟。三轮均完整、回调丢失0，每轮62个窗口样本、Hub区域提交1次、总量区域提交63次；下表只展示当前包，不使用旧包比较。

| 轮次 | 样本 | Hub / Total 提交 | Window cadence（Hz） | 超过 Window deadline | 完整 / 回调丢失 |
| --- | --- | --- | --- | --- | --- |
| 1 | 62 | 1 / 63 | 57.1875 | 1 | true / 0 |
| 2 | 62 | 1 / 63 | 58.0952 | 1 | true / 0 |
| 3 | 62 | 1 / 63 | 58.0952 | 2 | true / 0 |

测量范围为自有60Hz模拟器的公开负载与 Window 指标。cadence 不是物理显示 FPS；Hub/Total提交观察只证明这两个指定区域的范围，不证明整个界面树的所有提交，也不保证手机卡顿已解决。既有工具中的可选帧采样仍是诊断手段，不把固定33帧导出恢复为必做交付。

本轮没有物理 Xiaomi 13 Ultra FPS 或体验验证，没有 Lucky 公网 HTTPS 的安装后读取验证。整体桌面一致性也不以本轮动效测试代替用户验收。独立 Dashboard 整页、手动历史读取 receipt 及大 Hub 有界读取/兼容压缩缓存仍未实施，分别见 [DASHBOARD_PARITY_PLAN.md](archive/history/DASHBOARD_PARITY_PLAN.md)、[HISTORY_READ_RECEIPT_PLAN.md](archive/history/HISTORY_READ_RECEIPT_PLAN.md) 与 [HUB_CAPACITY_PLAN.md](HUB_CAPACITY_PLAN.md)。
