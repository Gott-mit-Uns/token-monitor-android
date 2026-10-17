# 有限历史证据索引

冻结日期：2026-10-17；来源提交 `cfab5438ed5dac6537c68affb385517ccfa44c0e`。本目录仅保留下表22份独有审计/测量/失败/来源资料，属于封存证据，不是当前验收或交接。文中“当前/待办/通过”均限原版本，不能覆盖现行HANDOFF/VALIDATION。不再自动向本目录新增过程记录；新增历史证据须先说明不可替代原因并由用户具体确认。

| 保留资料 | 原因 |
|---|---|
| [DESKTOP_070_DIFF.md](DESKTOP_070_DIFF.md) | 独有源码/字段逐项审计 |
| [DESKTOP_071_DIFF.md](DESKTOP_071_DIFF.md) | 独有源码/字段逐项审计 |
| [DESKTOP_072_ALIGNMENT.md](DESKTOP_072_ALIGNMENT.md) | 独有源码/字段逐项审计 |
| [ALIGN_072_CURSOR.md](ALIGN_072_CURSOR.md) | 独有源码/字段逐项审计 |
| [ALIGN_072_SESSIONS.md](ALIGN_072_SESSIONS.md) | 独有源码/字段逐项审计 |
| [ALIGN_072_UPDATE_OPENCODE.md](ALIGN_072_UPDATE_OPENCODE.md) | 独有源码/字段逐项审计 |
| [FULL_RENDERER_AUDIT.md](FULL_RENDERER_AUDIT.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |
| [LIST_INTERACTION_AUDIT.md](LIST_INTERACTION_AUDIT.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |
| [DESKTOP_SOURCE_MOTION_PARITY.md](DESKTOP_SOURCE_MOTION_PARITY.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |
| [DESKTOP_NUMBER_RUNTIME.md](DESKTOP_NUMBER_RUNTIME.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |
| [HUB_CAPACITY.md](HUB_CAPACITY.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |
| [HUB_CAPACITY_PERFORMANCE_BASELINE.md](HUB_CAPACITY_PERFORMANCE_BASELINE.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |
| [HUB_CAPACITY_FRESHNESS_REUSE.md](HUB_CAPACITY_FRESHNESS_REUSE.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |
| [V5_PERFORMANCE_EVIDENCE.md](V5_PERFORMANCE_EVIDENCE.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |
| [PHONE_ACCEPTANCE_0.2.53.md](PHONE_ACCEPTANCE_0.2.53.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |
| [USER_FEEDBACK_048.md](USER_FEEDBACK_048.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |
| [APK_SIZE_COMPARISON_064.md](APK_SIZE_COMPARISON_064.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |
| [GIT_BASELINE_0.2.51.md](GIT_BASELINE_0.2.51.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |
| [UI_072_VALIDATION.md](UI_072_VALIDATION.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |
| [REA_NATIVE_WIDGET_063.md](REA_NATIVE_WIDGET_063.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |
| [MAC_ANDROID_WIDGET_COMPARISON_063.md](MAC_ANDROID_WIDGET_COMPARISON_063.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |
| [WIDGET_LAUNCHER_EVIDENCE.md](WIDGET_LAUNCHER_EVIDENCE.md) | 独有测量、失败、运行/来源归属证据；不可用摘要替代 |

已移除普通过程记录由正常Git历史恢复，无改写历史：

- [整理前全部docs](https://github.com/Gott-mit-Uns/token-monitor-android/tree/cfab5438ed5dac6537c68affb385517ccfa44c0e/docs)包含各专项PARITY、旧计划和验收，代码/测试保留。
- [原联合交接](https://github.com/Gott-mit-Uns/token-monitor-android/blob/cfab5438ed5dac6537c68affb385517ccfa44c0e/docs/HANDOFF_0.72.0-01.md)保留4534/f7/17b7/db90候选、首次失败、生产输入绑定、公开升级和命名回执原文。
- [原发布回执](https://github.com/Gott-mit-Uns/token-monitor-android/blob/cfab5438ed5dac6537c68affb385517ccfa44c0e/docs/GITHUB_PUBLICATION.md)、[前轮整理验收](https://github.com/Gott-mit-Uns/token-monitor-android/blob/cfab5438ed5dac6537c68affb385517ccfa44c0e/docs/DOCS_CLEANUP_VALIDATION.md)、[原旧档案](https://github.com/Gott-mit-Uns/token-monitor-android/blob/cfab5438ed5dac6537c68affb385517ccfa44c0e/docs/archive/INDEX.md)提供历史入口，不复制多套工作区快照。
- [原性能方法](https://github.com/Gott-mit-Uns/token-monitor-android/blob/cfab5438ed5dac6537c68affb385517ccfa44c0e/docs/HUB_CAPACITY_PERFORMANCE_PLAN.md)、[原R8提案](https://github.com/Gott-mit-Uns/token-monitor-android/blob/cfab5438ed5dac6537c68affb385517ccfa44c0e/docs/APK_SIZE_OPTIMIZATION_PROMPT.md)只供追溯，后者仍延后，不构成执行授权。

许可证、所有测试/夹具、固定上游来源指纹、截图及manifest、改名前校验清单均不删除。缺失于Git的本地evidence/dist/work只保留历史指针，本轮未验证可用，也不得伪造。普通已提交过程记录能从固定commit完整恢复，不需要为每版留一份工作区副本。
