# Hub 连接

支持 Token Monitor 独立 Node Hub（NAS）、电脑 Host Hub 和 HTTPS 反代域名。

- 家庭局域网示例：`http://192.168.1.10:17321`，开启私网访问；Android 17 还需要系统局域网权限。
- 外网示例：`https://token.example.com`，可信 HTTPS 证书，填写根地址，不加 `/api/stats`。
- 认证使用 Hub 的共享密钥。手机本地输入，Keystore 加密存储，排除备份。
- Lucky 等反代需转发 Authorization 和 `/api/` 请求，允许 SSE 长连接，避免缓冲流。
- 前台启动刷新，优先 SSE，断线后每 60 秒后备轮询及退避重连。退出前台后停止常规网络刷新。
- 小组件显示共享缓存；手动刷新一次读取；Live 每 30 秒检查统计，最多一小时，可停止。默认不创建周期后台任务。

历史 cf 名称只保留在内部应用 ID、签名配置及现有仓库地址中，以保留覆盖安装兼容性。
