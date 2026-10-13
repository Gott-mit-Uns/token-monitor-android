# Android 客户端手动更新入口

关于页的 Android 客户端更新区与 Hub 构建版本分开显示。仅点击“检查更新”时发起一次匿名 HTTPS 请求；进入页面、恢复前台或 Hub SSE 更新不触发检查。进入后台或离开关于区取消在途检查；再次回前台仍须主动点击。检查失败不修改 Hub 状态、连接或缓存。

请求固定为 `https://api.github.com/repos/Gott-mit-Uns/token-monitor-android/releases/latest`，不读取或发送 GitHub 凭据，不接受重定向，不创建后台任务。GitHub 的 latest 正式发行端点加上本地 `draft=false` / `prerelease=false` 验证排除预发布和草稿。超时为 20 秒，响应上限 1 MiB。网络失败、仓库/API 无法访问、限流、发行材料不符各有提示，Android 发行页始终可用。

Android 版本沿用现有正式发行的 `vMAJOR.MINOR.PATCH` 标签、三段非负整数版本规则，逐段数值比较；不比较字符串大小，不使用桌面版本或 Hub revision。未找到可复用的 Android 版本比较器，因此增加了局部纯函数 `AndroidReleaseVersion`，拒绝 dev/rc、额外构建标记、非规范前导零和数字溢出。

发行说明必须来自指定 Android 仓库的精确标签页；APK 必须是同标签的 `token-monitor-android-MAJOR.MINOR.PATCH.apk`，状态 uploaded、大小大于零、唯一匹配，下载链接须与官方 GitHub 仓库/标签/名称精确相符。不选择 debug、其他仓库或相似名称的资产。此检查确认发行元数据与资产归属，Android 系统在安装时继续验证正式签名与升级兼容性；客户端不下载或自行安装 APK。

“当前版本说明”、Android 发行页、新版本发行说明及 APK 链接交由浏览器打开。安装由 Android 系统完成。不存在静默安装、定时检查、后台下载服务或逐版本 Hub 请求。发行说明在浏览器展示，不把远端 Markdown 注入应用界面。

定向验收入口：`AndroidUpdateTest` 覆盖数值比较、正式资产限制、失败恢复、检查去重及后台取消；`AndroidUpdateScreenTest` 覆盖可访问按钮、四种状态、精确浏览器链接和真实 Activity 生命周期，保存不可访问/可更新状态截图。测试结果、最终 APK 指纹和真机未验证项随本次统一发布交接记录；此文档不将测试存在或编译成功视为已执行通过。
