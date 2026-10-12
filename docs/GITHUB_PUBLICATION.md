# Native GitHub 发布记录

2026-10-12，经用户明确指示“先发布到GitHub”，建立独立仓库[https://github.com/Gott-mit-Uns/token-monitor-android](https://github.com/Gott-mit-Uns/token-monitor-android)，初始私有。创建时旧token-monitor-android地址重定向token-monitor-minion；随后按用户要求，将此Native仓库改名为token-monitor-android。仓库ID1415029606不变，仍为私有；原token-monitor-minion仓库ID1403285418保持不变。

源码历史保留初始基线288b7dfcc8831b9ce5c4a8c50fb3be4b150ae66b、Antigravity修复21eb492c9b46484d25d706c3d14c1aa25926a596、V3开发2f3b9a3334986c4070f7b840b416736118980749。main指向发布说明提交，保留两个开发分支及v0.2.51-dev基线标签。dev-hub-failover-20261012预发布标签指向实际构建源码2f3b9a3，不递增应用版本或正式版本号。

发布前检查688项当前文件和708个历史blob，未发现真实凭据的强模式命中、超过5MiB的blob或禁止归档目录；三个URL-userinfo命中为既有虚构校验fixture。缓存、日志、数据库、密钥、dist/evidence/work及APK不进Git。扫描是定向安全检查，不提升为不存在任何形式敏感信息的绝对保证。

[预发布下载](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/dev-hub-failover-20261012)附开发验证APK、SHA256SUMS.txt和VALIDATION.md；原始测试输出留在本机evidence/hub-connection-failover。应用SHA256为d27c8fa3f43f264ad7de4a156320a187f9382a3760b5b2eaf4a3ac8d8013729a，code60/versionName0.2.51-dev与签名未变；未覆盖原dist交付。无需重建或重复测试纯发布文档。

46单元及38Android/Compose定向通过；Lint0错误/97警告/1提示，新增0。身份保证限制、旧APK降级缓存限制、手机/蜂窝/Lucky未验证边界，均在预发布说明和[网络专项交接](HANDOFF_HUB_CONNECTION_FAILOVER.md)中明确保留。

远程提交、标签及下载校验回执保存在本地evidence/github-publication/，发布完毕停止，不继续开发或修改生产服务。
