# V3 / P1-02 连接策略交接

开发基础：21eb492c9b46484d25d706c3d14c1aa25926a596；分支 fix/hub-connection-failover。仅本地开发，无 remote/生产变更/版本递增。

## 修改前的定位

HTTPS优先来自HubViewModel.init、preferSecureEndpoint及restartForNetwork。HubNetworkObserver检查默认网络能力与LinkProperties，HTTP要求私网字面IPv4、匹配子网、Wi-Fi/有线、非VPN、ready、绑定SocketFactory和本地权限；Wi-Fi或子网不是同Hub证明。此前无独立LAN开关，无稳定Hub ID。

HubViewModel独占streamCall；stopStream取消Call、帧消费、保存与重连任务。后台http.pause取消在途请求，MainActivity停止网络观察；恢复health/stats完整解析后再开SSE。epoch/requestToken/bindingToken分别隔离流/HTTP/缓存。失败候选连接已有事务保护，普通刷新失败保留snapshot。取消旧Call后服务端观察EOF可能有调度延迟，需要实际受控连接计数验收。

LiveHttpsEntryTest历史失败证据：evidence/package-051/final/build-checks.md的appSideSuccessRun=false；模拟器把Lucky域名解析到LAN路由192.168.31.1且NAT不可达。此轮不改变路由，不把Mac/fixture TLS冒充蜂窝公网通过。

## 实现前确定的状态机

状态由foreground/loading/live/authBlocked、endpoint与错误分类组合表达，不另设第二个SSE所有者。

| 触发 | 转换与准入 |
|---|---|
| 后台 | DISABLED：取消HTTP、SSE、重连；不读统计 |
| 前台/网络替换 | CONNECTING：选择许可入口，health→鉴权stats→完整解析→CONNECTED→唯一SSE |
| 用户确认关联 | PROBING_LAN：明确声明同Hub（非密码学证明），两入口各health/stats/parse成功后原子保存授权；失败不发布候选数据 |
| 已确认双地址+允许LAN | LAN优先；当前系统网络仍须满足绑定许可；不能仅凭Wi-Fi |
| LAN超时/不可达 | 一次HTTPS回退；认证/TLS/格式/协议错误终止，不跨入口掩盖 |
| HTTPS超时/不可达 | 只有关联已确认且LAN许可时才回退HTTP；未知/蜂窝/VPN禁止 |
| 网络变化/恢复 | 取消旧工作，代次隔离；HTTPS正在正常工作且LAN尚未许可时保持HTTPS；不后台探测 |
| SSE断流 | RECONNECTING，继承2–60秒退避/Retry-After；新流必须full snapshot，freshness不沿用旧基底 |
| 无可用入口 | OFFLINE，保留最近有效缓存；用户刷新/网络变化/恢复时再评估 |
| 401/403 | AUTH_ERROR，生命周期和网络变化不解除封锁 |
| TLS异常 | 单独提示，保留TLS校验，禁止换入口掩盖证书异常 |

## 身份保证与限制

固定v0.69.0的src/hub/server.js健康响应含ok/role/runtime/version/hubBuild/deviceCount/secretRequired/now，未提供稳定可认证实例ID。完整stats也没有可证明双入口身份的合同。无法实现可靠的IDENTITY_MISMATCH检测；不同设备/用量不作为证明或否证。未确认双地址不自动跨入口；只有用户主动切换才验证该候选。

关联确认提供“用户声明 + 两端鉴权可读”保证，不能抵抗共享凭据的错误配置、恶意LAN或IP复用。HTTP凭据与响应在局域网明文传输；用户可独立关闭LAN。改地址/凭据撤销关联。缺省旧记录不获得新的自动切换授权。真正密码学同源仍需要独立服务端协议任务，本轮不修改Hub。

动态IP无法由静态Network Security Config精确穷举；继承manifest的usesCleartextTraffic=true，但所有Hub明文调用仍受normalize/权限/系统能力/子网/绑定Network/无代理和LAN开关的运行时拒绝规则，不新增全局放行或trust-all。

## 实际实现和修改范围

产品：HubConnectionPolicy.kt（纯策略/分类）、HubViewModel.kt（选址/双端确认/冷却/认证TLS封锁/释放屏障）、HubStream.kt（释放通知及TLS/可重试分类）、HubStore.kt（加密可选策略字段）、HubUiActions.kt（界面动作）、HubConnectionSettings.kt与中英strings.xml（开关、确认和风险说明）。没有修改Hub协议、NAS/Mac/Lucky服务、Provider或采集行为，没有新依赖。

测试：新增HubConnectionPolicyTest、HubFailoverTest、HubLanSettingsScreenTest；扩展HubStoreTest、NetworkFixtureHub；更新HubEndpointTest/HubNetworkTransitionTest中旧“未确认地址自动接管”的预期，改为拒绝自动接管、失败保持原绑定/缓存、显式事务后重验。原测试方法数量保留，容量、鉴权、TLS、取消及数据断言没有放宽。

工程文档：AGENTS、ARCHITECTURE、HUB_API_CONTRACT、NEXT_TASKS、UI_PARITY及本交接。

## 验证结果

| 项目 | 实际结果 |
|---|---|
| 单元 | 46项，0失败/错误/跳过；HubConnectionPolicy 6、HubNetwork 3、HubFreshness 22、HomeQuotaLayout 3、LimitDisplay 6、LimitProviderLayout 4、Limit 2 |
| Android/Compose | 最终候选38项全部通过，0失败；自有API36模拟器emulator-5556，受控loopback HTTP/TLS，不是生产Hub |
| 新网络专项 | 7项：超时回退/冷却、明确关联/独立关闭、认证停试、TLS与外网拒绝HTTP、断流全量恢复/HTTPS故障到许可LAN、失败确认保留缓存、连续网络/前后台竞争/旧响应隔离 |
| 活动流证据 | OkHttp EventListener统计stream callStart/callEnd/callFailed，快速切换用例max=1，后台active=0；既有实际socket关闭计数/Activity Home与Back退出恢复通过 |
| UI | 1项新专项内部4组浅深×合成fontScale1/2；360dp组件宽度，确认前不执行关联、明确确认后执行、独立关闭LAN、禁用确认按钮。风险说明可滚动；既有设置/连接事务专项另通过。未宣称真机字体/所有字形边界验收 |
| 兼容 | 旧v1/v2加密记录、完整性/容量/失败写保留等10项HubStoreV2检查，HubStore默认策略与新增字段重载，连接凭据保留/替换通过 |
| 构建 | 应用及普通Android测试工程编译与组装通过；最终build-5.log |
| Lint | 原始报告0错误、97警告、1提示；按id/severity/message/file与上一轮报告比较新增0。去重集合只有96警告，最终计数以原始issue元素为准 |
| 回归 | Antigravity与其他Provider15项包含在46单元内，通过；未重复全385/252或全局UI矩阵 |

candidate-1首次Android运行29项/5失败：3项旧HTTPS自动接管预期，与新准入规则冲突；2项缓存容量边界受到新增默认字段字节影响。改为默认字段省略，保留原容量阈值和断言；安全预期按本轮规则改写。所有首次日志、APK/测试APK指纹和source.sha256保留，没有删测试/忽略错误。candidate-1 change.patch不含当时未跟踪文件正文，source.sha256覆盖它们，故首轮源码正文不能仅凭patch完整还原。candidate-2额外冻结所有修改/新文件正文、patch、完整Kotlin/strings指纹、XML和报告。

证据：evidence/hub-connection-failover/，最终candidate-2/network-ui-tests.log、unit-results、lint-results.xml、source.sha256、apk.sha256、installed-*.sha256、signature.txt；build-1至5日志保留。build-3/4为中间编译，不冒充最终安装验证。

## 身份与实网未完成边界

真正“HTTP/HTTPS身份不一致”检测：协议缺失，受限而非通过。实际覆盖的是未经明确授权的候选无法自动污染缓存；用户明确关联后，应用仍无法发现共享凭据错配或IP复用。独立强身份协议属于P1后续任务，本轮不改服务端。

真实手机覆盖安装、系统大字/触摸、蜂窝/Lucky TLS与私有stats/SSE、真实Wi-Fi切换/厂商后台行为全部未验证。历史LiveHttpsEntryTest、LAN桥接history1.685秒间隔异常未重跑/修复，继续按原独立任务保留。

新增兼容限制：旧记录向新客户端读取已测试通过；旧APK严格拒绝未知envelope字段，写入新策略后不能直接降级至原APK读取该缓存（源码结论，未做真实降级实验）。回退需要支持新增字段的兼容构建，禁止清数据。SavedHub策略属性放在构造体外以保持旧ABI，未来调用copy需显式保留这些属性；当前产品路径未用copy复制SavedHub。

未确认且当前权威为HTTP的旧配置，在外网安全停读，不自动使用未经确认的HTTPS；用户可主动验证切换到HTTPS，回可信LAN后确认双入口。LAN关闭不会删除地址或缓存。TLS/认证封锁需要用户主动连接事务重验；普通刷新和生命周期不绕过封锁。HTTP首次故障后没有后台重试服务。

## 开发验证交付

生成专用Debug应用/测试APK：candidate-2/validation-app.apk及validation-test.apk。应用SHA256：d27c8fa3f43f264ad7de4a156320a187f9382a3760b5b2eaf4a3ac8d8013729a；测试APK：bb2794931b8de73b09a76b3e7364b249329d8a67da5591980cdc38b4eeeef3e7。模拟器覆盖安装后的文件SHA与冻结包一致，没有清除应用数据。applicationId/versionName/code仍为dev.bing.tokenmonitor.nativeclient/0.2.51-dev/60；保持既有Debug签名，见signature.txt。

原dist/token-monitor-native-0.2.51-dev.apk校验仍为ef830691261dcd38d322eafd4e286068856871ea52e1347efbfd4b2123a4d805，没有覆盖旧交付。未发布/上传，没有remote。

最终开发commit SHA见同证据目录git-delivery.json；不把commit自身SHA写入被提交文档造成自引用。

建议下一独立任务：P0-02/P1-08真实手机覆盖安装和Wi-Fi→蜂窝→Wi-Fi验收，观察双方连接计数、缓存/设置保留及Lucky私有链路；强身份协议单独设计/授权。本轮到提交停止。
