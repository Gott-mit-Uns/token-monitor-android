# Android 客户端更新入口

关于页的 Android 客户端更新区与 Hub 构建版本分开显示。应用启动或恢复前台时按需发起一次匿名 HTTPS 请求，成功检查后持久化节流 24 小时，失败也持久化节流 1 小时，成功后清除失败退避记录。手动“检查更新”可立即重试；进入关于页复用应用级状态，Hub SSE 不触发更新检查。进入后台取消在途检查，取消不会记录成功检查或锁死下次前台重试。不创建后台调度或弹窗。检查失败不修改 Hub 状态、连接或缓存。时间倒退后允许重新检查。

请求固定为 `https://api.github.com/repos/Gott-mit-Uns/token-monitor-android/releases/latest`，不读取或发送 GitHub 凭据，不接受重定向，不创建后台任务。GitHub 的 latest 正式发行端点加上本地 `draft=false` / `prerelease=false` 验证排除预发布和草稿。超时为 20 秒，响应上限 1 MiB。网络失败、仓库/API 无法访问、限流、发行材料不符各有提示，Android 发行页始终可用。

Android 正式版本采用 `<桌面基线>-<两位Android修订>`（例如 `0.72.0-01`），保留三段数字历史版本和历史组合名解析。`AndroidReleaseVersion` 逐段数值比较，正式修订号参与排序；不比较字符串大小，不使用 Hub revision 或桌面最新版查询结果决定 Android 更新。拒绝 dev/rc、额外构建标记、非规范前导零、00 修订和数字溢出。按2026-10-16用户新要求，正式Release直接作为latest，Tag、APK文件名与versionName使用同一正式编号；停止新建旧编号兼容发行。只识别旧编号的旧更新器需手动覆盖安装一次，历史解析保留用于已有版本比较。不能改写历史Tag。

发行说明必须来自指定 Android 仓库的精确标签页；APK 必须是同标签的 `token-monitor-android-<版本>.apk`（正式修订；旧编号仅保留历史解析），状态 uploaded、大小大于零、唯一匹配，下载链接须与官方 GitHub 仓库/标签/名称精确相符。不选择 debug、其他仓库或相似名称的资产。此检查确认发行元数据与资产归属，Android 系统在安装时继续验证正式签名与升级兼容性；客户端不下载或自行安装 APK。

“当前版本说明”、Android 发行页、新版本发行说明及 APK 链接交由浏览器打开。安装由 Android 系统完成。不存在静默安装、定时检查、后台下载服务或逐版本 Hub 请求。发行说明在浏览器展示，不把远端 Markdown 注入应用界面。

验收结果仅记录在 [VALIDATION](VALIDATION.md)，待办见 [HANDOFF](HANDOFF.md)。
