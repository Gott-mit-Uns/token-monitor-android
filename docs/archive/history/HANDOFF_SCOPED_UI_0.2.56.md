# 指定页面专项质量优化 — 0.2.56 候选

2026-10-12，Asia/Shanghai。范围仅为选择菜单、关于页 Hub 构建信息及主页总 Token/缩写布局。用户明确授权交给聊天「完成最高优先级 Android 任务」统一合并发布；本分支不独立发布同名 Tag/Release。专项开发、实际界面验证及受影响回归现已完成，供统一整合。

## 基线与修改范围

开发起点为已发布 0.2.54 / 9049bf3；独立产品提交 d20a509；另一个任务发布 0.2.55 后，以 8cc99a8 合入 f4c3bc6 基线。本候选为 0.2.56/code65。会话排序属于继承的新基线，本轮未修改该功能。源工作区为 `work/token-monitor-ui-quality-055`；目录中055为任务创建时的名称，不是最终版本号。

| 严重程度 | 首次复现 | 最小修复 |
|---|---|---|
| P1 | 约329dp、标准系统字号、九位精确数值199,470,859；等宽数字字体的缩写单独占行，偏离桌面v0.69 | 预留缩写宽度，用实际字形测量选择数字字号；只在系统/应用标准大小内适配，最低24sp。放大字体保持系统字号并允许完整分行；动效依旧按widest预留，保留精确数字与点击详情 |
| P1 | 桌面读取health.hubBuild判断版本状态，Android验证health后丢弃此字段，关于页永远未知 | 复用已有health读取，保留核心/运行时修订和构建指纹；使用桌面v0.69注册表比较当前/较旧/较新/未知。连接验证和刷新成功后提交状态，随加密缓存保存与冷恢复；未增加请求、轮询或SSE |
| P2 | 通用上拉/下拉菜单每项48dp目标之间另留2dp，选中底色只有28dp | 去掉额外行间隙，底色覆盖48dp行，保留原组件、触摸目标、图标、滚动与键盘/减少动效语义 |
| P2 | 关于页短文字的自身宽度取整触发字体溢出边界 | 关于信息占用可用行宽，长构建提示在窄屏/大字体完整换行 |

未发现本轮范围内的P0。未扩展其他页面、未改NAS/Hub/Agent/Lucky/桌面服务或网络策略。

## 桌面参考与Hub信息的准确含义

对照固定v0.69.0/3f106ae3的totalNumberFontScale/fitTotalNumber、总量CSS，以及hubBuildStatus/hubBuildComparison/hubBuildRegistry。桌面用coreRevision/runtimeRevision和指纹判断版本状态，不从health.version=1得到应用版本。Android现在显示例如「Hub 构建：Node Hub · 核心74 · 运行时5」，并显示相对桌面参考的构建状态，不推断不存在的应用SemVer。没有成功读取时显示等待连接验证；旧服务缺hubBuild时明确未报告，异常或指纹不匹配时显示无法验证。缓存信息是最近一次成功验证值，不代表离线时已探测生产Hub。

涉及DesktopHubScreen、DesktopOptionMenu、AboutSettings、HubSettingsContent、HubBuildInfo和必要的HubHttp/HubViewModel/HubStore字段传递。没有改变既有请求路径、后台取消、连接替换提交边界或存储加密信封版本。新增元数据仅含已验证类型的公开构建字段；不缓存health正文。旧缓存升级读取已验证，未验证强制降级到不识别新缓存字段的旧版本。

## 证据与验证状态

所有证据为独立API36模拟器TokenMonitor_Release053/emulator-5586、公开合成数据，不是真机或生产Hub联调。证据根：`evidence/scoped-ui-quality-055/`。先前执行中断、编译错误、安装批次不一致与首次布局失败均保留，后续日志另存。

- `before/`：0.2.54产品/测试APK、指纹、首次3项失败、329dp浅色中文及411dp深色英文截图。首次失败测试APK不依赖后续HubBuildInfo。
- `desktop-069-*.png`：从固定原始总量HTML/CSS和fitTotalNumber生成的受控桌面参考。
- `after/final-build-056.log`：正式签名候选构建、419项Release单元通过；Lint0错误/93警告/1提示。
- `after/upgrade-*.txt`：0.2.55→0.2.56正式签名覆盖安装，两阶段旧加密历史和语言偏好读取通过；两个独立合成缓存文件SHA保持。
- `after/matrix/`、`matrix-run.txt`：320/411dp × 系统字号1.0/1.3/2.0 × 浅深 × 中英，另含应用largest的两个组合；每组四种数字字体、上下菜单、选择/退出、Token详情与关于信息。26组全部通过，共78项。每组保存9张指定区域截图；实际系统字号不是合成LocalDensity替代。
- `after/regression.txt`：42项受影响回归首次41通过、1个既有freshness测试失败；健康只读一次/缓存恢复不再读health的断言、V1/V2旧缓存、加密元数据冷恢复、401/TLS/取消/连接替换、菜单与数字动效均通过。
- `after/freshness-isolated.txt`、`baseline-freshness-055.txt`：候选独立复跑及已发布0.2.55均复现同一失败。基线用相同Freshness源码及首次测试APK，应用/测试指纹、源码相同证明已保存。
- `after/freshness-final.txt`：修正观察门槛后该类3项通过；`baseline-fixed-gate-055.txt`：0.2.55上同一个严格检查也通过。因而42项回归当前全部通过；不是覆盖或改写首失败。验证修正独立提交c8c0614。
- `after/freshness-final-gate-build.log`、`lint-release.xml`：最终仅测试修正后的构建与Lint通过，仍0错误/93警告/1提示；产品APK与矩阵使用包逐字节一致。矩阵、首次回归、最终freshness测试APK分别冻结并绑定哈希。

### 既有freshness检查点修正的因果证据

原测试在EOF后第二SSE接通时继续要求重连前08:01快照对象不变和statsReads=1。既有HubViewModel会先refreshSaved验证health/HTTP、接受fixture提供的07:00权威HTTP，再建立第二SSE；因此原断言在孤立freshness之前已失效。原HubStream从无full基线的freshness返回null，业务代码无需更改。

修正仅在测试中：EOF前严格assert一次读取；等待第二HTTP、07:00快照与恰好多一次保存，再捕获before-orphan快照/缓存/写次数。仍assertSame快照和缓存、写次数不变、读取次数恰好2、live=false；还要求HTTP重新验证确实替换旧对象，后续new-full/freshness业务引用断言全部保留。没有改成assertEquals或删除断言。该修正属于受影响回归的验证口径维护，未修改SSE/重连产品代码。

首次0.2.54截图窗口约329dp（864px/420dpi），最终窄屏矩阵为320dp（840px/420dpi）；不将两者声称完全相同窗口。截图仅为公开fixture，不代表当前生产Hub构建74。

## 发布与停止边界

正式证书沿用既有e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202，包名dev.bing.tokenmonitor.nativeclient。候选应用/测试包指纹、签名和metadata见after/；正式统一包可能因来源许可更正重新构建，不能把本专项候选hash直接当最终Release资产hash。

GitHub正式Tag/Release、上传、下载校验与最终APK由统一发布聊天完成，并写入最终统一交接。不修改或覆盖0.2.55。该范围完成后停止，不推进其他任务。新版实体手机、真实公网/LAN、生产Hub字段与完整SSE门槛仍未验证。
