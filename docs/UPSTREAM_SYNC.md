# 上游同步方法与本次差异证据

2026-10-12接管；只分析v0.68→v0.69，未修改生产实现或生成APK。

- 上游仓库：[Javis603/token-monitor](https://github.com/Javis603/token-monitor)。
- 已实现参考：v0.68.0 commit `5d2db368d8313415763860d594de00e46a663418`；现有只读checkout位于兄弟目录 `../work/token-monitor-reference/desktop-v0.68.0`，detached HEAD，检查时干净。
- 目标：[v0.69.0发布说明](https://github.com/Javis603/token-monitor/releases/tag/v0.69.0)，commit `3f106ae3d4712b26cced8272f0d488eaab0de1e6`。标签对象 `e94c8ac34b0179391479f268da1808755cc3aa28` 与commit不是同一SHA。
- [真实比较](https://github.com/Javis603/token-monitor/compare/v0.68.0...v0.69.0)共16提交。GitHub connector比较与独立公开远端tag抓取交叉核对。
- 本地证据：`work/audit-069-source/upstream.git` 为独立审计bare仓库；`diff-stat.txt`、`relevant.diff`、`v0.69.0/`中仅定向源码；`fetch.log`。未切换/修改旧参考checkout。

## 已覆盖的提交与因果路径

| 提交 | 变化 | Android范围 |
|---|---|---|
| ad3f9aa、8891608、050f983 | Electron、headless文档、Windows dock置顶 | D，不移植 |
| 945a23f、eac5837 | Cursor活动触发云刷新、turn结束检测 | 生产端D，Android通用turnEnded消费/字段验证 |
| c62544e、4129b5e | 设备名统一、Lucide导航 | 小范围展示/资源对齐 |
| 80fc301 | Antigravity原生timing、reasoning总量/历史、vendor pin | 生产端修正；Android已有counter消费，不自行重算 |
| 891ac79 | Antigravity单可见组保留双周期及标签 | 首个独立Android开发任务 |
| bb6ec57、22ff2ba | 等待活动、Codex问题等待、T3轮询测试时长 | 新可选字段和TTL；local-only部分排除 |
| 3e98bff | Grok Bot识别/分组、历史身份证据overlay | 基本分组可用旧Hub；历史增强远程不完整 |
| 2c927c5 | Antigravity摘要标题读取、重命名watch | Android已有title显示，生产端与标题策略待验证 |
| 12ec522、6d36168 | Codex父子/原生续接、T3跨provider关系 | 新wire元数据、账目去重、分组和成员详情 |
| 3f106ae | 发布0.69.0 | 不触发Android自动递增版本 |

已读的不仅是公告：API.diff、shared usage normalize/merge、sessionLive、collector增量、Antigravity metadata、T3 metadata、renderer sessionRows、deviceBreakdown/providerPresentation、syncDisplayStats本地overlay、sessionRelationshipWire测试，以及Android对应parser/纯展示/网络状态机。

Node `src/hub/server.js`无diff，shared/Worker usage有diff；事件名保持但字段合同变。Agent关闭持续sessionActivityPolling，桌面局部活动并非每次都上传。公开health对应core74；目标core78。因此不能只更新Android就宣称所有v0.69功能已经可见。

## 后续一次同步的固定输出

1. 固定tag与解析后的commit，保留diff文件清单；不以main当已交付版本。
2. 按 [FEATURE_MATRIX](FEATURE_MATRIX.md) 标记A/B/C/D/E，区分当前部署、目标源码、生产端和桌面local-only。
3. 针对变化字段检查HTTP、SSE full/freshness、模型、缓存、页面；先补协议证据，后写独立实现任务。
4. 复用现有测试，运行受影响子集。旧失败日志保留，新的通过报告绑定源码/应用/测试包。
5. 新功能实际验收后才更新“已实现参考”；不批量重构/递增版本/发包。

首次本地promisor clone获取失败与后续成功范围记录在 `evidence/takeover-069-2026-10-12/acquisition-notes.md`；失败仓库不是上游证据来源。
