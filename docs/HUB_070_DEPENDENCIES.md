# 桌面 v0.70.0：Hub 数据依赖与 Android 消费边界

2026-10-14，Asia/Shanghai。上游固定为 `ac4eeb2c4aea239dd94b67f074c73f69aafc46f8`（v0.70.0），比较起点为 `3f106ae3d4712b26cced8272f0d488eaab0de1e6`（v0.69.0）。本轮没有修改、升级或重启 NAS、Hub、Agent，也没有读取手机或复制供应商凭据。

## 本轮只读生产证据

沿用已配置桌面的安全连接，凭据只在内存中使用，对默认 DXP4800 现有 LAN 入口执行 GET `/api/health`、鉴权 GET `/api/stats`，两项均为 HTTP 200。只保存构建信息、字段存在性和数量摘要；不保存私有响应、设备名称、标题、金额、账号或鉴权值。证据为 `evidence/desktop-070/hub/provider-contract.json`。

当前 4 设备，`coreRevision=78`、`node-hub runtimeRevision=5`，core/build 指纹与既有 v0.69 合同吻合。当前 today/month/allTime 没有 `clients.catpaw`，顶层会话没有 CatPaw 行，`limits.providers` 没有 MiniMax 记录。因此这次读取证明现有 Hub 可读及新数据未出现，**不证明已部署 v0.70，也不证明真实 CatPaw/MiniMax 数据已联调**。真实新 provider 字段与来源端采集均列为未验证；不能把空数组的检查记为金额 schema 通过。

## 明确可消费的合同

| 项目 | 固定源码依据 | Android 行为 | 验收条件和生产缺口 |
|---|---|---|---|
| CatPaw 用量 | [clientCatalog.js](https://github.com/Javis603/token-monitor/blob/ac4eeb2c4aea239dd94b67f074c73f69aafc46f8/src/shared/clientCatalog.js#L81)、[usage.js](https://github.com/Javis603/token-monitor/blob/ac4eeb2c4aea239dd94b67f074c73f69aafc46f8/src/shared/usage.js#L306) 保留 `catpaw` client；普通 periods、clients/clientModels/sessions 承载统计 | 使用既有可选字段 parser；新增 CatPaw 名称、隐藏/置顶/排序能力，沿用现有通用图标降级。保留原工具相对顺序，新工具追加到默认工具列表末尾，不生成或补算统计 | 合成新 Hub full HTTP/SSE/缓存与 freshness 后 CatPaw tokens/title/model 可读，显示过滤不改 Hub total/cost/unpriced；没有字段时不出现虚构工具行。真实 Hub 未出现，仍依赖有支持的源端收集后上传 |
| MiniMax 按量余额 | [limits.js](https://github.com/Javis603/token-monitor/blob/ac4eeb2c4aea239dd94b67f074c73f69aafc46f8/src/shared/providers/minimax/limits.js#L231) 将 provider `available_amount` 转为 `windows:[{kind:"billing",metric:"credits",label:"Balance",remaining,currency}]`；cn=CNY，国际=USD；[结果行](https://github.com/Javis603/token-monitor/blob/ac4eeb2c4aea239dd94b67f074c73f69aafc46f8/src/shared/providers/minimax/limits.js#L399) 为 `accountLabel:"Pay-as-you-go"` | 修复原默认 rates 仅显示 session/weekly 而漏掉余额的问题；credits 以本币金额独立展示，Home/额度/缓存 Widget 共用投影。余额没有 quota 分母，不绘制推测百分比；不换算、合并币种，也不加进用量成本 | CNY/USD 两账户分别显示；真 0 显示 0.00；缺失/非法金额或未提供合法币种显示缺失，未知合法货币代码以代码标明；used/remaining 模式不改变余额语义。旧 Token Plan 的 session/weekly 双周期继续显示。真实 Hub 无 MiniMax provider，生产余额尚未验证 |
| 源端 CatPaw 采集 | [collector.js](https://github.com/Javis603/token-monitor/blob/ac4eeb2c4aea239dd94b67f074c73f69aafc46f8/src/shared/collector.js#L416)、[CatPaw paths](https://github.com/Javis603/token-monitor/blob/ac4eeb2c4aea239dd94b67f074c73f69aafc46f8/src/shared/providers/catpaw/paths.js) | 依赖 Hub/Agent；Android 不读取 SQLite、WAL、应用日志或触发桌面收集，不替代 fork collector | 在源端启用 CatPaw，具备支持该 client 的采集运行时，成功上传规范化 periods/会话后才能取得端到端证据；本轮未改变生产设置 |
| MiniMax 按量模式/地域/供应商接口 | [account.js](https://github.com/Javis603/token-monitor/blob/ac4eeb2c4aea239dd94b67f074c73f69aafc46f8/src/shared/providers/minimax/account.js#L56)、[limits.js](https://github.com/Javis603/token-monitor/blob/ac4eeb2c4aea239dd94b67f074c73f69aafc46f8/src/shared/providers/minimax/limits.js#L258) | 依赖源端选择 api 模式、成功读 balance、Hub 保留 remaining/currency；手机只读 Hub 统计，不新增供应商 API 客户端、设置或密钥 | 源端身份与接口成功，上传本币余额后进行鉴权样本/UI验收；仅 health 构建信息不能代替 |

`balance` provider 块不是 MiniMax 按量余额的必要来源；新版本消费已有 `windows.remaining/currency` 合同，不要求 Hub 增加额外字段。上游 [limits/core.js](https://github.com/Javis603/token-monitor/blob/ac4eeb2c4aea239dd94b67f074c73f69aafc46f8/src/shared/limits/core.js#L170) 保留这些窗口字段。余额和订阅付款/充值记录也不是同一数据，不以订阅记录反算供应商余额。

## 采集与服务端负责的变化

Codex 压缩/归档会话元数据、Hermes reasoning 计费、DeepSeek 模型标识、供应商额度刷新和长同步调度的正确性取决于生成或汇总数据的源端。Android 保留 Hub 的原始会话 accounting key、权威 total/cost/unpriced 和模型明细，只消费已存在可选字段；不会以会话标题/原生 ID 合并记账，不猜模型，不把 reasoning 加入 totals 再收费，也不会因手机刷新操作去拉供应商接口。具体 v0.69→v0.70 差异分类、源码行与各项验收条件由本轮功能矩阵/差异清单列明，本说明仅记录 provider 合同与真实 Hub 缺口。

## 兼容与验证边界

- 没有改 HTTP 路由、认证、TLS、LAN 准入、双入口关联、重试预算或 SSE 生命周期；蜂窝→Wi-Fi 恢复与安全封锁由主任务对最终 Release 做原回归。
- 没有改缓存信封。全量 projection 保留未知可选字段与金额 JSON 表示；freshness 只改白名单时间，不替换用量、providers、会话或未知元数据。新/旧 Hub 均可冷解析，不存在新字段强制要求。
- CatPaw 是追加可管理工具 ID。旧偏好列表按字符串保存，原 ID 不改、size 允许上限仅变大；旧文件可读且不自动重写。新增 CatPaw 选择使用现有偏好保存格式，保留语言、货币、汇率、周期和已有工具选择。
- 定向源码验收：`Provider070ContractTest`（本币/缺失/未知/旧双周期/HTTP/SSE/freshness/CatPaw 总量与设置）、`Provider070CacheTest`（实际加密缓存与 Widget RemoteViews、大字体、旧 V7 工具偏好不重写）。最终执行状态以本轮正式 Release 验收记录为准，新增测试本身不等于已通过。
- 真实生产 CatPaw、真实 MiniMax 按量余额、实体手机 Launcher、蜂窝/局域网、手机覆盖升级未由 provider 子任务验证，不以合成样本或模拟器结果代替。
