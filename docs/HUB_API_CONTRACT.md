# Android Hub API 与连接合同

适用：Android `0.72.0-01` / code76；核对时间：2026-10-17；来源：本分支基线的 HubHttp、HubStream、HubJsonProjection、HubViewModel、HubStore 与固定上游审计。本文规定合同，不证明服务已部署；实测范围见 [VALIDATION](VALIDATION.md)。

## 读取入口与权威来源

仅 GET：health、stats、stats/stream、history、devices、subscriptions、sync/content、sync/settings/modelAliases 和 customPricing。先 health，再鉴权 stats 并成功解析后提交；health.version 是协议号，hubBuild 仅兼容参考。可选404不等于空数据；401/403不匿名重试、不自动解封；429及SSE429/503保留限流/Retry-After。不跟随重定向，不新增认证、POST、采集或后台轮询。

stats.periods 是总量来源，不叠加设备列表、关系组摘要与成员。today/month/allTime保持独立；精确整数、金额、未定价、窗口时区、来源日期、缺失与真实零分别保留。余额credits、spend、订阅和历史成本独立，具体单位见 [COST_MODEL](COST_MODEL.md)。

## SSE 与缓存

前台最多一条 SSE，x-token-monitor-stream=2，读取超时45秒，callTimeout无限。snapshot/stats 信封内 stats 完整替换；首完整快照前 freshness 不生成业务对象。freshness 只更新根 updatedAt/staleAfterMs、limits.updatedAt 以及匹配设备的新鲜度白名单，不覆盖业务 periods、sessions、projects、historyPreview 或修订。每代独立基底，不复用旧流、HTTP或缓存作新流typed基底；HTTP/SSE与旧响应保持 epoch、requestToken、bindingToken 和 sequence 隔离。后台取消网络与重连；普通EOF与已经获准的原子保存按现有边界处理。

解压输入/展开缓存32MiB、密文10MiB是独立拒绝边界，不是普遍安全容量。只投影设备重复sessions/projects，保留完整顶层明细与有序本地模型ID。v2流式gzip→AES-GCM→AtomicFile；v1继续可读，失败保留旧缓存，未知版本/截断/篡改/解压越界拒绝；不更换Keystore alias。容量和性能遗留见 [HANDOFF](HANDOFF.md)，历史测量在有限[档案](https://github.com/Gott-mit-Uns/token-monitor-android/blob/cfab5438ed5dac6537c68affb385517ccfa44c0e/docs/archive/INDEX.md)。

## 双入口与错误边界

用户明确关联同Hub且两端health/鉴权stats/完整解析成功后，才启用LAN优先和一次网络故障回退；用户声明及匹配凭据/总量/设备/构建不构成可认证的稳定实例身份。改变地址或凭据撤销关联。失败保留旧绑定、快照和明细，禁止跨Hub混合。

LAN限私网字面IPv4、Wi-Fi/有线本地子网、系统许可与绑定Network.socketFactory；Proxy.NO_PROXY，独立连接池；网络未知、VPN、蜂窝或新网信息未完整时拒绝LAN。网络变化先撤销旧准入，再重建。已关联的网络故障/5xx可一次HTTPS回退，LAN冷却60秒；反向仅LAN仍许可才可发生。认证、格式、协议与TLS错误不得换址绕过；401/403和TLS封锁跨前后台/换网保持，用户主动连接事务才重验。HTTPS正常证书校验，禁止trust-all。

## 可选字段、设备与会话

鉴权full snapshot可含hub.displayName：非空、≤80 Unicode码点、无控制/格式/孤立代理码点。名称只属于显示元数据，不是Hub身份；随原始快照加密保存，freshness不能改名，新的无名称full回到DNS/未知降级。公开health/collector/IP/产品型号不能猜名称。服务端发行与部署分别核验。

session nativeSessionId、t3ThreadId、parentSessionId/parentClient均按验证后可选证据处理，冲突不猜。记账键仍client:sessionId；一个成员只计一次，父total不再与组摘要叠加。liveActivity有独立观测时钟，未来值拒绝，30秒租约过期回退；waitingForInput按lastUsedAt保留true/false/缺失，turnEnded/归档压制，10分钟窗口，不能当批准原因。title限160码点，源端撤销不可由本机补取。

分组调用数成员缺失/非法则未知；缓存率累加原分子分母，常规input+read+write，Goose input已含read；分母0不出读数，不平均比例。速度先逐成员cap(timedOutput,output)，只汇总有效正duration的计时部分，乘1000换tok/s；不代表未计时成员。最近活动取最大时间，不平均。缺少远程批准原因/逐轮正文/Grok历史增强合同，不用Android本地采集补齐。

## 共享数据与历史

historyRevision、deviceHistoryRevision、subscriptionsUpdatedAt、syncSettingsRevisions分别驱动失效；缺字段不代表空。绑定/修订变化拒绝在途旧响应。devices历史按设备ID集合和修订绑定，集合不一致最多3次重读。源日期、时区和未知/零保持；重复日/月键按现有歧义规则处理。完整历史读取预算和保存边界只有 [HISTORY_READ_RECEIPTS](HISTORY_READ_RECEIPTS.md) 一份权威说明，日/月展示见 [COMPACT_RECORD_CALENDAR](COMPACT_RECORD_CALENDAR.md)。

别名只改显示，不改原模型ID或总费用。价格version=1，null与空数组分开；非数值/负值/重复模型/无input及output的响应拒绝并保留旧值，不用价格本地重算。修订变化/后台/切Hub遵守已有缓存和取消规则。订阅保留原币种与金额单位，服务撤回联调仍未穷尽。
