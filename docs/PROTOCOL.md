# Android Hub 读取协议

基线为桌面 v0.68.0 commit 5d2db368d8313415763860d594de00e46a663418。协议依据为上游 docs/API.md 与 src/hub/server.js。已使用基线 Node Hub 生成模拟响应，并完成安卓解析、设备网络测试与真实NAS Hub桥接读取验证；Worker源码已对照，实际部署仍未验证。

## 数据来源

fixtures/v0.68.0 内全部是模拟数据，生成工具为 tools/capture-protocol.cjs。运行工具会临时在本机回环地址启动 Node Hub，随机鉴权值只存在内存中，不写入产物；完成后关闭服务并删除临时数据库。不会调用真实 Hub，也不会从旧 Android 项目复制模型实现。每次生成的时间戳与部分 revision 会变化。

## 客户端规则

- 连接验证先读取 health，再鉴权读取 stats。health.version 不是桌面版本；hubBuild 是兼容参考，不是可信运行时证明。缺 build 的旧 Hub 仍可尝试核心接口。
- stats.periods 是显示总量的来源；today、month、allTime 分开，不把设备列表再次叠加到总量中。大整数至少使用64位整数。
- 零用量设备保留。设备新鲜度采用服务端 stale、ageMs、receivedAt 和来源 updatedAt 的不同含义；旧 updatedAt 不能独立代替 stale。
- SSE 发出 x-token-monitor-stream: 2。snapshot/stats 的 data 是事件信封，完整数据在信封 stats 内；freshness 是部分更新。心跳注释不是新用量。
- freshness 只合并允许的新鲜度字段，保留 periods、limits.providers、sessions、projects。首次快照前收到 freshness 不构造空统计快照，等待完整 snapshot。
- 可选接口404表示能力不支持；鉴权失败不降级为匿名读取。公共统计不替代私有统计。
- historyRevision、deviceHistoryRevision、subscriptionsUpdatedAt、syncSettingsRevisions 用于各缓存失效；字段缺失不表示内容为空。
- 会话 turnEnded 为 true/false/缺失三态。完整快照替换与增量合并分别处理，不能将缺失状态解释为任务完成。
- tokenComponents/throughput 能力与缺失值保留；未分类与未定价不猜测。时区与周期截止采用 Hub 语义。
- 模型别名仅读取共享配置；不改写原始模型ID或本地重算总成本。

## 当前验证覆盖

工具验证：未鉴权 stats 返回401；health、stats、devices、history、subscriptions、sync/content、共享别名和价格读取返回200；总Token 5,000,000,123 未截断；两个设备均保留，零用量设备总量为0；freshness 合并保留期间用量和额度提供商；SSE 首帧为 snapshot 且包含相同累计总量。

尚未覆盖：Worker 运行时对照、历史时区/跨日联调、实际 stale 超时、标题撤销、Lucky TLS和手机直连。单元测试及模拟器覆盖范围以 VALIDATION.md 最新记录为准。

## Android 当前连接与缓存行为

依据最新用户要求，SSE只在前台打开，后台关闭socket与重连任务；前台恢复连接。鉴权失败停止自动重试，网络中断采用有上限的退避。HTTP读取也在后台取消；过期请求不得回写。

HTTPS与私网IPv4 HTTP共用凭据、保存为同一Hub的双入口，每次从后台返回前台、默认网络标识变化时优先HTTPS。只在前台观察默认网络：Wi-Fi/有线IPv4子网与HTTP入口匹配时允许手动HTTP，其他网段、蜂窝、VPN或网络未知时使用已保存HTTPS；不探测服务或读取SSID，不自动降级HTTP。没有HTTPS入口时停止局域网网络请求并提示配置。子网匹配不能独立证明同Hub身份，入口属于同一Hub仍依据用户确认。切换不可用入口时保留同Hub已缓存快照与明细，成功读取才持久化新入口。

默认网络替换时先发布尚未完整的新状态，撤销旧网段许可；收到新capabilities与link properties后才能允许HTTP。HTTP还必须具备对应Network.socketFactory，普通读取与SSE共用此绑定，明确使用Proxy.NO_PROXY和独立连接池；不把已授权的本地请求交给其他默认网络或系统代理。网络丢失关闭全部在途读取、流和重连任务并保留快照，前台恢复网络时重新验证。HTTPS已选中时也随网络标识变化重建连接。401/403停止状态在网络替换、入口自动改为HTTPS和前后台切换期间保持，只有用户主动操作才重新尝试。后台只更新状态，不产生网络请求。

这些规则依据[Android默认网络观察说明](https://developer.android.com/develop/connectivity/network-ops/reading-network-state)与[Network.socketFactory说明](https://developer.android.com/reference/android/net/Network#getSocketFactory())；应用侧行为已有合成双传输集成测试，不代替实际Wi-Fi/蜂窝/Lucky验收。

历史与订阅使用源端日历键和BigDecimal金额，随统计快照写入同一Keystore加密记录。历史/订阅revision变化失效相应明细；在途明细请求若对应revision变化则丢弃，避免将旧数据存入新revision。设备别名只改变手机展示，不向Hub写入。

设备明细与clientHealth v1按来源独立展示；诊断observedAt不由设备updatedAt替代，liveTokens不由历史期间推算。余额credits与支出spend分别读取，currency不转换，boundaryKind只决定到期/重置文案。

历史日期范围：7/30/90天和全部已记录日的汇总以返回的最大日期键为截止日期，不用手机日期或手机时区补算。缺失日和缺失Token保持未知；重复日期键不参与用量或成本合计。显示已记录日、提供Token日、提供成本日数量；总量使用BigInteger，成本使用BigDecimal。未定价Token按unpricedTokens显示，不将已知成本宣称完整成本。每月列表独立展示全部已提供月份。

活动日历展示截至Hub最新历史日期的近12个周一开始的周，星期轴固定，未来日期禁用。历史未知日使用独立标记，已记录零用量显示0；颜色仅表达窗口内正用量的相对等级，不代替完整数字，点击任一有效日期查看详情。

共享自定义价格：仅读取GET /api/sync/settings/customPricing的version=1文档。value=null与已保存空数组分别显示；五种单价按USD/100万Token显示，未设置保持未知，数字0显示明确免费。非数值、负数、重复模型或缺少输入/输出单价的整份响应被拒绝，保留已有数据。价格不参与本地重算；总费用仍读取stats。

价格文档随快照syncSettingsRevisions.customPricing变化失效；请求期间revision变化或返回文档与当前快照版本不符时不接受旧结果。配置与其他私有明细一同加密保存，切换到不同Hub或清除连接会清除价格缓存；后台不发起配置读取。0.1.3已经通过真实NAS只读桥接验证该接口、缓存与原有stats/history/subscriptions/modelAliases/SSE。

## 0.2.2主周期

GET /api/devices只用于WEEK/7D/30D，取得逐设备完整History；白名单仍只允许GET。响应只投影设备ID、显式historyAvailable和所需daily字段，不保存无关内容。修订deviceHistoryRevision与排序后的设备ID清单共同构成缓存签名，任一变化使投影失效；在途旧响应丢弃。设备清单不一致最多重读3次，后台取消且不重试。可选deviceHistory字段与既有快照一同Keystore加密保存，旧记录缺字段保持未知。

按每设备today窗口key/endsAt/timeZone选当天，过期窗口在来源时区重算；WEEK周起始规则由桌面Intl函数生成地区表。完整设备历史的范围缺日依桌面补零，缺历史能力/窗口则整个推导范围不可用。逐设备当前today覆盖历史只允许Token非递减，保留费用、未定价及组件能力。项目/会话和工具下模型归属未提供时不冒用其他周期。总量与各明细共用推导结果，日期刷新仅在实际前台生命周期运行。27个公开场景、合成SSE缓存失效/后台检查及当前真实Hub菜单验证见FIXED_PERIOD_PARITY.md。
