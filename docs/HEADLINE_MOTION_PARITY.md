# 0.2.62 / desktop 0.70 update

Default token animation is now roll; count remains the original interruptible timeline below. Roll uses one finite 650ms draw-only timeline per clipped visible reading, bounded two layers per character, stable digit slots for the selected user font, and native Text semantics. Unknown/first available values and mode switches settle; wrapped/unsupported-locale readings retain native Text. Amounts and static widgets are not animated. Source and validation: HANDOFF_0.2.62.md / DigitRoll070ScreenTest. Older count-specific tests explicitly choose count without weakening their value/timing assertions.

# 主页Token总量变化动效

当前用户指令：撤销此前额外强调该动效的要求。该功能按统一桌面复刻目标规划，不再每轮固定导出或强调33帧。用户反馈效果不佳、帧率低，须检查实际运行表现；下面33帧历史证据为32ms采样，不证明运行帧率或流畅度。当前继续核对固定桌面原始 requestAnimationFrame 数字过渡与安卓实现。

当前实现与真实运行指标见 [桌面数字复刻与运行测量](DESKTOP_NUMBER_RUNTIME.md)。该说明记录状态读取隔离和仍存在的源差异，窗口指标不等于实体手机 FPS。

## 历史记录

以下各版的固定帧导出、当时的用户要求及验收措辞保留为历史范围，已不构成当前额外要求。

0.2.41-dev 同包重新记录 33 帧实际主页 Token 动效和后台返回不重播断言，入口 evidence/settings-inline/final/motion-frames/preview.html；150 单元、135 普通 API36 持续覆盖连续更新、周期和减少动画。设置页排列和滑轨修正不改变 Token 动画规则。

0.2.40-dev 同包重新导出 33 帧实际主页 Token 动效，并通过后台返回不重播断言。入口 evidence/background-image/final/motion-frames/preview.html；150 单元、135 普通检查继续覆盖连续更新、周期与减少动画。背景图片独立层不改变数字动画规则。

0.2.39-dev 同包重新导出 33 帧实际主页 Token 动效及后台返回断言，入口 evidence/font-fallback/final/motion-frames/preview.html；148 单元、132 普通检查继续覆盖连续更新、周期与减少动画。字体列表回退修正不改变数字动画时长与续接规则。

0.2.38-dev本包重新导出33帧实际主页Token动画与后台返回断言，播放/逐帧入口evidence/menu-motion/final/motion-frames/preview.html；146项单元/131项普通回归覆盖连续变化、周期及减少动画。

0.2.37-dev本包重新导出33帧实际主页Token渲染与后台返回断言，公开播放/逐帧入口evidence/option-menus/final/motion-frames/preview.html；最终144项单元/129项普通回归继续覆盖连续变化和减少动画。

0.2.36-dev本包重新导出33帧实际主页Token渲染，连续变化、后台返回与减少动态效果在143项单元/129项普通回归及动效专项中继续验证。公开播放与逐帧入口为evidence/navigation-menu/final/motion-frames/preview.html；与本包SHA绑定，未使用旧截图代替。

0.2.35本包重新录制33帧实际渲染，播放及逐帧入口为evidence/home-geometry/final/motion-frames/preview.html；后台返回不重播通过，动效实现未改。

0.2.34本包重新录制33帧实际渲染，播放/逐帧入口为evidence/detail-footer/final/motion-frames/preview.html；后台返回不重播检查通过，主页动效实现未修改。

0.2.33本包重新录制33帧实际渲染，播放和逐帧入口为evidence/full-renderer/final/motion-frames/preview.html；后台返回不重播检查通过，动效实现未修改。

当前0.2.32-dev/code41同包33实际帧在`evidence/tool-settings-controls/final/motion-frames/preview.html`。143单元、129普通API36和16份最终页面报告通过；工具设置层级与标题调整保留更新1000ms、周期800ms、连续续接、准确整数、减少动态效果及后台返回规则。

当前0.2.31-dev/code40同包33实际帧在`evidence/list-interaction/final/motion-frames/preview.html`。143单元、129普通API36和16份最终页面报告通过；工具/模型列表与七组设置调整保留更新1000ms、周期800ms、连续续接、准确整数、减少动态效果及后台返回规则。

当前0.2.30-dev/code39同包33实际帧在`evidence/main-navigation/final/motion-frames/preview.html`。143单元、129普通API36和12份页面报告通过；主目录调整保留更新1000ms/周期800ms、连续续接、准确整数、减少动态效果及后台返回规则。

当前0.2.29-dev/code38同包33帧在`evidence/average-rate/final/motion-frames/preview.html`。142单元/127普通API36和10份页面报告通过，主页数字动效及三态减少动态效果、后台返回规则保留。标题平均速率使用周期计时字段即时显示，不增加读取，见AVERAGE_RATE_PARITY.md。

当前0.2.28-dev/code37同包重新导出33实际帧，入口`evidence/status-motion/final/motion-frames/preview.html`。141单元/125普通API36和10份页面报告通过，更新1000ms/周期800ms、连续续接、准确整数、三态减少动态效果及后台返回规则保留。新增首页运行图标与速率提示见STATUS_MOTION_PARITY.md。

当前0.2.27-dev/code36已用同一安装包重新导出33帧，播放/逐帧入口为`evidence/session-parity/final/motion-frames/preview.html`。139项单元、123项普通API36及六种会话配置通过；主页数字动效仍为更新1000ms/周期800ms，连续更新从当前值续接，减少动态效果及后台返回收束。系统比例在主题中共用一个观察器。详见SESSION_PARITY.md。

0.2.25同包重新导出33帧，位于`evidence/settings-management/final/motion-frames/preview.html`；本包132单元/119普通API36和16最终页面报告继续保留动效验收。更新1秒、周期切换0.8秒、减少动态效果和后台返回行为未因设置重做而改变。

0.2.24同包重新导出33帧实际Android渲染，预览位于`evidence/connection-transaction/final/motion-frames/preview.html`。本包130单元/114普通API36包含动效和减少动态效果检查。更新1秒、周期切换0.8秒，连续收到新值从当前数字继续；后台收束、前台不重播，后续版本继续保留此验收要求。

当前实现保留主页Token变化动效及system/on/off本机三态设置：system跟随Android，on直接收束，off保留过渡（即使系统动画比例为0）。当时每次封存均重新导出同包实际帧；该固定导出规则现已撤销，历史版本实录不冒充当前验证。

当时用户明确要求主页Token变化时有动效，该额外要求现已撤销。0.2.16-dev直接实现桌面v0.68.0的`app.js/animateNumber`、`animateTotalNumber`及总量渲染逻辑，不引入新的动画库或网络请求。

- 总量变化使用原始`1-(1-t)^4`缓出曲线，持续1000ms；切换主统计周期使用800ms。
- 递增、递减和归零都支持。中途收到新值，从当前显示的分数值继续，旧动画无法覆盖新目标；相同目标的重绘不会从头播放。
- 完整数字按较宽的端点拟合字号并在本轮动画中锁定，完成后重新拟合。≈简写保持最终目标读数，与桌面一致；费用和详情继续来自当前真实周期。
- 首次读取、缺少数据及关闭动画直接收束。Android系统“移除动画”/动画时长比例为0时跳过动画；设置运行中变化也立即生效。独立的桌面三态外观设置尚未移植，当前使用Android系统偏好。
- 进入后台收束到最终值，不在后台请求显示帧；返回前台不重播后台期间的动画。没有额外SSE、定时读取或后台工作。
- 原始桌面使用JavaScript Number；安卓在插值时保留12位小数和Long整数端点，超过Double精度的最后一位也能正确变化。连续中断不会无限积累小数位；3,000次中断检查通过。

`tools/capture-headline-motion.cjs`直接运行固定源码中的原始动画，使用公开样例、虚拟帧时钟和文本DOM替身，保留5组过渡、中途转向及减少动态效果参考。`HeadlineNumberMotionTest`核对原始曲线采样、转向、缺失数据、取消及大整数；`HeadlineMotionScreenTest`在安卓实际总量组件上核对周期时长、连续更新、字号、系统动画设置、后台恢复和实际渲染帧。

连续帧由同一个安卓安装包实际渲染，只有公开合成数据；使用测试帧时钟32ms采样及正常动画偏好，编码后的预览保留这些帧，不生成或补画中间画面。它证明画面与过渡采样，不作为实体手机帧率录像或流畅度测量。

最终安装包0.2.16-dev/code25，SHA256 `42f22d374b5ddc94be81bd180a9da020b5eb4771cba9d320f6d3fa94358ed360`。5项动效单元检查和4项安卓动效检查随119/85整组通过；动效专项六种配置通过，其中真实Android动画时长设置没有使用测试覆盖值。33帧、可播放/拖动逐帧的`preview.html`及无损动画`headline-count-change.png`位于`evidence/headline-motion/frames`。

最终升级、运输与封存证据见PROJECT_DEVICE_PARITY.md。本轮动效完成不代表其他页面、Lucky外网、物理手机或整个目标完成。

0.2.17新增原始system/on/off本机选择及V13保存。on立即收束，off保留过渡，即使Android全局比例为0；system跟随系统。三态普通/六配置检查通过。新安装包与33帧位于evidence/settings/final/motion-frames，绑定SHA256 `28ea2d941132ec634e6e9664ebff5518342e09e6b4c4ea7ace04b9470394762d`。设置依据见SETTINGS_HIERARCHY_PARITY.md；0.2.16的安装包与证据保留。


0.2.18保留相同动效实现及三态选项，随122/89整组通过，并用本包重新录制33帧及后台返回不重播检查。文件位于evidence/edit-controls/final/motion-frames/preview.html，APK SHA256 `a66881850eb17bb1c199bc187f4f8867a67d0239c7535bc137e1be33e851a582`。不是实体手机帧率测量；本轮其余验证见EDIT_CONTROLS_PARITY.md。


0.2.19保留动效及三态，当前包33实际帧和后台返回验证位于evidence/list-controls/final/motion-frames/preview.html，绑定SHA256 `e84a772909d1737198b5b6e3ee281180c69b7b5503abbfbb12ed426e302df078`，随122/89普通回归通过。
