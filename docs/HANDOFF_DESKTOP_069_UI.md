# V4 桌面0.69功能与UI交接

起始提交9f45fd270ba7d790d90e50ddb1ee7dda2057baa5，分支feat/desktop-069-parity-ui。继承Antigravity双周期和V3连接策略，不修改生产服务，不推送或发布GitHub。

## 阶段A：适用性边界

依据现有FEATURE_MATRIX、固定上游3f106ae、sessionLive/sessionRows/shared usage的相关函数和当前Android代码，不重复16提交审计。当前生产私有字段仍未验证；Android支持可选v0.69合同不等于生产Hub已升级。

| 功能 | 当前核查与本轮范围 |
|---|---|
| waitingForInput/liveActivity | Android缺失，v0.69合同允许；增加可选消费和30秒租约，旧字段回退。部署字段存在性E |
| 等待批准区别 | 远程合同只有generic waiting及Codex输入等待，无法证明approval原因；不推断批准状态 |
| Codex/native/T3/parent关系 | Android缺失，v0.69合同允许；纯展示分组、成员账目只计一次。缺失/冲突/循环保守处理 |
| Grok Bot | 旧Hub sessionId/models足够支持基本分组；桌面跨历史overlay不上传，不补本地采集 |
| Antigravity标题/重命名 | 已读取title，验证full快照替换；真实生产端授权/更新未验证 |
| Antigravity原生速度 | 已消费服务器timed counters，验证缺失/有效值；不估算官方读数，实际产出E |
| Antigravity推理Token | 已按Hub权威total/history读取；验证不额外加reasoning，实际生产端修复E |
| Antigravity双周期 | 已完成，保留定向回归 |
| Cursor结束 | 既有turnEnded消费，补状态测试；生产端检测不移植 |
| 设备名称 | 展示入口分散，统一本地别名→Hub displayName→deviceId→hostname→device |
| Lucide导航 | 静态图标可对齐固定上游，保留八入口和触摸目标 |
| 零Token本地活动/Windows Dock/collector | 不适用；当前上传合同不提供zero-token/local overlays，不造数据 |

## 阶段计划与验收

A文档→B可选元数据/状态/分组/设备与导航→C左下导航→D设置布局→E关于版本→F最终构建，每组独立提交。左下DesktopViewSwitcher实际是页面轮换/菜单，当前没有全页compact模式；常规文字与窄屏图标呈现复用同一组件，不移植窗口控制。

待实施结果、截图/测试数量、APK签名/校验及最终SHA在后续阶段补齐。
