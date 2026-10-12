# UI 对齐基线与未完成验收

2026-10-12；已实现桌面参考v0.68.0，目标v0.69.0。不要将目标版本当作当前完成版本。

## 应继承的成果

| 模块 | 既有证据 | 边界 |
|---|---|---|
| 八入口导航 | `docs/USER_FEEDBACK_048.md:9`；`work/s2-layout-matrix/matrix-summary.json` | 360/393/411dp、fontScale1/1.3/2、浅深18组36项；一次可见/无省略/48dp目标是模拟器实际窗口结果 |
| 语言菜单/选中值 | 同反馈文档及18组布局矩阵 | 中/英/SYSTEM及standard/largest；不能以缩小字体解决溢出 |
| 设置/来源/工具披露、列表、用量条、部分主页历史图 | `evidence/package-051/final/build-checks.md`；`work/s2-boundary-scale0`、`s2-boundary-scale1b`、`s2-home-window-scale0`、`s2-home-window-scale1` | 有减少动效/反向/后台恢复专项；不是全部图表或真机FPS证明 |
| 主页数字与其他既有动画 | `HEADLINE_MOTION_PARITY.md`、`DESKTOP_SOURCE_MOTION_PARITY.md`、`HUB_CAPACITY_FRESHNESS_REUSE.md` | 保留桌面触发规则，不能把局部补丁耗时当整体流畅度 |
| 覆盖安装旧数据 | `work/s4-upgrade-048-051/run-summary.md` | 自有API36，0.2.48→0.2.51，UID/文件字节保持及旧记录解密；真机未验收 |

早期home-window run2–4、普通batch4失败是历史试跑，不覆写。最新反馈首段和最终package回执是最终包结论；不能选择一份旧失败声称当前仍失败，也不能删除失败来声称首轮全过。旧8报告/46PNG/26桌面抓取未在本轮重跑，不扩展封包范围。

## v0.69 UI 改动计划

第二阶段已独立完成Antigravity单可见组双周期pick及标签，产品布局组件保持原实现。受影响单元、浅深Compose交互和8组字体/模式窗口证据见 [双周期交接](HANDOFF_ANTIGRAVITY_DUAL_WINDOWS.md)。大字采用合成LocalDensity；真机、真实系统字号和公网边界不变。仅此模块对齐v0.69，不能把全局已实现参考改称v0.69。

详见 [功能差异表](FEATURE_MATRIX.md)。独立接入Antigravity双周期pick/名称、会话等待状态、Codex/T3/Grok分组与成员明细、设备名称resolver、Lucide静态资源；每项复用既有组件、字号、48dp目标、弹层与reduce-motion体系，不重写页面。零Token本地会话不在远程合同内。

## 实体设备与公网验收单

以下全部**未验证**；当前adb只见emulator-5556。历史电脑端Lucky成功读取不等于Android公网通过。需要用户提供可连接且授权测试的手机；建议原测试目标小米13 Ultra，但本轮没有验证其在线或安装状态。

| 测试 | 最小操作与成功标准 | 安全/证据边界 |
|---|---|---|
| 覆盖安装/保留旧数据 | 确认手机原版本及签名；先记录非敏感设置/缓存状态；覆盖安装交付code60；确认UID、设置、旧缓存可解密、启动成功 | 不卸载、不清数据；真实私有文件不拷到文档；传输成功与安装成功分别记录 |
| 触摸/字体/弹窗 | 菜单八入口、语言完整；系统默认/大字，应用standard/largest，浅深；设置、工具明细与弹窗边界，操作无重叠 | 不将已有模拟器矩阵改称真机；截图避开凭据/标题 |
| 图表与动画 | 真机主页数字、用量条、历史热图/趋势、反向点击、减少动效、后台后首次操作 | 看真实触摸响应；卡顿记录可复现步骤，不只用虚拟时钟 |
| 公网Lucky | 手机关闭Wi-Fi，用蜂窝访问已保存HTTPS；系统信任TLS、应用私有stats/SSE/明细成功 | 不跳证书校验，不贴凭据；桌面health200不代替 |
| LAN直连 | 回到指定Wi-Fi，验证本地HTTP、Network绑定和同Hub，不出现两个总量 | 现有恢复优先HTTPS需作为已知差异记录 |
| 前后台/恢复 | 前台只一条活动流，Home/锁屏后台零流及零重连，恢复首full snapshot与数据一致 | 同时观察手机与服务器活动流计数；无需改服务器服务 |
| 网络切换与失败 | Wi-Fi→蜂窝→Wi-Fi，短断网、不可用入口、快速反复切换；保留缓存，无重复流/旧响应回写 | 非401失败与认证失败分开；已验证回退策略本身尚待实现 |
| 重连全量一致性 | 重连首snapshot与同Hub权威stats摘要一致，历史/别名/价格/订阅revision失效有效 | 不存响应正文；同步时点差异须用revision/时间边界解释 |

文档和fixture支持只是测试准备，本轮没有执行上述实体设备步骤，也没有授权生产服务变更。
