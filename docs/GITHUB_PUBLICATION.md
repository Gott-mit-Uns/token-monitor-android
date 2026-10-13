# Token Monitor Android GitHub 发布记录

## 0.2.60 — Windows/Mac 联合更新正式发布

2026-10-13，[v0.2.60 Release](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.2.60)已正式发布，非草稿、非预发行，版本 **0.2.60 / code69**。唯一annotated Tag固定产品提交 `a8f0c05b02e9ab3bf1ba3b01d9911992fe0fa6c5`，main已快进包含该提交，沿用既有正式证书与 `dev.bing.tokenmonitor.nativeclient` 包名，没有isolated标志。

从指定 `6783284` 接续网络就绪/有限恢复、冷启动一次性路由、轻量字体和Dashboard刷新图标，补修LAN→公网pending-ready恢复预算；用户追加的固定周期日期说明/空白间距删除也纳入同一个版本。主任务和侧任务源码/验收历史完整保留，无重复合并、强推、历史Tag/Release覆盖或有数据应用卸载/清除。

联合最终源码 **432 Release单元通过**；Lint **0错误/93警告/1提示**，67运行依赖与字体通知通过。最终APK **61次定向Android检查**、8组实际系统窗口 **56次执行**、4组320dp/系统2.0/App最大字号/中英文浅深主题周期补验 **12次执行** 全部通过；统计分别记录，不称为更多独立方法。正式68→69合成缓存/偏好升级保留UID、1份加密Hub缓存及4份偏好文件指纹；最终安装字节与发布字节相同。背景杀进程/force-stop另有PID和采样截图，真机/连续视频/性能边界明确。

正式APK **15,183,070 bytes**，SHA256 `7d9ddcc4395d9d87b5daa6d3c98b26cf627083c4d31b0b8e75f754a4fa0eab5f`。同最终提交、版本、正式签名和Release参数的字体基线 **11,488,341 bytes**，全部字体增量 **3,694,729 bytes ≤ 5,000,000 bytes**。两包内嵌VCS提交相同；基线仅反向移除原17项字体差异，没有安装到设备。

APK、SHA256SUMS.txt、release-manifest.json和VALIDATION.md四附件在草稿阶段全部完整下载，逐文件哈希与本地一致，随后正式发布；发布后再次完整下载APK，SHA256一致。回执位于 `evidence/unified-060/publication/`，交付 `dist/release-0.2.60/`；首次失败、截图、APK/测试指纹、侧任务Red及恢复过程均保留。范围见 [联合交接](HANDOFF_0.2.60.md) 与Release附件。

未连接实体手机；新版蜂窝→Wi-Fi、真实Lucky/家庭LAN、持续锁屏/厂商后台、真机后台请求/SSE数与性能/FPS未验证。**原0.2.59 Xiaomi13Ultra蜂窝→Wi-Fi首次及两次复测失败、手动刷新恢复的结论不变**。未修改Hub/Lucky/NAS/Agent/路由器/桌面生产服务。本次仅此一个正式Release，发布后仅补本文档回执，固定Tag/APK不重建/覆盖，到此停止。

## 0.2.59 — V5 统一优化正式发布

2026-10-13，[v0.2.59 Release](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.2.59)已正式发布，非草稿、非预发行。版本0.2.59/code68，产品提交与固定Tag指向 `a509800a062732cb8ecd9820458d48440a199f3c`；main与feat/v5-unified-compact-trends均已推送。安全同步c04533e，保留历史及未跟踪AGENTS-original.md，没有覆盖未提交修改。

本次统一交付真实桌面/Hub联调、测量支持的投影热点优化、趋势图/表格紧凑化、TLS连接池退出主线程修复及共享Compact Tokens。419 Release单元通过，Lint0错误/93既有警告/1提示，67项依赖通知校验通过；38组字号/窗口/主题/语言检查、受影响UI回归、25项网络/缓存回归与正式签名67→68升级通过。最终发布字节另行安装并通过UI、严格TLS、真实Lucky转发和旧加密缓存恢复检查。范围、首次失败及继承证据见HANDOFF_V5_UNIFIED.md。

正式APK为11,472,453字节，SHA256为 `06375896d1d9d4083ca9b39ab6198b4133942dff41633f5be075dd56bafcd3e7`，沿用既有正式证书。APK、SHA256SUMS、release-manifest.json和VALIDATION.md四个资产正式发布后全部重新下载，与本地逐文件哈希一致。最终APK内嵌源码提交与固定Tag一致，安装字节也与发布资产一致。

真实LAN使用只读桥接，真实Lucky使用透明TCP转发且保留原主机名与系统TLS验证；模拟器直连LAN正确拒绝，公网直连受其路由环境限制。实体手机、蜂窝切换、厂商后台和真实设备FPS尚未验收，不把这些路径声明为通过；未修改NAS、Agent、Lucky或路由器。交付dist/release-0.2.59/，证据evidence/v5-unified/，下载回执release-gates/download-verification.json。发布后仅补文档回执，不改变固定Tag/APK；V5完成后停止。

## 0.2.58 — 手机布局密度与字号反馈

2026-10-13，[v0.2.58 Release](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.2.58)已正式发布，非草稿、非预发行，标记为最新正式版。产品提交578d15d251541eb4bfe69dd06fc646a3f55d81f9，main及带注释Tag已推送；版本0.2.58/code67，沿用既有正式签名。

完成用户指定的8项手机布局/字号/README反馈，详情见HANDOFF_0.2.58.md。15项定向Release单元、9项最终设置回归、18项系统字号矩阵、2项额外大数值/最大字号检查及正式签名升级通过；Lint0错误。新版实体手机触控、蜂窝与长期性能未验收。

APK、SHA256SUMS、release-manifest及VALIDATION四份附件在草稿阶段全部下载，与本地逐文件字节一致；GitHub内容API回读README及三张用户实机照片也与本地逐字节一致。正式发布后再次下载APK，SHA256为016b0a8b51497df51925963d75a8499bbf30db68a72ae55e1ff49a6b61010f15，与构建及模拟器安装字节一致。产品Tag/APK保持固定；发布后仅补文档回执。

交付位于work/token-monitor-mobile-density-058/dist/release-0.2.58，证据在该工作区evidence/mobile-density-058。本轮完成后停止，不扩大其他待办。

## 0.2.57 — Token Monitor Android 名称与 README

2026-10-12，[v0.2.57 Release](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.2.57)正式发布，非草稿、非预发行。产品提交e72cc3f094c22a2bf04c529c5eb4ff47fafddefb，main与带注释Tag均推送。名称统一为Token Monitor Android，version0.2.57/code66；仓库介绍也同步更新，历史Tag/Release没有改写。

README重写并入库四张实际模拟器演示截图；通过GitHub内容API回读README与四个PNG，逐文件字节一致。正式APK名token-monitor-android-0.2.57.apk，SHA256：932ba020525ee79a367e3dc2fd1451a0075d106f70ad28d4c643b8513ee4dbe6。APK、SHA256SUMS、release-manifest及VALIDATION四份附件在草稿阶段全部下载核对，发布后再次下载APK校验一致；正式签名沿用既有证书。

7次现有Release页面定向检查、Lint0错误、APK实际名称/版本/许可归属和签名检查通过；截图使用公开演示数据。没有本轮旧版数据保留专项、真机、蜂窝/Lucky或生产服务操作。交接HANDOFF_0.2.57.md；交付在work/token-monitor-readme-refresh/dist/release-0.2.57，证据在其evidence/readme-057。发布后仅补文档回执，保持APK和Tag不变，到此停止。


## 0.2.56 多任务统一正式发布

2026-10-12，[v0.2.56 Release](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.2.56)已正式发布，非草稿/非预发行；main与带注释Tag已推送，Tag产品提交e13b68f7bc8ae021356152040edf96ba131ba6b2。用户授权合并其他任务一起发布，完整保留来源许可7a69170、页面优化d20a509及其最终交接7a829dc的提交历史，并保留0.2.55会话排序。版本统一0.2.56/code65，不覆盖历史Tag或资产。

整合包419项Release单元通过，12项页面与23项网络/菜单/缓存检查通过；最终测试包4项freshness/生命周期检查通过，阶段复跑不累加独立测试数。专项候选完成26组系统配置/78项检查及42项相关回归；最终包另行整合验证。正式签名0.2.55→0.2.56合成加密记录/偏好覆盖安装通过；Lint0错误/93既有警告/1提示，67项依赖通知生成与APK内容校验通过。首次freshness失败及0.2.55基线复现保留，修正夹具HTTP重新验证观察检查点，未改变产品重连逻辑。详情见HANDOFF_0.2.56.md。

正式APK SHA256为d41dc0a075a83824f48100a54f6f0a78cc84a7f371c4d7398631e2319ae51a1f，11,471,717字节；沿用0.2.53起的正式证书。APK、SHA256SUMS、release-manifest、VALIDATION四个资产均uploaded；完整下载四个资产后逐文件哈希一致，manifest commit与Tag一致，最终正式发布后再次完整下载APK校验一致。

首次下载仍在运行时曾提前校验并触发发布，随即恢复草稿；等待下载完成、确认全部资产一致后才重新正式发布。首次失败和恢复过程保留在publication-order-recovery.txt，未覆盖资产或Tag。网络TLS/HTTP2临时失败通过仅当前进程HTTP/1.1兼容方式完成，不修改全局配置、路由或证书验证。

交付dist/release-0.2.56/；完整统一证据位于work/token-monitor-unified-056/evidence/unified-release-056/，专项矩阵位于work/token-monitor-ui-quality-055/evidence/scoped-ui-quality-055/。未执行实体手机、蜂窝/Lucky、生产Hub或GitHub Actions验收；未修改NAS/Hub/Agent/路由。独立验证模拟器已停止。本轮发布完成后停止；后续只补文档回执，不改变固定Tag/APK。

## 0.2.55 会话混合排序正式发布

2026-10-12，[v0.2.55 Release](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.2.55)已正式发布，非草稿/非预发行；main和带注释Tag已推送，Tag产品提交a1ba688203684cbf58103fd8027f22ac38f343ef。开发分支fix/session-review-order-055基于已发布0.2.54，快进合入main，保留历史，无强制推送或remote变更。

0.2.55/code64只修复Auto Review摘要未参与会话混合排序的问题；采用固定桌面0.69参考，原账目/成员、分页和详情保留。36 Release单元、15 Release UI及两阶段正式签名0.2.54→0.2.55合成加密记录/偏好升级通过；最终Release Lint0错误/93既有警告/1提示，无新增问题。没有实体手机、生产服务操作或GitHub Actions测试。详情见HANDOFF_0.2.55.md。

正式APK SHA256为88589cbf84318ca426e38897430da1d2d93c36d4236eeb18bc47ad40c21e07fd，正式证书与0.2.53/0.2.54一致。APK、SHA256SUMS、release-manifest、VALIDATION四个资产全部uploaded；草稿阶段下载全部资产，与本地逐文件哈希一致，manifest commit与Tag一致；发布后再次下载APK校验一致。上传命令曾返回APK资产重名，但草稿资产完整且回读正确，未重传或覆盖资产，确认后发布。

交付dist/release-0.2.55/，完整本地证据evidence/session-review-order-055/；不上传测试APK、截图、签名私钥或私人配置。旧Debug迁移、真机/公网和其他待办未扩大验收。本轮发布结束后停止。

## 0.2.54 双任务整合正式发布

2026-10-12，[v0.2.54 Release](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.2.54)已发布，非草稿、非预发行。main和带注释标签已推送；标签产品提交97f8d0adba5fa4d2619e539903fe1ca87f0c9c2e。大字体2070edf与历史验证55d682f完整合并，合并提交f517232。

正式APK为0.2.54/code63，包名dev.bing.tokenmonitor.nativeclient，沿用0.2.53正式证书。APK SHA256：65d629702762298b6cf56842e61fd252069a5c386f754b26028de1090bfdb9f5。APK、SHA256SUMS、release-manifest、VALIDATION四个资产均uploaded；重新下载全部资产，APK与清单校验一致，manifest对应标签提交。验证范围和未验证边界见HANDOFF_0.2.54.md，完整本机发布回执为evidence/large-font-054/github-release.json及github-download/。

新版真机覆盖/大字体、运行中字号刷新、蜂窝/Lucky和真机SSE尚未验证。电脑未识别手机，不把模拟器升级声明为手机验收。发布后仅补本文档回执，不改变APK/标签或开启下一任务。

## 0.2.53 正式签名 APK 发布

2026-10-12，经用户授权修复手机 UI 并配置正式签名，[PR #1](https://github.com/Gott-mit-Uns/token-monitor-android/pull/1) 已合并，首次正式签名 [v0.2.53 Release](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.2.53) 已发布（非草稿、非预发行）。Tag 指向 `cc50eb565c4e71f3716a40c2be613914235478c1`；与验证提交 `50733ab08aaccc761c6c0f7c767f86897e5c8aa0` 源码树完全相同。

发布 APK 为 0.2.53/code62，SHA256 `dff4af8483554533d517000636d687144258cf120f7f6499876b10781525b292`。APK、SHA256SUMS、release-manifest.json、VALIDATION.md 上传后在草稿阶段全部下载回读校验一致，正式发布后 APK 再次下载校验一致。私有仓库需要已获授权的 GitHub 账号访问；未更改可见性，旧预发布和资产保留。

本轮本地 404 单元、16 Debug UI、18 Release UI和11 Window边界通过，Lint无错误；没有 GitHub Actions 或实体手机验收。签名密钥只在仓库外，不上传。旧 Debug 安装不能直接覆盖，禁止先卸载/清数据绕过签名；详情见 [HANDOFF_0.2.53.md](HANDOFF_0.2.53.md) 和 [RELEASE_SIGNING.md](RELEASE_SIGNING.md)。发布后仅文档状态更新，不重建或覆盖固定 Tag/APK。

## 历史源码同步

2026-10-12，用户确认本 Native 项目的正式仓库地址为 https://github.com/Gott-mit-Uns/token-monitor-android，并明确要求先发布 GitHub。本次将本地桌面 0.69 部分对齐、0.2.52-dev/code61 版本记录及合并后的根目录 `AGENTS.md` 同步至既有私有仓库；默认分支继续使用 `main`。远端 `main` 为本地开发分支祖先，保留全部提交历史，使用快进同步，不强制推送、不覆盖旧 Tag 或 Release。

产品源码交付提交为 `138d7b7`，规则合并提交为 `cd653e2`；本次发布文档提交附在其后。开发分支 `feat/desktop-069-parity-ui` 同步保留，供异地任务按需求接续。新任务应读取 `AGENTS.md`、`docs/NEXT_TASKS.md`、`docs/HANDOFF_0.2.52.md` 和 `docs/HANDOFF_DESKTOP_069_UI.md`，核对实际 HEAD、工作区和未合并工作，不能把桌面部分对齐声称为完整对齐。

本次仅发布已有源码和工程文档，不修改 Android 产品代码、不递增版本、不构建 APK、不上传 APK、不创建新的 GitHub Release或推送新的版本 Tag。0.2.52-dev 本地交付仍是 Debug 验证包，正式签名来源和正式 APK 发布流程尚待确认。历史开发预发布及其资产保留。

发布前扫描可达 Git 历史的凭据强模式和运行数据文件，检查跟踪文件没有构建缓存、APK、签名文件、私有配置及原始数据；扫描仅为定向检查，不代表绝对无敏感信息。本次无需重复构建或运行 Android 测试，继承验证范围见上述交接文档，不把历史测试计为此次新执行。真实手机、Lucky/蜂窝、生产 Hub 字段及性能未验收项保留。

`AGENTS-original.md` 是用户恢复的本地参考文件，保持未跟踪且不纳入发布。异地可获得已推送代码和文档，无法通过本仓库取得本机未跟踪文件、忽略的 APK 或原始测试证据。

## 历史首次发布记录

2026-10-12，经用户明确指示“先发布到GitHub”，建立独立仓库[https://github.com/Gott-mit-Uns/token-monitor-android](https://github.com/Gott-mit-Uns/token-monitor-android)，初始私有。创建时旧token-monitor-android地址重定向token-monitor-minion；随后按用户要求，将此Native仓库改名为token-monitor-android。仓库ID1415029606不变，仍为私有；原token-monitor-minion仓库ID1403285418保持不变。

源码历史保留初始基线288b7dfcc8831b9ce5c4a8c50fb3be4b150ae66b、Antigravity修复21eb492c9b46484d25d706c3d14c1aa25926a596、V3开发2f3b9a3334986c4070f7b840b416736118980749。main指向发布说明提交，保留两个开发分支及v0.2.51-dev基线标签。dev-hub-failover-20261012预发布标签指向实际构建源码2f3b9a3，不递增应用版本或正式版本号。

发布前检查688项当前文件和708个历史blob，未发现真实凭据的强模式命中、超过5MiB的blob或禁止归档目录；三个URL-userinfo命中为既有虚构校验fixture。缓存、日志、数据库、密钥、dist/evidence/work及APK不进Git。扫描是定向安全检查，不提升为不存在任何形式敏感信息的绝对保证。

[预发布下载](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/dev-hub-failover-20261012)附开发验证APK、SHA256SUMS.txt和VALIDATION.md；原始测试输出留在本机evidence/hub-connection-failover。应用SHA256为d27c8fa3f43f264ad7de4a156320a187f9382a3760b5b2eaf4a3ac8d8013729a，code60/versionName0.2.51-dev与签名未变；未覆盖原dist交付。无需重建或重复测试纯发布文档。

46单元及38Android/Compose定向通过；Lint0错误/97警告/1提示，新增0。身份保证限制、旧APK降级缓存限制、手机/蜂窝/Lucky未验证边界，均在预发布说明和[网络专项交接](HANDOFF_HUB_CONNECTION_FAILOVER.md)中明确保留。

远程提交、标签及下载校验回执保存在本地evidence/github-publication/，发布完毕停止，不继续开发或修改生产服务。
