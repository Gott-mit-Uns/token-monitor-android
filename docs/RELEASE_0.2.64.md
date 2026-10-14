# Android 0.2.64 / 73 正式发布准备

2026-10-15，Asia/Shanghai。用户已明确授权把 0.2.64 合入主线、推送、创建正式 GitHub Release，并上传 APK 与校验文件；手机由用户自行安装，NAS 不动。该授权取代 2026-10-14 候选阶段的发布暂停边界。

已在独立 `release/0.2.64` 工作区从实时 `origin/main` / `567022709646aac52279874889b97656cfe7b5d5` 快进合入已验收候选 `ccb477acfc8739166659c19cd217f1013189c771`。原主目录的未提交 NEXT_TASKS、AGENTS-original.md 和历史审计保持不动；没有强制推送或覆盖历史版本。

正式包沿用精确已验收候选字节，仅将文件名改为 `token-monitor-android-0.2.64.apk`，15,238,142 bytes / 14.53 MiB，SHA-256 `df7ab620f0ca9d086ff30da761a232cb5bd99a9053356a3688c61006f1ab8c56`。原包名和正式证书保持，Release 非 debuggable。没有混入新的 R8／资源瘦身或字体/依赖更改。

Tag 与正式 manifest 将记录实际发布提交，并区分原产品构建提交 `ab9a6acee07ddee302f23dd7e033cd19803e9f2e`、最终测试提交 `fc206065daf9b9fc29fd1c9b3136b619f83ae2bf`。发布树与构建树的主源码和 Gradle 构建输入相同；APK 内嵌 VCS 信息为 `NO_VALID_GIT_FOUND`，不声称内嵌了发布提交。关联由源树、Git、外部 manifest、实际 APK 与安装回读校验保证。

验收沿用精确最终产品的 455 单元、Lint 0错误/96警告/1提示、49原功能回归、移除负向2项、升级2项、浅色10项、深色10个方法、网络18个方法及语言复测5项。失败整组与后续定向复测按原范围保留，不把分项通过写成原整组全绿，不把最终测试包冒称重跑全部。详见 [完整开发交接](HANDOFF_0.2.64.md)。

正式附件为 APK、SHA256SUMS.txt、release-manifest.json、VALIDATION.md。先准备草稿和完整回读校验，确认四附件逐文件一致后正式发布，再下载正式附件复核；最终结果写入 [发布回执](GITHUB_PUBLICATION.md)。本文件为发布前准备事实，不单独证明发布已成功。

实体小米、OEM桌面、LAN/HTTPS/蜂窝和锁屏实网本轮未执行；历史失败不改写。手机自装，未修改 NAS、Hub、Agent、Lucky、路由或其他生产服务。包体积对比及优化提示词仅是后续工作的分析输入，本轮保持 0.2.64 产品和安装包不变。
