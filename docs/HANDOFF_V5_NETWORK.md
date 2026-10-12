# V5 真实数据与网络验收

2026-10-13（Asia/Shanghai）。修改范围为必要的 Android TLS 连接池清理修复及定向联调测试/工具；Hub、Agent、Lucky、桌面设置与既有 Android 端点准入策略均未改动。

已只读确认 Mac 已安装桌面 0.69.0、DXP4800 LAN 与 Lucky HTTPS 鉴权 stats 可读，两个入口 coreRevision 78、runtimeRevision 5、4 个设备，固定 v0.69 构建指纹一致。LAN history/shared capability 也成功。数据不断变化时夹取 LAN→Lucky→LAN 比较字段，避免将正常上传变化判断为数据合同错误；安全结果保存在 `evidence/v5-unified/network/host-dual-entry.json`。同源稳定身份限制仍继承 V3。

新增 `tools/check-v5-live.py` 指定本轮独立模拟器与冻结 APK，沿用已有桌面安全配置的内存加载器及只读桥接。全程不清除应用数据、不记录凭据/原始响应/真实截图；凭据一次性交给直连测试后只保留在应用既有加密缓存中，测试结束通过既有 forget 操作删除本次测试配置。报告保留源 APK/测试 APK SHA-256、公共构建字段、字段出现次数、路由/连接计数、脱敏断言和首失败文件/行号。

`LiveDirectLanTest` 新增显式 HTTPS 模式，可对既有 Lucky 入口使用正式生产 HTTPS 客户端完成鉴权 GET/SSE、加密 snapshot/history、真实 Home/Launcher 生命周期。原 LAN 模式仍以生产子网准入为前提，不伪造 NetworkScope。现有 `LiveHubTest` 保留真实 MainActivity、65 秒两次心跳、共享设置及 Widget 缓存验证，并通过受控桥接独立统计上游连接关闭。

## 已定位及修复的生产客户端缺陷

真实 Lucky TLS 首次运行在 `HubViewModel.onCleared` 崩溃。脱敏栈显示 `StrictMode→ConscryptEngineSocket.close/drainOutgoingQueue→RealConnectionPool.evictAll`：空闲 TLS Socket 关闭可能发送 close_notify，原代码在主线程清理连接池，触发 `NetworkOnMainThreadException`。对应首失败 APK/测试 APK 指纹和脱敏栈分别保留在 `https-relay-first.json`、`https-relay-main-thread-first.json`。

最小修复只将网络替换、验证中网络替换、局域网 SocketFactory 替换、ViewModel 清理所涉及的空闲池关闭移到 Dispatchers.IO。任务独立于已被取消的 viewModelScope；在调度时捕获原连接池，局域网替换在既有锁内捕获原池，不延迟选择新发布的客户端。请求取消、generation/epoch、SSE 串行退出和缓存边界不变。后台不会由清理任务发起请求。`setForeground(false)` 仍即时取消活动调用，空闲池清理只发生在原有清理位置。

新增 `HubTlsPoolLifecycleTest` 使用真正 Conscrypt TLS keep-alive Socket，在主线程启用 detectNetwork/penaltyDeathOnNetwork，覆盖网络替换与 ViewModelStore 清理（viewModelScope 已取消）。同一测试 APK 下，修复前同版本正式 APK 必现 NetworkOnMainThreadException，修复后通过；证据 `tls-pool-baseline.json` 与 `tls-pool-repaired.json`。测试合成证书只在内存中使用，保留主机名校验，不影响生产信任。

## 真实路径验证

- 直接 Android LAN：未通过准入；模拟器 10.0.2.x 与 NAS 不同子网，ready/local=true、VPN=false、allowed=false，凭据交接次数为 0。`direct-lan-first.json` 保留首次结果。没有伪造子网或放宽 HTTP 安全条件。
- 直接 Android Lucky：未验证。模拟器 split-DNS 将原域名解析到主路由私网地址，原 Wi-Fi TCP 无路由，仿真蜂窝超时；公开 DNS 只有 IPv6 AAAA，无 A。宿主真实 TLS 可读不等于模拟器可直接到达，`direct-https-first.json` 和 `public-https-tls-first.json` 保留失败。
- 真实 Android Lucky 经本地 TCP 诊断路线：通过。只在本轮独立模拟器设置 HTTP CONNECT 代理，回环服务把加密字节透传到实际 Lucky，既不终止 TLS，也不记录数据。Android 原域名、系统证书信任及鉴权保持完整；生产客户端未注入测试 DNS 或信任根。真实鉴权 GET/SSE、加密 snapshot/history、实际 Home 取消调用 <3 秒、6 秒后台无调用/重连、新 ViewModel 缓存冷恢复不发请求、Launcher 建立一个新 SSE 均通过。20.953 秒，`https-relay-repaired.json`；结束后代理恢复 :0，隧道 starts/ends=1/1，active=0。HTTP/2 可复用底层 TLS 隧道，测试以原 SSE Call 取消、队列/运行调用为空及返回后新 Call 验证流生命周期。

本阶段正式签名修复候选 APK SHA-256：`1b9b458f9413bf7ffea258d9c1da4716276c81e8e796904db59285c09b69947a`；可选测试 APK SHA-256：`6acf3d564e39c44576e343ca5b1394190bf2b6a8904b2ba2aa49c0010c4070fa`。二者冻结于本轮网络证据目录 final-app.apk / final-test-fixture.apk。

Android 实际解析证明三个权威周期总量原值与 timed 平均速率保留、1064 个合法标题保留、6 个 Codex 等待标志、152 个合法原生会话标识保留。宿主当前样本三个周期总量均等于设备周期之和；session title/timedOutputTokens/timedDurationMs/reasoningTokens 各出现 1239 次、turnEnded 15 次、parentSessionId 90 次（跨设备与周期的出现次数，非唯一会话数）。当前样本没有 Antigravity 标题、liveActivity、t3ThreadId、session timedTokens；这些真实源端情形无法复现，继续继承有效的合成合同/解析验收，不发明源端字段。reasoning 仅作源端字段出现记录，Android 仍保留 Hub 总量，不重复加算 reasoning。

## 签名覆盖升级与边界

独立 API36 模拟器的正式 0.2.58/code67→0.2.59/code68 覆盖升级通过，appId 保持 10213，两份明确命名的合成加密存储文件哈希不变；旧 snapshot/history 解密及 English 偏好读取成功，`signed-upgrade.json`。不清除数据或卸载。首次宿主证据采集误把 Android println 当作 instrumentation 输出，修正为只读取这两份合成文件的哈希，`signed-upgrade-first.json` 保留；未改变产品/断言。

第一次 TLS fixture 复测受 Android 旧代理缓存影响；删除 legacy http_proxy 并不会完全清除框架代理。诊断工具改用 :0 正确停用，局部 TLS fixture 明确 NO_PROXY 避免外部诊断代理污染；错误分别保留 tls-pool-regression-first.txt 与 tls-pool-proxy-first-*.json，没有降低 StrictMode 或证书断言。

真实手机覆盖安装、真实 Wi-Fi→蜂窝→Wi-Fi、厂商后台、直接公网/IPv6 Lucky 和直接 Android NAS LAN 均未验证。LAN 桥接结果另列，不将诊断路线、宿主或模拟器夸大为手机/公网成功。未受本次修改影响的强身份与 V3 关联限制继续有效。

真实 LAN 桥接最终通过（84.443 秒，`lan-bridge-repaired.json`）：65 秒前台覆盖两次真实心跳、无重连；GET 合计 8 次（health/stats/history/modelAliases/subscriptions/customPricing 各 1、SSE 2），streamStarts/Ends=2/2，最终 activeReads/activeStreams=0。宿主独立确认 Home 后 3 秒内上游读取结束、6 秒后台期间无新调用。Android 对该时刻变化中的 1241 个会话周期条目保留 1066 个合法标题，三个周期原始总量/平均速率、6 个等待标志和 152 个原生标识检查通过。两个阶段的数目随真实上传变化，不把重复测量累加为独立测试。

最终修复 APK 又实际读取并验证从正式 0.2.58 升级保留下来的两份合成加密文件，appId=10213 和两个 SHA-256 与原升级前值完全相同；`signed-upgrade-final.json` 关联最终 APK/测试 APK 指纹。不将早期候选通过记录替代最终包读取验证。

此处1b9b458是完成真实网络验收的冻结候选，正式Tag构建会更新APK内的Git追溯元数据。最终发布APK/Commit/下载校验见GITHUB_PUBLICATION.md及release-manifest.json；源码与ZIP内容等价检查明确区分候选与正式文件，不能把候选hash写成最终Release资产hash。
