# 本分支安装与更新

从 [Gott-mit-Uns/token-monitor-android Release](https://github.com/Gott-mit-Uns/token-monitor-android/releases/latest) 下载本分支APK。原应用ID和固定签名保持不变，已有本分支安装可直接覆盖，不需要先卸载。安卓上游使用不同应用ID和签名，不能作为本分支的更新包。

这是只读Hub查看端。配置已有NAS或电脑Hub的HTTPS反代域名、Tailscale或局域网地址，再输入凭据验证连接。凭据不放进URL。可配置指向相同Hub与账户的家庭Wi-Fi备用地址；不修改Hub、采集端或桌面服务。

显示设置保存在安卓本地，凭据加密并排除备份。设备名跟随Hub；安卓不重新命名设备。断网时保留最后有效快照，并显示时间与连接状态。

小组件从启动器的组件选择器添加；布局受启动器分配尺寸影响。主动开启Live每60秒读取，最多一小时；关闭后不创建周期后台任务。常规前台SSE及轮询行为见 [Hub说明](../HUB.md)。

当前签名版本在模拟器验证覆盖安装。真实手机／启动器、已认证NAS和Android17的未验证范围见对应Release，不因下载或安装成功就视为日常使用已验收。
