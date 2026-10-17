# 网络切换后的前台验证恢复修复稿

2026-10-13 更新：Mac联合候选补修已关联LAN→pending公网→ready的HTTPS恢复上下文，新增定向Red/Green和真实Android恢复fixture回归；见 [HANDOFF_0.2.60.md](HANDOFF_0.2.60.md)。原0.2.59真机失败结论不变。

2026-10-13，Asia/Shanghai。分支 `fix/network-revalidation`，基于已发布的 `a509800a062732cb8ecd9820458d48440a199f3c`。保留 0.2.59/code68；没有创建 Release、推送、合并其他待办或覆盖实体手机正式 APK。本修复稿不是新的正式版本。

## 问题与修复范围

Windows 对 Xiaomi 13 Ultra / Android16 的正式0.2.59验收中，蜂窝返回办公室Wi-Fi后，默认网络已VALIDATED，但应用30秒仍展示缓存/连接失败；主页刷新恢复。首次观察及两次受控复测一致。原验收资料仍保存在独立 `token-monitor-v5-acceptance-20261013` 目录，不改写旧结果。

源码确认恢复缺口：`onAvailable`先发布available=true/ready=false；网络变化立即停流并验证，失败后没有首次HTTP验证重试；同一HTTPS网络后续ready=true不再次触发验证。底层首次失败未取得异常分类，不能宣称已确认DNS、IPv6、TCP或TLS连接池是触发原因。

`HubViewModel`复用现有ready字段，在切网时立即取消旧请求和SSE，待网络详情ready再验证。仅本次切网恢复会话的NETWORK失败，在既有回退用尽后额外重试最多三次（2/4/8秒+小幅抖动）。上下文绑定网络编号、连接binding和requestToken；成功、退后台、更换连接、切网、用户刷新/忘记连接或调整LAN配置时取消或终止。重复ready回调不重置预算。认证、TLS、协议错误和取消不参与重试。

复用既有验证、端点准入、缓存、SSE释放屏障及异步TLS池清理。没有修改HTTP/SSE协议、证书验证、签名、缓存格式、图表或生产服务。

## 测试方法与证据

为不替换已配置手机的正式包，增加测试依赖Robolectric4.17及已有同版本OkHttp TLS测试库；只进入test配置，不进入运行时。生产编译仍为JDK17/SDK37。新用例使用JVM中的真实HubViewModel、HTTP/SSE及回环合成TLS服务，缓存存储替身只保存公开fixture，不验证AndroidKeyStore。

Robolectric API36要求Java21，首轮运行环境失败已保留于 `work/network-recovery/first-runtime-failure.*`，不计为产品失败。为保持仓库既定JDK17，新用例采用虚拟API34；这不是Android16/API36真机或模拟器验收。

- 修改前：9项定向用例中4失败，分别是待ready前提前验证、health首次失败不恢复、stats首次失败不恢复，以及没有期望的有限恢复尝试。原生产源码无修改；收据及测试源码/指纹在 `evidence/network-recovery/baseline/`。
- 首次修复后：同一9项全部通过，收据在 `evidence/network-recovery/first-green/`。
- 最终完整Debug单元：430通过，0失败/错误/跳过（既有419+新增11）；`compileDebugAndroidTestKotlin`通过；Lint Debug为0 errors、97 warnings、1 hint。本轮没有修复无关警告。
- 最终结果、完整单元数、Lint及源文件指纹：见 `evidence/network-recovery/validation-summary.json`；最终测试结果在 `evidence/network-recovery/final/`。未运行的设备测试不得据编译通过计为通过。

最终11项覆盖：等待ready且一次验证；health和stats一次失败后自动恢复/缓存保留；失败上限且重复回调不续预算；后台取消/前台恢复；用户刷新替代重试；切网取消旧重试；TLS/协议/取消不重试；认证封锁；旧网络晚到stats不覆盖新快照；更换连接取消旧authority恢复。成功恢复用例核对最终只余一条合成SSE。

Windows工具放在任务目录及 `D:/BING/CodexTools/tm-network-recovery-20261013`，未改系统PATH。SDK管理器的API37包名为android-37.0，编译级别未改。中文路径的AGP限制仅在本任务Gradle缓存/命令中绕开，没有写入产品gradle.properties。

## 待执行的设备回归

1. 修复候选使用既有正式签名和可追溯构建；版本、覆盖方式及交付需另行安排，不把本分支68候选冒称正式0.2.59资产。
2. 同一Xiaomi手机、既有Lucky入口，办公室Wi-Fi↔蜂窝至少5轮；正常可达网络下无手动刷新恢复，记录网络就绪、验证终态、SSE首个full快照时间。
3. 各在健康读取和stats阶段制造一次安全合成NETWORK失败，确认自动恢复；持续失败确认预算上限；不能对生产服务注入故障。
4. 重试等待/验证期间再次切网、退后台、锁屏、手动刷新和更换配置，检查旧响应隔离；安全只读条件下核对活跃SSE数、后台无新增请求。
5. 401/403、证书错误、无效协议仅在隔离fixture验证，不能绕过TLS或自动降低为HTTP。
6. 继续执行原Android `HubNetworkTransitionTest`、`HubNetworkObserverTest`、`HubLifecycleTest`、`HubHttpLifecycleTest`及真实Conscrypt `HubTlsPoolLifecycleTest`。JVM合成TLS通过不能替代StrictMode/Conscrypt设备回归。

家庭LAN、连续锁屏和厂商深度后台策略仍保留原“未验证”状态。当前分支不整合冷启动或字体待办；完成修复稿和本轮验证后停止。
