# 原始目标完成审计

**本审计的完成判断已被用户验收否定。** 2026-10-09用户安装0.1.7后指出功能与界面都未对齐桌面。下文保留为0.1.7技术验证的历史记录，不能作为当前产品完成依据。重做已进入源码，后续开发已继续至0.2.15，当前范围见ACCEPTANCE.md；工具/模型成本及组件字段、独立视图、主页图表与模块配置等缺口见DESKTOP_PARITY_REWORK.md。新的功能与视觉验收完成前不宣称交付达标。

核对日期：2026-10-09。安装版本：0.1.7-dev，versionCode=8；本次核对没有修改应用代码或重新运行已通过的普通测试。

交付APK：`dist/token-monitor-native-0.1.7-dev.apk`，13,336,233字节，SHA256 `02ac3246b01725539ffee91b1e83af4ff23af9b340e794870b02860c02cd8cb1`。本次签名工具确认v2签名有效；这是开发签名。

## 直接要求与当前证据

| 原始要求 | 核对的当前来源 | 判断与边界 |
|---|---|---|
| 参照桌面v0.68.0独立开发安卓查看客户端 | 本地参考仓库HEAD为`5d2db368d8313415763860d594de00e46a663418`；独立Kotlin/Compose工程的DTO、网络、存储与页面；PROTOCOL.md与COMPATIBILITY.md | 工程与指定基线对应。旧Android fork业务代码不是本工程基础。协议与来源说明保留 |
| 安卓只查看Hub，不采集 | HubHttp.kt读取白名单与GET；HubStream.kt只读GET；AndroidManifest.xml没有采集服务；HubWidgetProvider.kt只调用缓存更新 | 实现符合。未建立提供商登录、ingest、远程数据编辑或删除入口 |
| Hub数据能够在安卓查看 | 当前APK对应的34项单元、API36/API37各32项普通设备检查；真实NAS桥接的实际MainActivity读取证据 | 解析、页面、缓存、真实Hub协议连接有证据。NAS真实鉴权留在只读桥接的NAS进程中；桥接不能证明手机直连或实际Lucky链路 |
| 只在打开应用时保持SSE，退出或后台关闭 | MainActivity.onStart/onStop；HubViewModel.setForeground/stopStream；HubHttp.pause；真实Activity持续/Home/Back/屏幕关闭摘要 | 前台75秒跨两个真实心跳无重连；Home/Back/关闭屏幕后停止上游，返回后恢复；后台取消普通读取及重连。证据是API36模拟器，不泛化为所有厂商设备 |
| 保存同一Hub的HTTPS与局域网HTTP | HubStore.kt同一加密记录内的address/alternate/credential；HubAddress校验；HubViewModel切换与缓存策略；双传输集成检查 | 已实现，两个入口指向同一Hub依据用户确认。HTTP当前支持RFC1918数字IPv4，并要求已确认的匹配Wi-Fi/有线网络，不自动转交其他网络或代理 |
| 外网使用HTTPS | HubNetworkObserver真实默认网络观察；HubViewModel在网络替换和回前台优先HTTPS；实际API36 Wi-Fi/CELLULAR/Wi-Fi检查 | 入口选择、蜂窝拒绝HTTP、失败保留缓存和不降级已经验证。实际Lucky公网连接成功仍缺证据，这是当前目标的剩余关键验收 |
| 评估哪些桌面功能安卓不需要 | DEVELOPMENT_PLAN.md与ACCEPTANCE.md排除范围；当前Manifest、网络入口及组件实现 | 已排除本机采集、提供商登录、Hub托管、上报、云盘归档、正文读取、桌面集成及服务端管理写入。小组件不启用后台网络 |

所有上述当前0.1.7测试摘要已与交付APK哈希核对。普通证据保留实际case：单元34项，API36与API37各32项，无失败/错误/跳过。真实Activity持续、返回键、网络切换、屏幕关闭各有独立`OK (1 test)`；覆盖升级包含两个独立通过进程。这些测试的覆盖范围见VALIDATION.md，不用计数代替具体行为证据。

## 实际入口缺口

当前ADB只有本项目的API36模拟器，没有实体手机。此前只读检查确认DXP4800的Hub/agent在运行，Mac配置使用HTTP，DXP4800未发现Lucky容器/进程。本次仅检查与本项目直接相关的现有说明资料，其中Lucky地址为`token.example.com`示例，没有找到可确认的生产入口；未探测示例域名或其他LAN设备。

实际Lucky HTTPS根地址尚未提供，因此没有尝试向未知目标提交鉴权，也没有用其他Worker/Hub替代用户的NAS Hub。需要获得实际入口后，验证它的证书/主机名、Hub health、鉴权读取、持续SSE及前后台恢复，再与现有LAN Hub核对。凭据不得附在地址、参数、日志或审计记录中。

HTTPS失败样例和测试客户端信任根不能证明实际Lucky的生产证书与公网路由。当前不能据此把完整目标标为完成。

## 交付与实机范围

可安装开发APK和本工程0.1.4→0.1.7实际覆盖升级已经验证，UID、Keystore、加密缓存和显示偏好保留。正式发行的签名/更新通道、物理手机网络与耗电、厂商启动器是仍待实际条件的项目，分别记录在ACCEPTANCE.md，不把它们宣称已通过。

没有待处理的编译或普通回归失败。下一步应解决实际入口与设备条件，继续真实链路验收；重复模拟器检查或加入通知、导出等可选功能不能替代这一步。
