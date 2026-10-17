# Token Monitor Android 手机适用能力差异审计

2026-10-13。对照实际安装的桌面 Token Monitor 0.69.0 与指定正式 Android 0.2.60/69 APK，遵循项目 AGENTS.md 的 Hub 查看客户端、移动端适配及只读审计边界。

核心统计、会话关系、额度、图表与主要数字/趋势动效已有广泛覆盖。本轮确认五项适合手机的剩余能力：会话身份复制、设置开关短过渡、手动数据导出、手机诊断报告、Android 自身更新入口。其中诊断和更新原先属于明确未纳入的桌面机制，手机应独立设计；不能把它们认定为已验收功能的回归。手机 Widget、图表手势与分享属于增强机会，不能一律称为桌面漏移植。

## 实际分析对象与证据强度

| 对象 | 实读结果 |
|---|---|
| 桌面应用 | /Applications/Token Monitor.app，Info.plist 版本 0.69.0 |
| 桌面 app.asar SHA256 | 8d82b426b74bcd1eb08db86e218d0f883b55095311fbeeb4a19c5b8843230895 |
| 桌面 renderer/app.js SHA256 | f32860f54e816a5a9357a038b96a00ffb53a31c3c58f5823f4c349de9984b291；REA module 观察与实包提取结果相同 |
| Android APK | /Users/bing/Downloads/token-monitor-android-0.2.60.apk；用户给出的 /bing/下载 路径在本机不存在，Downloads 内同名文件为实际目标 |
| APK SHA256 | 7d9ddcc4395d9d87b5daa6d3c98b26cf627083c4d31b0b8e75f754a4fa0eab5f，与正式发布回执完全一致 |
| APK manifest | dev.bing.tokenmonitor.nativeclient；versionName 0.2.60；versionCode 69；minSDK 26；targetSDK 37 |
| APK 内嵌 Git revision | a8f0c05b02e9ab3bf1ba3b01d9911992fe0fa6c5 |
| 本地源码 | HEAD 37319750e17d08c76897396a455b8281f59888c8；app/src/main 与 app/build.gradle.kts 相对 APK 内嵌提交无差异 |

REA 已完成桌面 JavaScript 静态分析与 APK 归档检查。Android JADX MCP 引擎未配置，未执行 APK 类/方法反编译；Android 实现证据来自上表对应的产品源码。未启动目标应用、安装 APK、访问生产 Hub、读取用户配置或修改生产服务。没有将静态存在性视作运行时或真机验收。

REA 桌面广泛语义图受资源限制，不能据图中未出现某关系判定功能不存在。本轮采用定向实包代码核对。三个 unpacked 原生文件的 ASAR 声明校验值与实际文件不一致，按 record-and-continue 保留差异；codesign --verify --deep --strict 检查退出码为 0。打包后签名可能造成此类差异，但本轮没有证明其成因，也没有执行这些原生文件；功能结论使用归档内 JavaScript/CSS。

REA 摘要、目标指纹及限制见 [证据记录](../../../evidence/rea-mobile-gap-20261013/provenance.json)。桌面提取代码位于 work/rea-mobile-gap-20261013/desktop。HTML 控件清单使用 markitdown 转换到 work/rea-mobile-gap-20261013/desktop-controls.md；控件属性与动效参数仍按原始代码核对。

## 确认未覆盖且适合手机的能力

### 会话身份一键复制

桌面 app.js:4861–4874 的 sessionIdLine 创建身份文本与复制按钮。Android SessionListDisplay.kt:71 已取得会话身份，SessionListUi.kt:138–142 的只读摘要显示说明和 ID，但没有选择文本或复制入口。

建议在会话摘要增加完整身份及独立 48dp 复制触点，复制当前会话身份而非截断标题或记账路径；用原始 row.session.sessionId/可信回退与既有格式器明确对应。分组摘要应选择具体成员，不把多个会话拼成一个 ID。成功反馈保持短小，缺失字段不编造。

现有 Hub/解析数据足够，不需要 NAS 或 Agent 修改。建议优先级：高收益、低实施风险。

### 设置开关的短过渡动效

桌面 styles.css:618–654 的设置 switch 明确使用背景/边框 180ms ease，圆点 transform 180ms cubic-bezier(0.22,1,0.36,1)。Android DesktopSettingsUi.kt:131–142 直接由 checked 改颜色和圆点 x 坐标，没有插值状态。

这是确认的小型动效差异。建议只补视觉过渡，设置语义值立即提交；复用现有减少动态效果和系统动画比例策略。禁用、页面隐藏或进入后台立即收束，不增加网络读取，不把所有刷新或数字更新都附加装饰动画。建议优先级：低成本局部打磨。

普通设置下拉不列为缺项：桌面 select-control-popup 的 CSS 和 selectControl.js 没有与主视图/周期菜单相同的进入退出过渡。Android DesktopOptionMenu 的 animate 默认 false 与此一致；主导航及周期已有的菜单动画应保留。

### 手动导出统计和历史

桌面 app.js:12571–12583 提供手动导出，preload.js:128 接到 export:now，main.js:7974–7983 选目录后调用写出；main.js:6335–6360 导出 periods 与完整 history 的 CSV/JSON 文件组。桌面完整 history 不可得时返回失败，不写成完整导出；devices/limits 不属于该文件组。

Android 产品源码未发现对应业务导出/系统文件保存或统计分享入口。HubData 已有 detailIncomplete、sessionsOmitted、projectsIncomplete、exact、historyRevision 等完整性信息，HubViewModel 保留历史读取状态，可支持明确范围的只读导出。

建议先做用户手动选择周期/设备范围的 CSV 或 JSON，经系统文件选择器保存；写明来源、更新时间、币种、日期口径及缺失/裁剪情况。完整历史不可得时提示或明确导出“当前可用范围”，不能宣称等价于桌面完整导出，不从会话明细重新累加替代权威总量。自动后台导出及其 30秒至60分钟定时参数不移植。建议优先级：中。

### 手机诊断报告预览和复制

桌面 diagnosticsPanel.js:126–174 提供生成、预览和复制；main.js:8035 起提供报告及完整性信息。Android AboutSettings.kt:31–46 显示自身版本、桌面参考、Hub 构建状态和许可；DeviceDetails.kt 已有 freshness、源版本和健康诊断码，尚无统一报告入口。

既有 DESKTOP_ANDROID_SCOPE.md 将桌面诊断机制列为不复制，因此这属于手机职责下的独立补充，不是移植错误。建议整理 Android/客户端版本、Hub 构建、错误类别、最近有效缓存年龄、网络/SSE 状态与系统字号/减少动态效果等已有状态。先让用户预览，再复制或分享；不包含连接地址、凭据、私有正文、会话标题或桌面 collector 扫描信息，不为报告扩大生产读取范围。

多数信息已有，无需新 Hub 协议。建议优先级：中高，便于后续定位手机网络恢复和界面问题。

### Android 自身更新检查与发行说明

桌面 app.js:13049–13061 有检查更新及发行说明。Android AboutSettings.kt:33 只显示安装版本；:42–44 的 updateAvailable/legacy 属于 Hub 构建比较，不能当作 Android 客户端更新功能。

既有范围文档明确不复制桌面更新机制，Android 应独立实现。最小方案是关于页提供本 Android 仓库的发行页及当前版本说明；后续才考虑显式前台检查最新版本。需要处理网络失败、仓库访问、版本比较及正确发布资产。安装继续交给 Android 系统，不移植桌面自动安装器，不悄悄后台查询，也不收集桌面更新凭据。建议优先级：中。

## 手机适配增强机会

| 机会 | 当前状态 | 最小建议与数据边界 | 优先级 |
|---|---|---|---|
| Widget 额度卡与 7天简报 | HubWidgetProvider.kt:25–30 仅今日 Token/费用/更新时间；:39 起明确只读加密缓存 | 可用缓存额度显示剩余与缓存年龄，7天须复用权威周期或已缓存历史投影；缺数据不造零。点按打开前台刷新，不增加后台 HTTP/SSE | 高 |
| 图表长按后拖动逐日查看 | Dashboard/记录/热图已有点击明细，记录柱图已有横向滚动；未发现拖动十字线 | 长按锁定后拖动，展示同一日期数据浮层；避免抢纵向滚动。现有历史足够支持日粒度，不推导小时粒度 | 中高 |
| 自选日期范围 | DashboardUiState.kt:73–76 只接受固定 ranges | 使用已可用历史选择起止日期；保留固定周期及完整性声明，不改变总量口径 | 中 |
| 分享当前周期简报 | 无统计 ACTION_SEND 入口 | Token/费用/周期/简单图表；默认不带设备名、账号、会话标题或地址，先预览；属于导出的手机表达 | 中 |
| 长按拖动排序与位置过渡 | 已有排序，ToolSettingsRow.kt:15 明确手机菜单替代拖动；:28–31 有上/下移 | 长按把手后拖动并提供轻触感，保留菜单支持无障碍/大字体；不能把缺少拖动说成缺少排序 | 低 |
| 前台额度越阈值提示 | 已有低额度高亮，未发现独立提示通知 | 前台取得新有效额度时单次提示，处理旧缓存和重复事件；后台实时提醒需独立推送方案，不加轮询 | 低 |

触觉反馈可只用于复制确认、图表精确选点或进入拖动，尊重系统设置，不对每次 SSE 数字变化振动。它是 Android 新增交互，不是桌面动效漏移植。

## 已支持的能力与参数

本轮未确认新的大类“显示参数漏移植”。当前源码已覆盖八入口及显隐排序、Home 模块顺序、工具隐藏/固定/顺序、会话关系与混合排序、输入等待/live 可选字段、额度单组双周期、金额/汇率、精确与缩写 Token/本地单位、速度与费用燃烧模式及设备选择、上下文 used/remaining、额度已用/剩余/低额度高亮/账户条数/来源/邮箱遮蔽、主题/厂商颜色/字体/背景图片及减少动态效果。

主数字、趋势柱续接、Dashboard/历史图表、展开收起、主视图与周期菜单、运行状态等动效已有实现。SessionListUi 单行省略文本可通过现有摘要查看，不把桌面的鼠标悬停滚动强搬成手机自动跑马灯。

上述结论是定向静态核对，不等于所有参数组合或全部设备均通过当前版本真机验收。旧 NEXT_TASKS 中的排序、设备名、图标、构建信息、字体和网络修复历史条目应与最新交接合读，不能当成未完成清单。

## 不应由 Android 补齐的差异

- zero-token 本地会话、nativeSessions/nativeProjects、本地实时 overlay 和 Grok 跨历史身份未由当前合同完整上传；Android 不能扫描桌面文件或从近期 Token 猜状态。
- waitingForInput/liveActivity 已有消费；通用 waiting 不足以区分“等待批准”，需要 Hub 明示原因，不能假装识别。
- 桌面日志/采集器/Tokscale、Windows Dock/托盘/悬浮窗口、开机启动/后台扫描、NAS Hub 托管与数据修复均在 AGENTS 排除范围。
- 外部 Codex 重置预测依赖额外第三方公网查询，既有范围文档明确排除；不能当作已有 Hub 参数遗漏。
- 当前新版本手机蜂窝与 Wi-Fi 切换、锁屏/厂商后台、帧耗时仍有验证欠账。0.2.60 已有网络恢复实现，不能以 0.2.59 历史失败声称它没移植，也不能以源码存在声称新真机已通过。

## 建议实施顺序

先用一个小任务补会话 ID 复制；再独立做缓存额度 Widget，提升手机随手查看的价值。开关短过渡可作为局部动效任务。手机诊断、手动导出和版本入口分别定义范围与验收后实施，不打包成全局重构。

本轮只保存分析和证据，不递增版本、不构建或发布 APK。下一步属于建议，用户选择后再开发；未修改 AGENTS、现有功能矩阵或历史验收记录。
