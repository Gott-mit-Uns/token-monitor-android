# 上游同步规范

开始先读取 AGENTS、HANDOFF、VALIDATION 和 FEATURE_MATRIX。按当前HANDOFF的固定目标，只比较上一基线之后的增量；固定tag及解析后的commit，禁止以浮动main代替交付基线。

逐提交判断生产端、Hub合同、Android消费和桌面专属范围；URL/事件名不变仍可能有payload增量。新字段检查HTTP、SSE full/freshness、解析、缓存冷恢复及展示；本地采集、SQLite恢复、Electron updater、窗口/鼠标/桌面系统集成排除，不以UI相似宣称完整对齐。

复用受影响测试，区分源码样例、模拟器、实网和真机；源端发布不证明部署。运行结果只更新 VALIDATION，待办/固定目标只更新 HANDOFF，费用语义只更新 COST_MODEL。逐提交审计保留在有限[档案](https://github.com/Gott-mit-Uns/token-monitor-android/blob/cfab5438ed5dac6537c68affb385517ccfa44c0e/docs/archive/INDEX.md)，普通任务总结放PR，不再新增逐版本报告。发布仍遵循 [VERSIONING](../VERSIONING.md) 和 [RELEASE_SIGNING](RELEASE_SIGNING.md)，必须获得该操作授权。
