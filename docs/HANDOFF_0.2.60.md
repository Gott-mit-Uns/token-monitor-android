# 0.2.60 联合更新验收与发布交接

2026-10-13，Asia/Shanghai。接续 `origin/release/0.2.60` 的 `6783284c6f4c116e70c072fb3c42026c458edb83`，使用独立 worktree；原 Mac main 及未跟踪 AGENTS-original.md 保留。候选统一为 **0.2.60 / code69**，正式包名仍为 `dev.bing.tokenmonitor.nativeclient`，不使用 tokenMonitorIsolatedTest。

## 交付变化

- 网络更换时取消旧 HTTP/SSE，等待同一网络 capabilities/link properties 到齐；仅 NETWORK 在前台额外最多三次受控重试。ready 不等同 Android VALIDATED、DNS/TCP/TLS 已成功。
- Mac 定向 Red 进一步复现已关联 LAN HTTP → 非 LAN pending → ready 时 HTTPS 转换丢失恢复预算；只修复 enforceSecureEndpoint 对同 scope 恢复上下文的保留，认证/TLS 封锁、关联准入和旧请求隔离不变。
- 冷进程启动显示既有图标一次；已配置等待初始终态后 Home，未配置进 Hub 设置。Activity 重建与普通后台返回保留页面，新的进程忽略旧 Settings 路由。
- 默认界面/总量用 Token UI（Inter + Noto Sans SC 常用中文），对齐数据用 Token Data（JetBrains Mono），400/700 两字重；保留明确 System、monospace、自定义字体偏好。旧“App”预设跟随新的默认字体，存储字符串不重写。
- Dashboard 刷新图标绘制为 8.3×8dp，48dp 独立触点保持。
- 用户追加的主页固定周期日期说明删除已整合；正常完整周期不插入空状态项，趋势页仅在有数据缺失提示时扣状态高度。算法、缺失原因、读取按钮及设备来源窗口保留，侧任务证据见 HANDOFF_HOME_PERIOD_ROW_0.2.60.md。

## Mac 实际执行证据

任务专用全新 Android API36 模拟器，JDK17/SDK37/Gradle9.8.0 wrapper。没有安装到实体手机，没有清除或卸载有数据应用；只使用公开合成 fixture 与隔离测试存储。

| 门槛 | 结果和边界 |
|---|---|
| Release 单元 | 432 passed / 0 fail/error/skip，81 XML；包含新双入口 pending-ready Red/Green |
| Lint | 0 errors / 98 warnings / 1 hint；公开依赖版本提示与 Windows 报告不同，不倒填原计数 |
| 许可 | 67 运行依赖通知检查通过；四 TTF 指纹、字重、覆盖和三份 OFL 完整文本核对 |
| 冷启动/导航/字体/Appearance | 原选定 17 项分阶段验收，保留首失败；最终字节再定向运行并记录在 VALIDATION.md |
| 网络/TLS/SSE/缓存 | 31 项受影响 Android 回归通过；严格 Conscrypt、认证封锁、端点准入、连接池离主线程关闭、取消及加密记录边界 |
| 新恢复设备回归 | 3 项真实 Android main/IO/Keystore/回环 TLS 通过：ready 等待+首次 health 失败恢复、stats 失败保留缓存并恢复一条替代 SSE、三次预算耗尽且重复通知不重置；网络 scope 与故障是合成的 |
| 实际系统窗口 | 320/411dp × 系统字号1.0/2.0 × 浅深主题，8组56项通过；最大系统组叠加最大 App 字号；字体检查每组均最大 App 字号，导航/语言另测中英及标准/最大 App 字号 |
| 受影响 UI | 主趋势、Dashboard 柱/K线/概览、记录柱/K线/折线/行、轴完整及无重叠、Home大数值、八入口/语言、48dp刷新和设置中心/外缘真实触摸独立 |
| 字形 | 混排、粗体、金额/小数/负号、动态长文本通过；系统与 Token UI 对龘(U+9F98)/𠮷(U+20BB7)在400/700均 hasGlyph=true；没有声称所有未知Unicode必然受每个厂商系统支持 |
| 正式签名升级 | GitHub 0.2.59/68 → 0.2.60/69 覆盖成功；UID、1份合成加密Hub缓存和4份偏好文件SHA保持一致；连接地址/alternate/LAN关联、缓存/history/metadata、App/System/mono/custom四组字体、字号/主题/配色/语言保留 |
| 真实生产Activity/进程 | 未配置设置页；公开默认缓存配置的浅深主题Home；Settings经实际Activity.recreate/普通后台返回保留；另有外部后台杀进程与force-stop的PID/采样截图证据。默认公开进程fixture使用空认证字段，仅验证本地配置终态/缓存路由，HTTP慢成功/失败/超时由真实回环/延迟fixture测试独立验证 |

证据位于 `evidence/unified-060/`：首失败与各轮应用/测试 APK 指纹、unit-results、core-run1、ui-harness-run2/run3、font-window-diagnostic-run2、matrix-run2、network-cache-run1、device-network-recovery-run1、upgrade、cold、process-restart。矩阵保存真实系统窗口参数；图表截图每组现场生成，累计尺寸表另保留原文和当前14行切片。

## 首失败与修复

- 离线缺 Robolectric 缓存导致首构建工具失败，在线补齐后成功，保持JDK17。
- 新UI测试调用 test rule.setContent 覆盖已建 MainActivity 内容，首17项有4项 harness失败；按既有Activity.setContent惯例或新的 restoration root修复。随后已配置Settings错误期待默认连接表单导致1项测试失败，改为准确Settings入口断言，首次未配置仍强检连接表单。
- 实际系统1.0短数值 Text 的语义重建使用父最大宽度，而节点为内容自然宽度，didOverflowWidth假阳性；断言前截图文字完整。测试使用分配窗口宽度使绘制/语义约束一致，原溢出/字体/点击断言全部保留。
- 新双入口 pending-ready 用例先失败再通过；没有把原失败XML改写为成功。
- 一次并发Gradle写同unit binary目录发生EOF/NoSuchFile工具失败，记录保留；最终串行完整432项通过。
- 最终封包复跑17项时 Appearance 有1项假设重建后自动显示Settings，在已有公开缓存时实际按约定回到Home；测试改为明确进入Settings后继续原断言。定向复跑另暴露真实System.nanoTime动画与Compose虚拟时钟不一致，测试等待idle失败；两个无动画断言的字体/偏好fixture设reduceMotion=on后2项通过，全部原断言保留。两次首失败日志、截图及当时APK指纹保留。
- 最终整组run2中Appearance两项通过，冷启动页面与共享页导航两项出现同样虚拟/真实时钟等待失败；仅将两个无动画断言的合成fixture明确reduceMotion=on，保留路由、键盘导航、菜单布局和零额外读取检查，run2记录与当时APK另存。

## 字体体积与最终封包门槛

四派生字体原始合计5,645,964 bytes不是APK增量。初期同版本/正式签名Release测量为3,694,729 bytes，但worktree与archive的AGP VCS错误字段不同，仅作前期证据。

正式交付必须从相同最终提交的两个普通本地Git clone构建，detached HEAD让AGP正确内嵌源码SHA。基线只反向移除原17项字体资源/角色/通知差异，保留网络、冷启动、刷新及后续最小修复；版本、签名和Release参数相同，不安装基线。最终两包VCS字段须相同，APK增量≤5,000,000 bytes；最终精确字节、SHA、新联合最终APK完整定向回归、窗口矩阵及再安装结果以四附件 `release-manifest.json` / `VALIDATION.md` 为准。

用户在首次冻结候选53项通过后追加固定周期显示修改，已要求一起发布0.2.60/69；原冻结候选的APK指纹不能代替新联合最终包。侧任务4项周期单元、13次UI执行与首Red保留；主任务联合补验、最终全部回归和窗口矩阵统计以四附件为准。

本文件写入产品提交时，最终提交封包、唯一annotated Tag、四附件草稿回读和正式发布仍须按既定顺序执行；不得把此源码文档作为已发布回执。发布完成后在 GITHUB_PUBLICATION.md 补只改文档的回执，固定Tag与APK不重建/覆盖。

## 必须保留的实体设备边界

Windows 原0.2.59/68 Xiaomi13Ultra：覆盖/缓存、Lucky HTTPS Wi-Fi/蜂窝读取、离线、五次前后台、两主题/大字号通过；**蜂窝→Wi-Fi自动恢复首次及两次复测失败，手动刷新可恢复**。原12份性能测量保留慢帧结论。

Mac目前未连接真机。新版蜂窝↔Wi-Fi五轮、真实Lucky/家庭LAN、连续两分钟锁屏、厂商深度后台、真机后台请求/SSE数及性能/FPS均未验证；不改写旧失败，不宣称未测量的性能提升。未修改 Hub/Lucky/NAS/Agent/路由器/桌面生产服务，凭据不进日志/截图/报告。完成本次一个Release及回读后停止，不继续历史待办。
