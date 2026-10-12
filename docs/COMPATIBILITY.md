# Hub兼容证据

协议基线：Javis603/token-monitor v0.68.0，提交5d2db368d8313415763860d594de00e46a663418。此表区分源码一致、合成运行与真实部署读取，不能将源码检查当作部署验收。

| 能力 | 基线Node Hub | 当前NAS Hub | 基线Cloudflare Worker |
|---|---|---|---|
| health角色为hub | 源码与合成运行确认 | 只读读取确认 | worker/src/index.js源码确认 |
| stats期间、设备、额度 | 合成响应与安卓解析确认 | 安卓桥接读取确认 | 共用usage/limits模块，路由源码确认 |
| SSE snapshot/stats/freshness | 合成运行与安卓socket测试确认 | 安卓桥接接收确认 | 首帧信封及stream版本协商源码确认 |
| history每日/月度日期键 | 合成响应与安卓解析确认 | 安卓桥接读取确认 | 共用history模块，路由源码确认 |
| subscriptions记录 | 合成响应与安卓解析确认 | 安卓桥接读取确认 | 共用subscriptionDisplay模块，路由源码确认 |
| 模型共享规则 | 合成响应与安卓解析确认 | 安卓桥接读取确认 | 共用syncContent模块，路由源码确认 |
| 共享自定义价格 | 基线响应与安卓五类单价解析确认 | 0.1.7真实Activity桥接读取与加密保存复查确认 | 共用syncContent规范，运行部署未验证 |
| clientHealth v1 | 闭合状态枚举与诊断源码确认；安卓解析测试覆盖 | 可选字段按实际响应显示；缺失保持未知 | 共用clientHealth模块 |
| HTTPS传输 | 独立合成TLS服务器验证证书/主机名/重定向/SSE | Lucky链路尚未验证 | 实际Worker部署尚未验证 |

已直接比较Node与Worker的hubProtocol.js、usage.js、history.js、clientHealth.js、subscriptionDisplay.js、syncContent.js、limits/core.js。去除Worker自动生成说明头后，七个模块与基线源文件内容相同。没有运行同步脚本或修改参考仓库。

Worker数据路由在未配置共享凭据时拒绝读取；安卓不使用公开统计替代鉴权数据。读取路径仅使用GET，未实现设备删除、订阅编辑、共享规则写入或采集上报。

真实NAS测试通过临时只读桥接完成，真实鉴权留在NAS内部。它证明实际Hub响应能被Android数据层、SSE和加密缓存处理，不证明Lucky证书、反代断连或手机实际Wi-Fi/蜂窝路由。

0.1.6在默认网络策略改动后再次运行真实NAS桥接测试，stats、设备期间、history、subscriptions、modelAliases、customPricing、SSE恢复和加密缓存/组件投影均通过。测试仅记录布尔结果，未记录真实用量、私有名称或响应正文，结束删除测试缓存并关闭临时端口。

0.1.7进一步使用真实MainActivity、系统默认网络观察/SocketFactory及正式客户端连接桥接，没有测试传输改写或手工网络范围。NAS当前package版本为0.68.0。前台连续75秒接收至少两次真实Hub心跳，桥接仅启动一条流；实际Home在3秒内结束上游，6秒后台阶段没有额外GET，实际Launcher恢复后只新增一条流。全程恰好8次GET（health/stats/4类可选读取/2条流），最终两条流关闭、缓存清理。结果evidence/results-api36-nas-sustained-0.1.7.txt。此证据仍使用鉴权保留在NAS的只读桥接，不等同于手机直连或实际Lucky链路。

同版本实际Back检查另行通过：此次Back销毁初始Activity，退出6秒没有额外读取，实际Launcher创建的新实例从加密连接重验并恢复SSE，恰好6次GET、两条流。结果evidence/results-api36-nas-back-0.1.7.txt。此生命周期范围不推断所有Android版本或启动方式。

API36实际默认网络从Wi-Fi切为CELLULAR再回Wi-Fi的检查通过，未注入客户端、网络范围或地址改写。安卓原SSE Call已取消且退出实际运行/排队列表；蜂窝阶段不读取HTTP Hub，回Wi-Fi保持HTTPS，手动切换才读Hub，Home/Launcher返回再选HTTPS。实际6次GET、两条流，缓存保留。结果evidence/results-api36-nas-network-0.1.7.txt。HTTPS端为`.invalid`失败样例，不能作为Lucky成功证据；断开的Wi-Fi使旧对端关闭未在3秒内被主机观察，遗留桥接读取由测试工具主动清理，不能据此声称断网后Hub立即收到关闭。

同版本实际关闭屏幕检查14.405秒通过：系统关闭屏幕后PowerManager确认非交互状态、Activity不再RESUMED，3秒内结束上游，6秒熄屏无新增GET。唤醒及恢复应用后只新增一条流，合计4次GET、两条流；加密缓存保留，初始Activity未销毁。结果evidence/results-api36-nas-lock-0.1.7.txt。范围为无安全锁的API36模拟器，不覆盖物理设备/PIN/厂商锁屏。
