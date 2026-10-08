# Token Monitor Android 0.68 桌面代码对齐评估

日期：2026-10-09。桌面基线：Javis603/token-monitor v0.68.0，提交 `5d2db368d8313415763860d594de00e46a663418`。安卓当前：Gott-mit-Uns/token-monitor-android v0.67.0-hub.3，提交 `0bb5efe`。

本轮为代码审查与版本范围建议，没有修改 APK、签名、NAS 或 Hub。读取桌面 renderer、主进程默认设置、字体、主题、货币、模型别名、速率、Hub 路由、统计模型及导出说明；与安卓 UI、DTO、设置和小组件对照。桌面字体源码的 Git blob 与官方 v0.68.0 在线版本一致。

## 0.68 的定位

安卓独立对齐桌面 0.68，不等待安卓上游。保留原生 Compose 实现。显示内容与计算定义对齐；触摸、返回、安全区、系统字号和后台生命周期采用安卓方式。版本建议 v0.68.0-hub.1 / 680101。应分别记录桌面协议基线与安卓代码来源，不能把未合并的安卓上游标成 0.68。

## 不进入手机复刻范围

| 桌面能力 | 手机端取舍 | 保留的查看能力 |
|---|---|---|
| 文件扫描、Tokscale、监听日志、WSL、Codex Dots 采集 | 不实现 | 查看采集端已经上报的统计与来源标记 |
| 扫描目录、重扫、删除本地归档 | 不实现 | 读取 Hub 历史及不完整提示 |
| 供应商登录、API Key、Cookie、账户发现与额度探测 | 不实现 | 多账户额度、余额、更新时间 |
| 切换电脑当前 Codex 账号、workspace、登录授权 | 不实现 | 查看共享账户标签和统计 |
| transcript 逐条提问、回复和工具调用正文 | 现有 Hub 不同步，无法仅靠手机实现 | 已共享的标题、状态、上下文与 Token 汇总 |
| 托盘、菜单栏、置顶、最小化、隐藏 Dock 图标 | 不实现 | App 与安卓桌面小组件 |
| Edge Dock、悬浮小窗、悬停把手 | 下一版不增加悬浮窗权限 | 保留小组件；点击可查看完整页面 |
| 全局快捷键、桌面拖窗、任务栏避让、电脑开机登录启动 | 不实现 | 系统返回、图标和小组件入口 |
| Discord Rich Presence | 不实现 | 不广播使用数据 |
| iCloud Drive 同步、iOS Scriptable/Widgy、原生 macOS 小组件桥接 | 不实现 | 当前 NAS/电脑/HTTPS Hub 与安卓小组件 |
| 手机运行 Hub、接收 Agent 上传、删除共享设备 | 不实现 | 查看设备与同步状态 |
| PUT 共享订阅、修改共享同步内容策略 | 下一版保持只读；不能说服务器没有这些接口 | 查看订阅与共享内容能力 |
| 手机自定义模型单价重算共享费用 | 不实现独立的价格权威 | 显示采集端/Hub 已算好的费用、币种换算与未定价警告 |
| 定时自动导出及无操作后台轮询 | 不照搬电脑调度 | 保留用户主动导出及既有小组件显式 Live 行为 |
| Codex 第三方重置预测 | 不额外增加第三方请求，现有 Hub 没有对应专用字段 | 官方重置时间；将来 Hub 提供预测时再支持读取 |
| 原生系统玻璃、真实透明桌面窗口 | 不照搬 Win/macOS 系统 API | 手机内部背景、透明度层次和主题 |

## 功能对照与缺口

| 项目 | 桌面 0.68 | 安卓 hub.3 | 建议 |
|---|---|---|---|
| 总量、费用、周期、工具、模型、设备、项目、会话、额度、趋势、状态 | 完整主要查看入口 | 对应入口已存在 | 保留并校准，而非重写 |
| 设备名称 | deviceId 优先，其次 hostname | 默认一致，但本地别名仍覆盖，铅笔仍存在 | 按用户要求取消改名入口及别名应用；保留旧别名数据供回退，不继续覆盖 Hub |
| 费用币种 | USD/CNY/HKD/TWD，自动及手动汇率，每币种回退 | 相同币种，仅一个当前手动汇率 | 补每币种保存、精度及符号；自动汇率为可选项，不强制第三方网络 |
| 费用精度 | USD 小于10用4位，其余2位；其他币种小于1用4位，其余2位 | NumberFormat 默认通常2位 | 对齐精度，避免低费用显示为零 |
| 未定价 Token | 总量/分类有 unpricedTokens 及告警 | DTO 未覆盖这些字段 | 优先补读取与警告，避免把缺失价格解释为真实零费用 |
| 模型别名 | 规范化匹配、多个来源、duplicates/prefix 自动分组及解析 | 精确键、一次映射、多来源手动合并 | 复用语义并测试链、冲突、循环和回退；保留当前用户映射 |
| 工具行自定义 | 显示、置顶、拖动排序 | 隐藏及 Token/费用排序 | 补置顶与手动排序 |
| 主页模块与视图 | 显示/排序及拖动 | 显示/上下调整 | 触摸拖动并保留上下调整辅助操作 |
| 额度显示项目 | 每提供方可选择窗口/用量项目 | 首页最多3账户、每账户最多2窗口 | 补显示项目选择，缺失与零明确区分 |
| 账户顺序与重点账户 | 可配置展示 | 按可用额度排序 | 增加固定显示偏好，不靠风险排序代替用户选择 |
| 会话上下文方向 | used/remaining 设置 | 主要已用读数并显示剩余 | 补方向选项 |
| 会话多模型说明 | 模型 Token/占比弹层 | 模型名及汇总，Hub未提供拆分时无法补 | 有来源才显示，点击替代悬停 |
| Codex Dots | 归入 Codex，带 observed-only 来源含义 | 总量可读取，但 usageSource/usageCoverage 未保留 | 补来源/范围标签，不自造独立工具归类 |
| 实时速率 | timedToken 计数差值追踪，速度与消耗切换、scope | 读取总计计时字段，显示会话平均速度 | 增量追踪、重置/离线/空闲边界；补 modelThroughput 等必要字段 |
| 订阅详情 | 续费/到期、已订阅时长、topup、回本等 | provider/plan/金额/interval 简表 | 补只读 DTO 与信息，不增加 PUT 编辑 |
| JSON/CSV | 完整 JSON、多份快照/每日工具/每日模型 CSV，含分类 | 简化 rows JSON 与单份混合 CSV | 补格式与数据完整度；保持正文和凭据排除，明确隐私裁剪 |
| 导出 CSV 编码 | UTF-8 BOM | 无 BOM | 对齐 Excel 中文兼容 |
| 热力图指标 | 费用/Token、活跃天数 all/year 设置 | 首页自动按费用可用性选择；趋势可选指标 | 补明确设置及活跃天数范围 |
| 图表 | 桌面大视口、悬停提示、独立仪表板 | 页面内 Canvas、点击，固定数量图例 | 内容与颜色对齐；手机响应布局，不开多窗口 |
| 设置共享 | 字体、排序、主题等主要为电脑本地设置 | 安卓本地设置 | 复刻功能不代表跨端自动同步；不臆造 Hub 设置接口 |
| 主页趋势 | 桌面提供 | 用户已要求移除 | 保留这个明确差异 |
| 二级 Token | 桌面按视图格式处理 | 用户指定完整整数 | 保留完整数字、不恢复缩写或截断 |
| 状态页 | 直接读取供应商状态服务 | 同样另行读取服务状态 | 它不是 Hub 数据；建议改为用户打开时读取，不增加背景周期 |

## 字体与文字：明确差异

桌面源码：src/shared/fontSettings.js、src/electron/main.js 默认设置、renderer/styles.css、dashboard.css、dashboard.js。
安卓源码：ui/Theme.kt、ui/ActivityHeatmapGrid.kt、widget/WidgetDeckRenderer.kt。

| 部分 | 桌面 0.68 | 安卓当前 | 对齐方式 |
|---|---|---|---|
| 主窗口界面字体 | ui-monospace → SFMono-Regular → Menlo → Consolas → Liberation Mono → monospace | FontFamily.Monospace，由 Android 系统解析 | 保留等宽方向，但明确跨系统字形不同 |
| 大数字字体 | -apple-system/BlinkMacSystemFont/SF Pro Display/Segoe UI/sans-serif | FontFamily.SansSerif | 分离“界面字体”和“数字字体”选择 |
| 独立仪表板正文 | -apple-system/BlinkMacSystemFont/Segoe UI/Roboto/sans-serif | 趋势仍沿用 App 等宽 Typography | 仪表板的文本按系统无衬线呈现，数字独立 |
| 字体设置 | 界面字体与显示字体两组；默认、系统、等宽、自定义；显示字体可跟随界面 | 没有字体家族选择，仅字号 | 补两组选择与预览；不把电脑 CSS 字体名称当作 Android 已安装字体 |
| 自定义字体 | 输入本机安装字体家族名 | 无入口 | 若需要可考虑本地 TTF/OTF 导入，先核对授权与安全校验；下一版不默认打包 Apple/Windows 字体 |
| 中文字体 | 各电脑系统 fallback | 各手机系统 fallback | 没有同一 CJK 字体文件，不能宣称字形一致 |
| 总量字号 | clamp(30px, 11vw, 46px)，字重500，行高1.05 | 紧凑42/舒适44/大号46sp，行高46/48/50sp，Medium；二级还会按宽度适配 | 保留手机可读性，按真实尺寸校准；px与sp不作物理尺寸等价 |
| 字号三档 | standard 1.0 / larger 1.1 / largest 1.2（主要 rem 文本） | 紧凑step0/舒适step1/大号step2，多数样式每档+1sp，总量+2sp | 统一三档的缩放语义，升级迁移保留用户可读性 |
| 主窗口基础字 | 默认11px | 舒适正文13sp/小正文12sp | 手机适配差异，不简单缩回11sp |
| 首页名称/数值 | 11px；行高约1.2/1.0 | 常用bodySmall舒适12sp、行高16sp | 缩小冗余行距，保留系统大字布局 |
| 二级名称 | 12px | bodyMedium舒适13sp，行高17sp | 校准行高与字重 |
| 辅助文字/费用 | 10px/主页费用12px | 常见labelSmall舒适10sp、行高13sp；顶部费用12sp | 统一语义；不让小字无视系统字号 |
| 字重 | 总量500、模块标题600、正文通常400 | Typography 与各 Text 局部 fontWeight 共同决定 | 统一明确规则，避免继承造成偏差 |
| 数字等宽 | 多处 tabular-nums | 大总量 tnum；其他文字依赖等宽字体 | 所有数值角色明确使用等宽数字，中文/标签不用强迫等宽 |
| 字间距 | 如主页大多无特殊tracking，仪表板标题0.02em/标签0.03em | labelMedium固定0.2sp | 分场景对应，避免所有标题统一加字距 |
| 图表字 | SVG/CSS 字体设置 | 部分 Paint Typeface.MONOSPACE + 固定9dp字号 | 接入统一字体/字号设置，改用可响应的文字尺寸 |
| 小组件字 | 桌面不同系统原生实现 | Android Pages 等使用打包 JetBrains Mono；Sans Bold 数字 | 与 App 统一字体角色，但保留启动器可用空间适配，不照搬桌面布局 |

## 视觉与交互差异

- 三套主题预设及 TM1/TM2 主色已经对应；桌面可逐色编辑，安卓主要靠主题代码；品牌色桌面可逐厂商覆写，安卓只有开关和已有素材。
- 桌面 CSS 使用透明背景、玻璃 alpha/blur、原生系统 backdrop 和背景图片；安卓使用应用内部颜色/渐变。应补图片/透明度选项，系统级玻璃不复刻。
- 桌面内容图标通常10px、主页标题跳转图标13px；安卓是用户确认的16/20/24dp内容图标及较大导航图标。保留该差异。
- Windows/Mac/Linux 桌面原生平台图标与安卓自选黑白 Windows/Apple/绿联 NAS 图标不同；保留已确认素材，Linux按当前NAS环境映射。
- 桌面 grid 通常一行左右列+单行省略；安卓大字体多行、完整数值。保留完整数字，不能“像素复刻”导致不可读。
- 桌面 titlebar/窗口控制、底部速率/视图切换；安卓没有窗口按钮，目前底部没有对应增量速率。补后者，舍弃窗口控制。
- 桌面悬停帮助、图例、多模型、账户提示；安卓需点击弹层或展开。当前不少详情仍更简略。
- 动画方向相近但不是同一时长/曲线：桌面CSS主页140ms，安卓各局部Compose tween不同。应对照重要状态转换，不复制hover动画。
- 安卓有系统状态栏、导航栏、安全区、返回键、软键盘、触摸目标和系统字体缩放，桌面没有。它们必须作为手机适配保留。
- 当前部分页脚按钮视觉/布局盒仅30/34dp；下一版需检查并落实48dp可操作区域，不能因桌面按钮小而照搬。

## 缓存分类需要校准的具体例子

total=100，unclassified=5，output=25，cacheRead=30，cacheWrite=20：

- 桌面工具明细只向 tokenComponentBreakdown 传total/read/output/unclassified，所以未命中输入为40（其中包含20写入），输入分母70，命中42.86%，未命中57.14%。
- 安卓 hub.3 独立分出写入20，未命中显示20，输入分母仍70，未命中显示28.57%。总量没有改变，但同名行不是同一定义。

建议0.68主行按桌面定义显示“未命中输入40”，独立保留“其中缓存写入20”辅助明细，不能把辅助子项再当成另一份总量相加。这是本轮发现的明确口径差异，应以桌面样例校准。

## 0.68 发布范围建议

必须：取消改名入口及别名覆盖（旧数据保留以便回退）、0.68合成/协议样例、Dots范围标记、未定价提示、缓存定义校准、费用精度、模型分组语义、只读订阅详情与完整导出、字体角色/选择/图表字体统一、工具置顶/拖动及额度项目选择、主页指标选项、全页面布局与触摸核对。

推荐但不强制引入外部服务：增量实时速率（有计时能力才显示）、本地背景图片与透明度、自动汇率可选开关。实时速率应复用 desktop tokenRatePresentation 的计时计数增量，处理首个样本、相同快照、跨午夜回退、空闲和断线，不能用网络等待时间除Token增量。

继续保留：中文、主页万/亿、独立图标尺寸、已确认黑白素材、主页不放趋势、二级完整数值、SSE、离线缓存、Keystore及原签名。NAS部署版本不因手机版本号自动变更。

## 主要证据入口

桌面固定版本链接：
- https://github.com/Javis603/token-monitor/blob/v0.68.0/src/shared/fontSettings.js
- https://github.com/Javis603/token-monitor/blob/v0.68.0/src/electron/renderer/styles.css
- https://github.com/Javis603/token-monitor/blob/v0.68.0/src/electron/renderer/dashboard.css
- https://github.com/Javis603/token-monitor/blob/v0.68.0/src/electron/main.js
- https://github.com/Javis603/token-monitor/blob/v0.68.0/src/electron/renderer/tokenRatePresentation.js
- https://github.com/Javis603/token-monitor/blob/v0.68.0/src/electron/renderer/modelAliases.js
- https://github.com/Javis603/token-monitor/blob/v0.68.0/src/shared/currency.js
- https://github.com/Javis603/token-monitor/blob/v0.68.0/src/hub/server.js
- https://github.com/Javis603/token-monitor/blob/v0.68.0/docs/export.md

安卓文件：ui/Theme.kt、ui/DevicesScreen.kt、ui/TokenComponents.kt、ui/DesktopPresentation.kt、ui/DesktopSettingsPanel.kt、ui/UsageExport.kt、domain/HubModels.kt、data/protocol/HubDtos.kt、widget/WidgetDeckRenderer.kt。
