# Android 0.2.64 / 73 正式发布完成

2026-10-15，Asia/Shanghai。用户已明确授权把 0.2.64 合入主线、推送、创建正式 GitHub Release，并上传 APK 与校验文件；手机由用户自行安装，NAS 不动。该授权取代 2026-10-14 候选阶段的发布暂停边界。

已在独立 `release/0.2.64` 工作区从实时 `origin/main` / `567022709646aac52279874889b97656cfe7b5d5` 快进合入已验收候选 `ccb477acfc8739166659c19cd217f1013189c771`。原主目录的未提交 NEXT_TASKS、AGENTS-original.md 和历史审计保持不动；没有强制推送或覆盖历史版本。

正式包沿用精确已验收候选字节，仅将文件名改为 `token-monitor-android-0.2.64.apk`，15,238,142 bytes / 14.53 MiB，SHA-256 `df7ab620f0ca9d086ff30da761a232cb5bd99a9053356a3688c61006f1ab8c56`。原包名和正式证书保持，Release 非 debuggable。没有混入新的 R8／资源瘦身或字体/依赖更改。

Tag `v0.2.64` 与正式 manifest 记录发布提交 `a7d509903a46223af5c0ecb6f82937aee16108b3`，并区分原产品构建提交 `ab9a6acee07ddee302f23dd7e033cd19803e9f2e`、最终测试提交 `fc206065daf9b9fc29fd1c9b3136b619f83ae2bf`。发布树与构建树的主源码和 Gradle 构建输入相同；APK 内嵌 VCS 信息为 `NO_VALID_GIT_FOUND`，不声称内嵌了发布提交。关联由源树、Git、外部 manifest、实际 APK 与安装回读校验保证。发布后仅补文档回执；Tag 和附件字节固定，不重建或覆盖。

验收沿用精确最终产品的 455 单元、Lint 0错误/96警告/1提示、49原功能回归、移除负向2项、升级2项、浅色10项、深色10个方法、网络18个方法及语言复测5项。失败整组与后续定向复测按原范围保留，不把分项通过写成原整组全绿，不把最终测试包冒称重跑全部。详见 [完整开发交接](archive/history/HANDOFF_0.2.64.md)。

2026-10-15 04:35:56（Asia/Shanghai）[正式 Release](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.2.64)已发布，非草稿、非预发行并标最新。正式附件为 APK、SHA256SUMS.txt、release-manifest.json、VALIDATION.md。四个草稿附件完整下载逐文件一致后才发布；正式发布后再下载四附件复核，均与本地大小、SHA 和资产元数据一致，无需认证的公开 APK 与校验文件也完整下载一致。草稿按 Tag 的 API 首次返回 404 的失败保留，改用官方 Release ID 413156120 回读成功后才发布，没有把失败或元数据当作下载证明。证据在 `evidence/publication-064/`，详见 [发布回执](GITHUB_PUBLICATION.md)。

实体小米、OEM桌面、LAN/HTTPS/蜂窝和锁屏实网本轮未执行；历史失败不改写。手机自装，未修改 NAS、Hub、Agent、Lucky、路由或其他生产服务。包体积对比及优化提示词仅是后续工作的分析输入，本轮保持 0.2.64 产品和安装包不变。
