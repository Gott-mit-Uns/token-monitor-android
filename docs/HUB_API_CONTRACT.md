2026-10-16 命名规则更新：正式v0.72.0-01直接作为latest，当前APK统一使用正式编号，停止旧编号兼容发行；下文此前桥接策略与对应测试仅保留历史证据。现行规则见[VERSIONING](../VERSIONING.md)。

2026-10-16 **Android0.72.0-01/code76已正式公开**：正式v0.72.0-01与latest兼容v0.2.67两Tag同PR #7源码3c4d2d6，最终APK db90保持；514单元、Lint0错误及受影响9＋2通过，17b7视觉78限定继承。公开Android查询／旧原解析器与真实官方64→75→匿名公开76升级／冷保留已验；两Release共8附件已匿名完整下载并核验，两APK字节相同，size／GitHub digest／SHA256SUMS／SOURCE全部匹配。完整回执见[GITHUB_PUBLICATION.md](GITHUB_PUBLICATION.md)及[联合交接最新节](HANDOFF_0.72.0-01.md)。NAS PR #11已合并并随NAS0.72.0-02发布，实际部署未验收；真机／真实Cursor／生产手机网络边界保持。以下为历史阶段记录。

2026-10-16 本轮固定桌面v0.72.0，Android0.72.0-01/code76联合候选：逐项实现、Hub依赖和桌面排除见[对齐清单](DESKTOP_072_ALIGNMENT.md)，实际验证/发布状态见[本轮交接](HANDOFF_0.72.0-01.md)。此前0.71设置与日历优化已包含，配套NAS0.72发行纳入协议核对；不代表生产部署或全平台完整对齐。

# v0.70 可选供应商字段增量

2026-10-14。CatPaw沿用`clients.catpaw`、clientModels及session client，不新增必需字段；MiniMax按量余额是billing/credits窗口的`remaining`金额及`currency`（CNY/USD），不是Token限额或百分比。不跨币种加总、不转换余额、缺失不作零；旧Hub无字段保留原展示。全量/HTTP/SSE/freshness/旧缓存兼容和生产无provider观察见HUB_070_DEPENDENCIES.md。以下原合同保留。

# Android Hub API 合同与 v0.69 增量

## 已发布Android兼容：可选Hub显示名称合同

NAS名称修复PR #11已合并，合同随NAS v0.72.0-02/source0f748427发布；实际部署未验收。该合同提供可选的鉴权 stats 顶层 `hub.displayName`，普通/Windows SSE 完整快照相同；公开 health 无名称。Android 仅从成功接受的权威快照读取，HTTP/SSE projection 保留该字段，限制为非空字符串、最多 80 个 Unicode 码点、无控制/格式/孤立代理码点。显示元数据不是稳定实例 ID，不参与端点关联或鉴权。

名称随原始快照进入既有加密缓存，无新存储信封、请求或轮询；离线读取旧快照，freshness 不修改名称，新的 full snapshot 缺字段则回到旧 Hub 的 DNS/名称未知行为。不根据采集设备 ID、IP 或产品型号猜 Hub 名称。只更新 Android 不足以使未提供字段的旧 Hub 显示 DXP4800。联合交接见 `HANDOFF_HUB_DISPLAY_NAME.md`。

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

## 0.2.56 Hub构建展示增量

Android复用现有GET /api/health的验证响应，保留可选hubBuild.schemaVersion/runtime/coreRevision/coreBuildId/runtimeRevision/runtimeBuildId。按固定桌面v0.69注册表比较状态；runtime只归一化node-hub/cloudflare-worker，修订须为正整数，指纹只接受sha256:加64位小写十六进制。health.version仍只作协议含义，不转换应用SemVer。

只有health、鉴权stats及解析成功后才提交候选构建，失败/取消保持既有绑定和有效状态。元数据随现有加密SavedHub可选字段缓存，缺字段旧记录保持可读；前后台/SSE/freshness仍保留现有state.copy语义。不保存原始health正文，不增加请求/轮询，不改变服务端合同或部署。关于页明确等待验证、未报告、真实修订与无法验证状态，离线值来自最近成功验证。定向证据见HANDOFF_SCOPED_UI_0.2.56.md。

## V5 真实部署复核（2026-10-13）

本机已安装桌面 Token Monitor 0.69.0。只读复核沿用桌面现有安全配置，凭据仅在内存中使用；DXP4800 LAN 与既有 Lucky HTTPS 入口的 health 和鉴权 stats 均为 HTTP 200，设备数均为 4，Hub coreRevision=78、node-hub runtimeRevision=5，构建指纹与固定 v0.69 注册表一致。LAN history 和 sync/content 也读取成功。此结果更新上文历史 coreRevision=74 的部署快照，不意味着生产服务由本次 Android 任务升级。

两个入口的设备集合与构建相同；连续读取时有正在发生的用量更新，不能要求不同时刻的 allTime 对象字节相同。LAN→Lucky→LAN 的夹取中，Lucky 的每个 allTime 字段与两侧至少一个 LAN 样本相同。仅保存匹配布尔值，不记录私有用量、设备名称、会话标题或正文。设备/构建/凭据匹配仍不提供可认证的稳定实例身份，V3 关联限制继续有效。

宿主验证证据：`evidence/v5-unified/network/host-dual-entry.json`。Android 当前模拟器与真实 NAS 不同子网，直接 LAN 准入应保守拒绝；通过 Mac 回环只读桥接读取真实 LAN 数据的测试，须明确标为桥接。真实 Lucky HTTPS 可由模拟器正式客户端直接读取，系统证书校验不变；具体 APK 指纹、生命周期和缓存结果见 V5 网络交接。

V5 Android 实际联调已完成：真实 LAN 数据经受控只读回环桥接、真实 Lucky 原域名/系统 TLS 经仅转发加密字节的本地 CONNECT 诊断路线。实际 snapshot/history/标题/timed 平均速率及受限 Codex 可选字段解析、加密缓存、Home/Launcher/SSE 与冷模型恢复通过。直接 Android LAN 和直接公网/蜂窝/IPv6 仍未验证，详见 `docs/archive/history/HANDOFF_V5_NETWORK.md`，不将诊断路线等同于手机网络。

真实 TLS 还暴露并修复 Android 连接池主线程清理异常：空闲 TLS Socket 关闭可发送 close_notify，原 ViewModel 清理/网络替换会触发 NetworkOnMainThreadException。现在捕获原池后在独立 IO 调度中关闭，不改变鉴权、证书/主机名、端点准入、取消/epoch、SSE 或有效缓存规则。相同真实 Conscrypt keep-alive/StrictMode 测试 APK 的修复前后失败→通过，以及最终真实联调均有指纹证据。

## Fixed v0.71.0 optional display fields

`clients.goose` and session totals stay collector/Hub authority: no cache/reasoning addition or cross-day redistribution. `clientHealth.clients.goose.source.checks[]` admits only public `id=goose-db` and boolean exists, never paths. Unknown enums degrade to unknown.

`limits.providers[].provider=notion` is non-monetary allowance only: session/windowMinutes360 and monthly billing, numeric used/limit and optional resetsAt. No invented monthly duration. Identity is a collector-generated account hash; unknown schema/status is unavailable. Notion balance/usageSummary/raw workspace/user fields never enter its native model, Token totals, costs or credits.

WorkBuddy accounts use exact keys/region/status/stale/actionRequired/full windows. A missing bound key never falls back to another same-name/email account. Display totals require distinct keys, all healthy/non-stale accounts, and identical non-measurement window fields. Independent balances are not shareable; the total never replaces cached accounts, subscription bindings or authoritative stats.

Fields are optional. HTTP/SSE/freshness/cache envelope/network admission remain unchanged. Remote Pi/CodeBuddy chat Detail still has no endpoint; requirements are a proposal only. See [increment and NAS coordination](DESKTOP_071_DIFF.md).
