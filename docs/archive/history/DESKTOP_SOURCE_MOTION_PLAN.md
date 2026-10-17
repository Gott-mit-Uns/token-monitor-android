# NEXT_IMPLEMENTATION：桌面原有动效复刻

状态：45已实施、验证并封存安装包，结果见[实施记录](../../DESKTOP_SOURCE_MOTION_PARITY.md)。本文保留实施前的44源评审和计划：当时趋势Canvas直接绘制目标高度、headline首次有效值静态显示且计时晚于原版。45已修正这些源差异；44安装包与证据保持原字节，下面的原先差异不能当作45仍未完成事项。下一项整页范围见[Dashboard计划](DASHBOARD_PARITY_PLAN.md)。

用户已撤销此前单独提出的“主页 Token 变化时需要动效”要求。统一目标是复刻桌面 v0.68.0 本身已有的行为，不增加独立 Token 装饰效果，不把固定帧图集或每轮导出 33 帧作为必做交付。

固定来源：`../work/token-monitor-reference/desktop-v0.68.0`，提交 `5d2db368d8313415763860d594de00e46a663418`。下列行号均指该固定原始源码；Android 路径均相对于本项目根目录。

## 来源规则

| 行为 | 原始源码 | 必须保留的规则 |
| --- | --- | --- |
| 趋势当前高度与续接 | `src/electron/renderer/app.js:1891–1918` | 按日期/月 key 捕获当前显示像素高度，包括进行中的变换；起始 scale 为旧显示高度除以新目标高度，向 1 变化。420ms，`cubic-bezier(0.22,1,0.36,1)`，底部固定。已有 key 无延迟；新 key 或明确进入从 0 起，延迟 `min(index,14)×14ms`。 |
| 零值与外观 | `renderer/usageCharts.js:486–502`；`renderer/styles.css:6556–6570` | 全部点都生成矩形，包含高度 0 的点。已有零值转正仍是已有 key，延迟为 0；正值转 0 直接显示 0，不做退出动画。零值横线、日期轴、范围文字、四卡数值静态。按底部缩放整个矩形，圆角也随纵向变换缩放。 |
| 首次/主视图进入 | `renderer/app.js:340,4959–5014,6964–6977` | 首次 entry 标志为 true；真正切换主视图再次置 true。非空图渲染消费该标志；空图提前返回，不能提前消费。点击已选中的主视图不重播。 |
| 周期与详情入口 | `renderer/app.js:12030–12033,12048–12050,12809–12817` | 周期切换不把趋势时长改成 800ms，也不把已有柱统一归零。图表/↗ 打开独立 Dashboard，不改变主视图；Android 从详细历史返回不算趋势重新进入。 |
| 减少动态效果 | `src/electron/motionPreference.js:16–20`；`renderer/app.js:1712–1740,12945–12948` | on 立即收敛，off 忽略系统减少动态效果，system 跟随系统。趋势跳过新的 WAAPI 动画；切换为减少动态效果时 finish 已有动画，同时收敛 headline。 |
| 窗口隐藏 | `renderer/statsRenderScheduler.js:20–33`；`renderer/app.js:6573–6579,13033–13055` | 隐藏时合并新渲染请求，恢复后 flush 一次。隐藏处理没有显式取消 headline/trend 动画；已有 RAF/WAAPI 的隐藏行为交给浏览器时间线。不能将 Android STOP 收敛称为原版隐藏规则。 |
| headline 初次与计时 | `renderer/index.html:1557`；`renderer/app.js:309,1656–1704,6624–6651` | 初始 currentTotal 为 0，普通首次非零统计按 0→目标、1000ms、`1-(1-t)^4` 变化。周期主动切换为 800ms。调用时先记录 `performance.now()`，首个 RAF 已包含等待进度。先写较宽端点用于字体适配，不能保证用户先看到一帧字面的 0。 |
| 初次抑制例外 | `renderer/app.js:6627–6632`；`src/electron/main.js:2246–2251` | 显式抑制或减少动态效果直接显示目标。Windows 浮球展开后重建窗口是显式抑制的具体场景；普通冷启动不默认抑制。 |

## 趋势 holder 与绘制接入

拟新增一个不依赖 Compose 的 `TrendsBarMotion`，由 `DesktopHubScreen` 的主视图层持有，跨概览/详细历史保留。对外接口可采用 `replace(specs, plotHeightPx, fromZero, nowNanos)`、`frame(nowNanos)`、`settle()`；`specs` 包含日期/月 key、点的原始 index、目标高度占图表高度的比例。帧结果包含各 key 的目标几何和当前 `scaleY`，Compose 包装层返回绘制状态；仅 Canvas 绘制闭包读取逐帧状态。

实施边界：

- 替换目标前，按当前图表尺寸和当前 scale 捕获显示高度，再按源公式计算新 scale。保留所有点的 key 和 0 高度，不以“当前是否绘制正值矩形”判定新柱。删除的 key 直接消失，不增加退出效果。
- 进入请求由真实主视图切换产生，不由组件创建、详情返回或普通重组产生；非空有效图才消费请求。derived 数据未就绪与空图不构造假柱形。
- 按底部对最终矩形作纵向变换，保留源圆角、普通/最后柱透明度与独立 `chart-bar` 角色；不移动轴、卡片或零线。300×120 仍是坐标系，保留第 44 阶段弹性高度及短屏自然滚动。
- 尺寸变化只缩放当前几何和当前变换，不单独启动新动画；源 resize 处理 `app.js:12587–12589` 不重新渲染趋势。进行中的高度变化若遇到新数据，必须从当前显示高度续接。
- 源每次 `renderTrends()` 都重建 DOM；进行中再次渲染可能向相同端点重新计时。实现需对应实际数据/视图渲染事件，禁止把所有 Compose 重组当作该事件。静态相同高度按源阈值 `abs(scale-1)<0.001` 跳过。

复用现有 Android 减少动态效果偏好和生命周期约束：概览不可见或应用后台不启动绘制帧；STOP 可按现有平台策略立即收敛；恢复处理合并后的最新目标，详情返回不强制归零。这是移动端适配，须与原版浏览器后台行为分开记录。动画不增加 Hub 请求、SSE、服务或后台定时工作。

## headline 首次显示与首帧修正

当前差异位于 `HeadlineNumberMotionUi.kt:37–40,73–75` 和 `HeadlineNumberMotion.kt:18–24`：构造即用目标值；首次 null→有效值也直接 settle；启动时先等待一帧取得起点，再等待下一帧插值。

下轮保留同一个 headline holder、仅 `DesktopTotal` 读取帧状态、格式器/端点排版缓存和精确 Long 端点。通常首次有效统计应使用初始 0 作为源起点；实际数据尚未有效时仍保留现有占位，不为占位增加闪烁。首次目标为 0、减少动态效果、无效固定周期及恢复抑制各自保持明确状态，不能用“previous 为 null”统一吞掉首次动画。

固定周期恢复不能只按 period 名称变化推断时长。原 `app.js:6613–6622` 的 unready 分支取消 RAF、显示“—”，并在 6618 行明确将 `currentTotal` 设为 0；它不清私有 `numberAnimValue`，也不消费显式 initial suppression。周期 handler 在同步 render 返回后立即清 `periodMotionActive`（12032/12050），不会把它保留到异步 ready。所以 later ready 通常从 `currentTotal=0` 起、1000ms；若同一次周期点击已经 ready，则当次有效 render 捕获 800ms。detail 不支持而 period 已知时，同一提前返回分支静态显示并保存已知 period total，后续不能错误从 0 起。新来源脚本已执行这些真实分支与同步 handler 片段，见 `evidence/desktop-source-motion/reference/original-motion.json`。

holder 应区分“冷启动待首个有效统计”“固定周期暂不可用”“已知周期但详情不支持”“应用已有显示值后进入后台/缓存恢复”和“显式抑制”这些状态，避免 `retarget(null)` 丢失正确恢复起点，或让 `previousPeriod` 提前推进误决定时长。Android 的可信缓存首次呈现、STOP 后收敛及恢复是否抑制需要明确平台政策；桌面证据只证明显式抑制场景，不能推导所有缓存/后台恢复都该从 0 播放，或都应直接最终值。

启动/重定向时记录与帧时间同源的单调时钟，再请求首帧；首帧直接计算已经经过的时间，去掉仅为记录起点而等待的一帧。测试使用注入的同源虚拟时钟，不混用墙上时钟与测试帧时钟。继续保留普通 1000ms、主动周期切换 800ms、quart 曲线、从当前小数续接、同目标不重启，以及字体按较宽端点拟合；不强制添加可见的零值帧。平台 STOP 收敛与 Android 时长比例仍属于适配规则。

## 先验证规则，再验证真实页面与运行成本

| 范围 | 最小测试集合 |
| --- | --- |
| 原始来源对照 | 使用原模块和受控 DOM/WAAPI、RAF 时间戳运行真实函数，记录 capture 高度、key、duration/delay/easing 与首帧数字；测试调用实际函数，避免镜像公式作为唯一 oracle。 |
| 趋势纯状态 | 首次 entry；已有 key 上升/下降；途中重定向；新 key；已有 0→正值无延迟；正值→0立即收敛；删除 key；相同目标静止/进行中重渲染；index 14 之后延迟封顶；尺寸变化不重启；空图不消费 entry。 |
| headline 纯状态/时间 | 冷启动已有目标和 null→有效目标；首次 0；首帧有已过时间；普通/周期时长；途中续接；同目标保持；fixed-unready→later-ready 从 0/1000ms、同步 ready 为 800ms；detailUnsupported 保留已知 total；unready 不消费 suppression；可信缓存/后台恢复适配与显式抑制分别覆盖。 |
| 实际 HubScreen | DAY/MONTH/TOTAL/fixed、刷新期间更新、主视图进入、详情点击/返回、设置切换偏好、系统减少动态效果、STOP/恢复；使用公开 fixture 和固定日历时钟。确认数字/图表动效不产生额外网络读取，卡片/零线静态，短屏和大字仍完整可读。 |

运行测量使用真实帧时钟，先固定构建类型、AVD/设备、预热、数据和采样窗口，再比较新旧候选。分别记录 headline 区域的成功提交次数、趋势 Canvas 绘制失效范围，以及窗口 FrameMetrics 的 draw/total p95、真实 deadline、VSYNC cadence 和报告完整性；这些不是单个组件独占耗时。沿用 `DESKTOP_NUMBER_RUNTIME.md` 已说明的测量边界：scope 次数不代表整树，Window cadence 不代表手机呈现 FPS，固定少量预热不证明 JIT 收敛，模拟器结果不证明实体手机卡顿已解决。出现实际运行回退时先检查逐帧读取范围和文本排版，不以新增效果掩盖问题。仅按布局或行为验证需要保存选定截图/帧，不强制制作完整帧图集。
