# 安装与覆盖升级

从项目 [GitHub Releases](https://github.com/Gott-mit-Uns/token-monitor-android/releases/latest) 下载正式 APK，并对照同一发行的 SHA256SUMS.txt。正式包名为 `dev.bing.tokenmonitor.nativeclient`；安装包命名及版本顺序遵循[VERSIONING](../VERSIONING.md)。

保留数据升级时使用同包名、同正式签名且 versionCode 更高的 APK 覆盖安装。不通过卸载、清数据或更换证书绕过兼容问题。历史 Debug 签名不能当作正式签名升级来源，需先明确原版本、备份及安全迁移方式。

仅支持三段数字旧编号的更新器需要手动下载一次正式 APK 覆盖安装；停止旧编号兼容发行不影响同包名和正式签名的安装连续性。下载成功、安装成功、启动成功和历史/设置保留是不同验收点。

应用配置用户自己的 Hub 地址并按现有方式提供认证；手机不采集本地 Token、不搬入桌面 Cookie 或新增第三方登录。LAN HTTP 与公网 HTTPS 的准入和失败边界见[Hub合同](HUB_API_CONTRACT.md)及[AGENTS](../AGENTS.md)。

实体手机与实网尚待验证范围见[NEXT_TASKS](NEXT_TASKS.md)；旧开发包和各阶段测试说明保留于[历史安装快照](archive/history/INSTALL_BEFORE_CLEANUP.md)，不作为当前下载指引。
