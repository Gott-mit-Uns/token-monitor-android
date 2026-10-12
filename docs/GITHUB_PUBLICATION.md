# Native GitHub 发布记录

## 当前源码同步

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
