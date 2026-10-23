# 上游同步规范

开始先读取 AGENTS、HANDOFF、VALIDATION 和 FEATURE_MATRIX。按当前HANDOFF的固定目标，只比较上一基线之后的增量；固定tag及解析后的commit，禁止以浮动main代替交付基线。

逐提交判断生产端、Hub合同、Android消费和桌面专属范围；URL/事件名不变仍可能有payload增量。新字段检查HTTP、SSE full/freshness、解析、缓存冷恢复及展示；本地采集、SQLite恢复、Electron updater、窗口/鼠标/桌面系统集成排除，不以UI相似宣称完整对齐。

复用受影响测试，区分源码样例、模拟器、实网和真机；源端发布不证明部署。运行结果只更新 VALIDATION，待办/固定目标只更新 HANDOFF，费用语义只更新 COST_MODEL。逐提交审计保留在有限[档案](https://github.com/Gott-mit-Uns/token-monitor-android/blob/cfab5438ed5dac6537c68affb385517ccfa44c0e/docs/archive/INDEX.md)，普通任务总结放PR，不再新增逐版本报告。发布仍遵循 [VERSIONING](../VERSIONING.md) 和 [RELEASE_SIGNING](RELEASE_SIGNING.md)，必须获得该操作授权。

## Android 持续排除项

以下三项由用户明确要求从手机端移除，后续桌面增量对齐不得重新引入，除非用户明确重新授权：

| 排除功能 | 排除范围 | 必须保留的相关能力 |
|---|---|---|
| 桌面最新版查询 | 手机端桌面 Release API 请求、查询状态、按钮与桌面发布说明入口 | Android 自身手动更新、当前 Android 版本说明、静态桌面参考版本及来源许可 |
| 自定义字体名称输入 | 字体菜单的自定义选项、名称/字体列表编辑、应用及输入预览 | 内置/系统/等宽字体预设、字号、独立重置；旧版自定义字体的存储读取与渲染回退 |
| 主题码操作 | 主题码输入、复制、粘贴、编码、解析及应用 | 主题预设、浅深/系统模式、颜色选择器、厂商颜色，以及旧版已保存配色 |

对齐时逐项检查入口、专用实现、文案和受影响测试。源码中的 Desktop 前缀不代表应删除；不因这三项排除而改变 Hub 数据合同、缓存格式、统计算法或其他已保留功能。旧偏好只读取，不因打开设置而重写、清空或强制回默认。新版本采用现有存储格式，用户主动改预设或重置时才更新对应选择。
