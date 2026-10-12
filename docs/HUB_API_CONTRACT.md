# Android Hub API 合同与 v0.69 增量

2026-10-12。此文件是当前入口/新字段的增量合同；既有完整规则继续引用 [PROTOCOL.md](PROTOCOL.md)，不替代旧验证。上游依据 [固定v0.69 API](https://github.com/Javis603/token-monitor/blob/3f106ae3d4712b26cced8272f0d488eaab0de1e6/docs/API.md)、`src/hub/server.js`、`src/shared/usage.js`、`worker/src/shared/usage.js`。Android依据HubHttp、HubStream、HubParser、HubViewModel。

## 请求与事件

| 请求 | 用途 | 当前行为 |
|---|---|---|
| GET /api/health | ok/role=hub/构建兼容参考 | 不带私有鉴权；version=1是协议值，不是桌面版本；构建指纹不是部署源码证明 |
| GET /api/stats | 权威totals、devices、limits、sessions/projects | 私有读取；32 MiB解压后输入限制；成功解析才更新 |
| GET /api/stats/stream | 前台实时统计 | Bearer；x-token-monitor-stream=2；45秒读取超时、无限callTimeout；快照信封内stats |
| GET /api/history、/api/devices | 历史/设备固定周期详情 | 32 MiB输入；signature/revision与设备集合一致才接收 |
| GET /api/subscriptions | 共享订阅 | subscriptionsUpdatedAt驱动失效 |
| GET /api/sync/settings/modelAliases、customPricing | 共享别名与价格 | syncSettingsRevisions驱动失效；只读 |
| GET /api/sync/content | 能力发现 | 不拿公开health作为标题授权 |

客户端不POST/PUT、不匿名替代私有stats、不跟随重定向。401/403停止自动尝试，404可选能力不支持，429有可见限流状态；SSE的429/503读取Retry-After。HTTP和SSE同用Network绑定、禁系统代理的私网transport。

每次SSE连接首事件是完整snapshot；stats是完整替换；freshness只改updatedAt/staleAfterMs、limits.updatedAt与devices时间/stale等白名单，不改sessions、periods、projects、revision。首完整snapshot前freshness不生成业务快照。新连接不复用旧流typed基底。缓存/HTTP不是freshness基底。

本次Git diff未改Node Hub路由/SSE服务文件，但shared normalizer/aggregation和Worker副本变更，**有效payload合同有增量**。不能因URL/事件名称未变就认定无需适配。

## 新可选字段

| 字段 | 语义与合并边界 | Android当前 |
|---|---|---|
| sessions.*.liveActivity.state/observedAt | running/waiting/idle/unknown；独立观察时钟，未来观测拒绝，30秒后退回transcript状态；unknown清除旧观测，归档不采用 | 已消费；缓存恢复不延长租约 |
| sessions.*.waitingForInput | Codex原生问题等待；true/false/缺失；按lastUsedAt，时间相同false优先；turnEnded/归档压制，10分钟窗口 | 已消费；不能当作批准信号 |
| sessions.*.nativeSessionId | 验证的Codex UUID，续接rollout同native conversation；冲突丢弃；记账仍用client:sessionId | 已消费受限可选字段 |
| sessions.*.t3ThreadId | 验证的T3 App身份，可跨provider/native线程更换；有界非文本，冲突跨设备值丢弃，人工fork不同ID | 已消费受限可选字段 |
| sessions.*.parentSessionId/parentClient | 委派父关系；parentClient默认child client；缺失父不猜，人工fork不加link | 已消费；review独立于关系树 |
| title | 服务器与生产设备独立opt-in；重命名和撤销由上游生产端处理 | 已读，限制160 code points/控制字符，不本地取标题 |
| timedTokens/timedOutputTokens/timedDurationMs、modelThroughput | raw counters，按设备连续差分或session比例；capability false表示无读数；首帧仅基线，负delta重置 | 已支持通用消费；Antigravity原生duration是否产生需联调 |

group只是一层展示：一个accounting member计算一次；父total不能再次与已汇总子组叠加；保持tokens、cost、unpriced、模型明细和原始key，界面合计不替换Hub总量。旧字段缺失必须继续可用，不以空值清掉旧协议可读内容。

**不进入Hub：**zero-token local rows、period.sessionActivity局部覆盖、nativeSessions/nativeProjects、localRecentUsageActivity以及Electron历史Grok身份overlay。已产生Token的会话可以带liveActivity随普通发布上传，但源端局部活动变化不推进用量时间、不单独触发同步；headless Agent关闭持续活动polling。Android不能保证桌面同样即时的等待，也不能通过后台采集补齐。

## 双入口：V3准入

固定v0.69 server.js的health字段为ok/role/runtime/version/hubBuild/deviceCount/secretRequired/now，没有可认证的稳定实例ID。相同凭据、设备、总量、版本或HTTP200均不证明同源，Android无法可靠检测IDENTITY_MISMATCH，不自行发明字段或改Hub。

SavedHub保持一个凭据和单份权威缓存。新增加密可选lanEnabled/endpointsAssociated，旧记录默认允许手动LAN、双入口未确认。用户明确确认同Hub且两端分别health/鉴权stats/完整解析成功后才保存关联；验证时不发布候选数据。这是用户声明加可读性检查，不能抵抗共享凭据错配/IP复用；TLS不变。改地址/凭据撤销关联。

| 条件 | V3行为 |
|---|---|
| 前台恢复/网络替换 | 已关联且系统许可LAN、绑定Network和本地权限可用时优先HTTP；否则HTTPS或保持未确认的权威入口 |
| 未确认入口 | 不自动接管；用户主动切换走health/stats/解析/原子提交，失败保留旧绑定和缓存，不混合新旧明细 |
| HTTP网络故障/5xx | 关联确认后才一次HTTPS回退，LAN60秒冷却；认证/格式/协议错误不换址 |
| HTTPS网络故障 | 关联确认且LAN仍许可时一次HTTP回退；外网/未知/VPN禁止 |
| TLS证书/握手错误 | 独立提示、封锁自动恢复；不经LAN换址掩盖，不绕证书。用户主动连接事务才重验 |
| 401/403 | 自动HTTP/SSE停止，生命周期/网络不解封；用户主动连接事务重验 |
| 后台 | 取消HTTP/流/重连并停止网络观察，不读Hub统计 |
| 竞争与重连 | requestToken/epoch/bindingToken隔离，旧流响应释放完才开新流；基底为空，首full snapshot |

动态IP无法由静态Network Security Config精确穷举；继承manifest usesCleartextTraffic=true，Hub HTTP仍受地址正规化/私网字面IP/权限/能力/子网/绑定Network/无代理/独立开关限制，不新增全局放行或trust-all。详见V3交接。

## 当前部署证据与缺口

上一接管审计仅对已知Lucky入口公开health作系统TLS校验的GET；role=hub、4devices、coreRevision74、node-hub runtimeRevision5。coreBuildId匹配v0.68.0注册表；目标v0.69.0为coreRevision78。无鉴权stats/SSE样本，且DXP4800 SSH拒绝连接，不能确认该Lucky后端与默认NAS、LAN入口为同一服务，也不能宣称已升级/提供新字段。

下一步只保留脱敏能力结果：build、字段是否出现、各状态数量、schema合法性、连接数/生命周期计数与全量一致性摘要；不保存凭据、标题、私有响应正文。取得现成安全会话后才读取私有接口，普通health连通不作为完整业务通过。

V4不改事件、HTTP路径、网络策略或存储信封。原始snapshot可选元数据随加密缓存保存，恢复时按当前时钟评估租约；freshness不重写关系。版本展示不将health.version=1或hubBuild指纹当作Hub应用版本。
