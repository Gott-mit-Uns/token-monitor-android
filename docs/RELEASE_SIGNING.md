# 正式 Android 签名维护

首次正式系列从 `0.2.53` / code62 开始，包名为 `dev.bing.tokenmonitor.nativeclient`。经用户要求配置的新正式 RSA4096 证书，公有证书 SHA256 为 `e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202`。后续正式 APK 必须沿用此证书，不重新生成或换用 Debug 证书。

本机私有配置在 `~/.local/share/token-monitor-android/private/release-signing/`；目录权限 0700，密钥及配置 0600，均在仓库之外。需要安全备份此目录；失去私钥会影响后续同包覆盖升级。不要把目录、配置内容或备份提交 GitHub、写入聊天/日志或公开文档。

Gradle 默认读取该目录中的 `release-signing.properties`，也可以由 `TOKEN_MONITOR_RELEASE_SIGNING_PROPERTIES` 指定另一个安全配置文件路径。配置字段是 storeFile、storePassword、keyAlias、keyPassword；源码和示例中不提供实际凭据。Release 签名缺失时 packageRelease 拒绝执行；不会签成 Debug 或静默产生 unsigned 交付。

普通开发测试使用 Debug 变体；需要验证真实 Release APK 时使用 `-PtokenMonitorReleaseTest=true` 构建对应 AndroidTest。真实 Window 专项另加 `-PtokenMonitorMotionPerf=true`。沿用 JDK17、SDK37、Gradle wrapper，不清空应用数据或执行 clean 掩盖问题。

异地/云端构建需要另外通过安全方式提供相同签名配置；GitHub 中没有私钥，也没有配置 GitHub Actions 签名 Secrets。本次 APK 在本机正式签名后上传 Release，不宣称已建立云端签名发布流水线。

旧 Debug APK 与新正式证书不兼容，不能直接覆盖安装。不要先卸载有数据的旧版。迁移方案与真机验收另行安排；Release 到 Release 的后续版本保持证书和包名、递增 versionCode，并验证实际安装和数据保留。
