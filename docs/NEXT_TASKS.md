# 下一阶段任务与停止边界

基线2026-10-12接管审计，当前正式交付仍是0.2.51-dev/code60。第二阶段按单独授权完成P0-01本地基线及P1-01双周期修复，见 [第二阶段交接](HANDOFF_ANTIGRAVITY_DUAL_WINDOWS.md)。其他项目仍是建议，不自动继续执行；没有正式版本递增或服务升级。

## P0：开发前置的可追溯基线

| ID | 证据/影响 | 下一步与完成条件 |
|---|---|---|
| P0-01 已完成 | 用户明确授权的第二阶段建立本地Git，基线标签v0.2.51-dev；历史开发提交来源仍不可恢复 | 纳入源码、资源、测试、构建与文档；排除缓存/证据/APK/凭据，保留磁盘原文件；680项基线指纹（清单自身另入库），无remote。见GIT_BASELINE_0.2.51.md及第二阶段交接 |
| P0-02 | 真机覆盖安装与旧加密缓存仍未验证，模拟器升级已通过 | 下次手机交付前，与用户安排真实覆盖安装，先确认旧版本/签名和可恢复的数据保留方式；禁止卸载/清数据。安装、启动、设置/旧记录解密分别留脱敏证据 |

## P1：功能与核心链路

| ID | 证据/影响 | 下一步与验收 |
|---|---|---|
| P1-01 已完成 | 对齐上游891ac79：单组session/weekly独立选择与标签；缺字段不造零，重复同周期取最紧 | 产品修改只在LimitDisplay/HomeQuotaLayout；复用HomeQuotaRow及parser，多组和其他provider规则保留。定向验证、首次失败及未验证边界见第二阶段交接 |
| P1-02 本地安全实现完成，强身份/实网待验收 | 用户明确关联且两端验证后才LAN优先和网络失败回退；独立开关、认证/TLS封锁、SSE释放屏障 | 见HANDOFF_HUB_CONNECTION_FAILOVER.md。无可认证Hub ID，用户声明不是密码学证明。下一独立任务安排真机/蜂窝/Lucky验收；服务端身份协议另行授权 |
| P1-03 | Luckyhealth core74，目标core78；NAS SSH被拒绝；新字段当前部署E | 先恢复已配置的安全只读连接或用已授权安全会话；核对后端/双入口身份、部署build与生产端版本、私有payload字段存在性。不给health200升级成功含义。服务升级另外立任务，不在Android审计里执行 |
| P1-04 | Android丢弃liveActivity/waitingForInput，活动显示仅旧三态 | 在P1-03证据后，接入可选状态模型、30秒租约和10分钟问题等待、unknown/归档/未来/缺失回退；Home/Sessions共享投影。保留turnEnded优先，重连/缓存不复活过期等待；零Token本地overlay不采集 |
| P1-05 | parent/native/T3字段未保存，只有旧auto-review汇总 | 独立关系任务：先native/T3等价身份，再parent；处理冲突、missing parent、cycle、人工fork、跨client、分页和详情。成员每个记账key只计算一次，费用/unpriced/models完整，Hub总量不改。协议→模型→cache→UI逐层验证 |
| P1-06 | Grok基本分组可依据现有id/models；历史增强是Electron-only | 独立纯展示分组，成员及合计保留；明确历史识别受限，不能声称完整历史对齐；不读取Cursor本地数据库 |
| P1-07 | Antigravity title/throughput/总量消费已有，但当前Agent产出/授权未验证 | 定向只读联调生产端升级后的标题重命名/撤销、计时能力、推理Token总量与history一致性；缺数据保留无读数，不本地再加reasoning |
| P1-08 | 真机公网、SSE单连接/恢复一致性未驗收；perf50仍接近192MiB边界 | 按UI_PARITY实体设备单完成；大full frame与导航/保存并发只做必要定向测量。先复现再优化，不能据局部freshness加速声称整体流畅 |
| P1-09 | `USER_FEEDBACK_048.md`最新验收保留LAN只读桥接16次中的1次失败，history两读间隔1.685秒；原日志`work/s5-lan-home-r10.txt`仍在。影响历史请求节流和revision变化下的读取次数；不是重复SSE的证明 | 先对照该运行时间线、HistoryReadCoordinator和UI重试/revision失效触发，区分并发读与允许的新revision；只复现这条失败路径，保留首次日志。无证据时不归咎于Hub或改节流阈值掩盖 |

## P2：独立的小范围对齐

| ID | 证据/影响 | 下一步与验收 |
|---|---|---|
| P2-01 | device项目列表使用hubDisplayName/id，rate用hostname，详情另走name | 提取一个纯展示resolver，先明确Android本地别名优先，再v0.69 displayName/id/hostname/device；同名/空白/缺失对齐各页面 |
| P2-02 | 上游4129b5e更新导航静态SVG | 导入受影响资源与许可，检查theme/尺寸/48dp；不改导航布局 |
| P2-03 | 旧文档包含历史“当前包”说法；Lint93/97来自不同构建 | 新文档作总入口，旧文档注明历史范围；按报告来源跟踪实际警告，不为消除计数改产品语义 |

## P1-01 已完成的独立执行规格

输入：现有Hub antigravity windows，无需服务部署。修改范围限定 `LimitDisplay.kt`、`HomeQuotaLayout.kt` 及必要的existing tests；优先扩展 `LimitDisplayTest`、`HomeQuotaLayoutTest`、`HomeQuotaScreenTest` 中已有fixture，而非复制新测试工程。

定向验收：单group两周期、只剩一个周期、重复同kind取最紧、过滤hidden后只剩一group、多group保持旧pick/顺序、没有reset时标签完整、showUsed模式、reduce-motion无行为倒退。先跑受影响单元；需要界面证据时仅跑已有HomeQuota专项。当前任务不要求全量385/252、所有动效或重新打APK。完成后更新FEATURE_MATRIX/UI_PARITY并等待后续任务，不自动串行推进其它项目。

需要用户配合的是手机连接/原版本确认、蜂窝公网测试，以及必要时恢复NAS安全只读访问。无需在聊天里提供凭据；若安全会话确实无法建立，在执行前单独确认登录方式。
