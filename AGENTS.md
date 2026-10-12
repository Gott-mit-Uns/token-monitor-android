# Token Monitor Android 增量工作约定

本项目是 Kotlin + Jetpack Compose 的 Hub 远程只读客户端。最近接管审计：2026-10-12，Android 0.2.51-dev/code60，已实现参考 v0.68.0；v0.69.0 是待对齐目标，不能声称已完成。

## 开始工作

先读 `docs/TAKEOVER_AUDIT_0.2.51.md`、`docs/NEXT_TASKS.md`，再按问题查 `docs/ARCHITECTURE.md`、`docs/FEATURE_MATRIX.md`、`docs/HUB_API_CONTRACT.md`。历史入口是 `docs/HANDOFF_0.2.51.md` 和 `docs/USER_FEEDBACK_048.md`。先目录、搜索、具体范围；不遍历证据截图、冻结源码和整个上游。

2026-10-12 接管审计时此目录及父目录没有 Git 元数据。第二阶段已按用户明确授权建立本地基线，带注释标签 `v0.2.51-dev`，分支 `fix/antigravity-dual-windows`；安全范围见 `docs/GIT_BASELINE_0.2.51.md`。此基线保存当时源码，不代表此前两天有可追溯提交。不得清理、重置或覆盖现有工作；不自行创建 remote 或推送旧 GitHub Android 仓库。

## 产品与安全边界

- 不采集本机 Token，不读取 Claude/Codex/T3/Cursor 会话文件，不移植桌面 collector、Tokscale 或本地活动轮询。
- 数据只从 Hub 获取。HTTP/SSE/重连仅在前台需要时运行；后台关闭连接、重连和在途请求。Widget 仅读缓存。
- LAN HTTP 和 Lucky HTTPS 是同一 Hub 的两个入口，不叠加数据。公网必须 HTTPS；本次要求在已验证 LAN 内优先本地 HTTP。**现有代码优先 HTTPS，变更须作为独立任务按 NEXT_TASKS 验收。** 子网匹配只许可传输，不能证明同 Hub；未经验证地址不能自动回退。401/403 不通过换地址绕过。
- 不改动 Mac、NAS、路由器及正常服务来完成 Android 开发。默认 NAS DXP4800 为 192.168.31.12/BING；DH4300plus 为 192.168.31.13/BING。设备操作遵守用户的单独授权。
- 凭据只经既有安全连接/加密存储使用，不写日志、文档、截图、命令参数或版本库。用户未授权时不读取私有响应或索取凭据以扩大审计。

## 增量开发与验证

每次选择一个 NEXT_TASKS 项，先明确证据、影响范围和验收条件；不要无理由重写已验收的八入口导航、语言布局、动效、存储及升级体系。新上游功能先证明 Hub 提供字段，再补解析和纯展示。C 类能力不得通过 Android 本地采集补齐。

只执行受影响的定向检查；修复通过后不要重复未受影响的完整套件，不以增加测试数量为目的写重复测试。保留首次失败及对应 APK/测试 APK 指纹，复跑另存，明确模拟器与真机、公网、fixture 的区别。新增字段兼容缺失、旧 Hub、full snapshot、freshness、缓存冷恢复与页面投影。

不自动递增版本、打包或发布。构建使用既有 JDK17、SDK37、Gradle wrapper；禁止 `clean` 或清除应用数据来掩盖升级问题。交付必须关联源码指纹、应用/测试 APK 校验、签名、版本及实际安装证据；传输或选中 APK 不代表手机已安装。

证据归 `evidence/<任务>/`，临时抓取归 `work/<任务>/`。及时更新上述文档，用路径代替重复展开日志。每个问题保留优先级、下一步、成功标准和未验证范围。用户要求只做审计时，文档完成后停止。
