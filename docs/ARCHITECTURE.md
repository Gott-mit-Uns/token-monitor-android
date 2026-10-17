# Android 当前架构与模块索引

客户端使用 Kotlin + Jetpack Compose，只读取 Hub。Android 不执行本地会话采集、不维护 Electron 窗口、不提供桌面小组件。固定版本与验收边界见[当前待办](NEXT_TASKS.md)及[功能矩阵](FEATURE_MATRIX.md)。以下是模块职责，不能替代真机或生产部署验收。

生产 Kotlin 文件位于 `app/src/main/java/dev/bing/tokenmonitor/`。

| 层 | 主要入口 | 职责 |
|---|---|---|
| 生命周期与网络许可 | MainActivity、HubNetworkObserver | 前台连接、后台取消；配置重建与真正后台分别处理 |
| 页面操作与协调 | HubUiActions、HubViewModel | 连接验证、HTTP/SSE、缓存和页面读取协调；当前没有独立 Repository，不为文档整理拆层 |
| HTTP 与 SSE | HubHttp、HubStream、HubSseFramer、StreamMerge | 受限 GET、严格解析、snapshot/stats/freshness、取消与退避；最多一条有效 SSE |
| 模型与投影 | HubJsonProjection、HubData、SessionMetadataParser | Hub 权威总量、可选字段、full/freshness 投影；缺失不伪造零，不重复累计设备/父子数据 |
| 加密缓存 | HubStore | AndroidKeyStore AES-GCM、gzip、AtomicFile、v1/v2 兼容；输入/密文/展开限制与旧记录保护不变 |
| 偏好与连接策略 | DisplayPreferences、HubConnectionSettings、HubConnectionPolicy | 显示偏好、明确双入口关联、LAN准入和认证/TLS封锁；地址或凭据变化撤销关联 |
| 历史与共享数据 | HubHistory、DeviceHistoryStream、HistoryReadReceipt、HubModelNames、HubPricing、HubSubscriptions | 按连接与修订隔离读取；历史、订阅和模型价格只读 |
| Android 更新 | AndroidUpdate、AndroidUpdateModel、AndroidUpdateUi | 前台按需、成功24小时/失败1小时节流；桌面最新版查询独立，无后台唤醒或自建安装器 |

## 页面与展示投影

| 范围 | 主要模块 | 当前规则 |
|---|---|---|
| 主页与额度 | DesktopHubScreen、HomeUsageUi、HomeQuotaUi、LimitDisplay、LimitProviderLayout | 复用 Hub periods/limits，账号与周期分离，额度与余额不混入历史费用 |
| 会话 | SessionDisplay、SessionListDisplay、SessionRelationshipGroups、SessionFamilyMetrics、SessionListUi | 关系分组、去重账目及成员详情；缓存率按分子分母、速度按计时总量聚合，最近活动取最大时间 |
| 工具、模型、项目、设备 | ToolDetails、MainBreakdownProjection、ProjectDeviceDisplay、DeviceDetails | 保留来源、精确数值与缺失语义，不从设备列表重算 Hub 总量 |
| 趋势与记录 | DesktopTrendsMain、TrendsMainProjection、DesktopDashboardScreen、DesktopCharts | 日/月日历选择与明细复用统计口径和时区 |
| 设置与共享数据 | DesktopSettingsUi、HubSyncDevices、SharedDataUi、SubscriptionPresentation | 紧凑页内设置、只读价格；手机/Hub/采集设备角色分离，名称和连接状态取真实字段 |
| 动效 | HeadlineNumberMotion、DesktopDisclosure、QuotaWindowMotion 等系列 | 保留既有时序、快速反向、减少动效及后台收束 |

## 连接、数据与风险边界

旧流、HTTP 响应、连接绑定及缓存序列分别隔离；连接或账号切换后旧请求不得覆盖新状态。SSE 使用有界待处理帧与消费任务，freshness 不冒充新的完整数据。失败保留有效缓存，缓存/未知/过期/真实零值分别展示。

双入口只在用户明确关联并验证后使用；用户声明不等于密码学身份。认证与 TLS 失败不能通过切换入口绕过。网络恢复使用有界前台重试，不添加后台采集或无限轮询。

HubViewModel 和页面派生仍较集中，大帧解析、保存及冷恢复的内存和交互成本需要依据[性能计划](HUB_CAPACITY_PERFORMANCE_PLAN.md)实测；本轮不做核心重构。当前真机、实网和生产字段的未验证范围统一放在[NEXT_TASKS](NEXT_TASKS.md)。

历次架构增量、旧行号与小组件历史完整保留于[整理前架构快照](archive/history/ARCHITECTURE_BEFORE_CLEANUP.md)，不再作为当前架构描述。
