# 当前验收记录

适用：Android `0.72.0-01` / code76；治理基线：`cfab5438ed5dac6537c68affb385517ccfa44c0e`；检查日期：2026-10-17（Asia/Shanghai）。来源：固定基线的联合交接、发布回执、上轮整理记录及保留的原始证据指针。本次文档治理不重新构建APK，不执行设备/生产网络验收；下面历史结果按各自提交和工件继承，不能改称本轮实跑。

## 现行验证方法

沿用Wrapper/JDK17/SDK37，优先受影响的定向测试；不clean、清数据、换证书或放宽断言。JVM用`:app:testDebugUnitTest`，UI须区分测试编译与实际执行、隔离模拟器与真机、公开fixture与真实账号。可选live/permission/perf/capacity只在明确开关启用时计入范围。正式发行按VERSIONING和RELEASE_SIGNING，签名/源码/应用及测试APK指纹、安装/启动/数据保留分别留证。共享三份home/limit/subscription样例唯一来源为`app/src/sharedTest/resources/`，生成工具只写该目录；本次不改样例、配置和脚本。

## 沿用产品验证（旧提交，未重跑）

| 来源与工件 | 已记录结果 | 适用限制 |
|---|---|---|
| build5b1fb38 / APK db90f7e9…，最终Tag3c4d2d6；2026-10-16旧回执 | 514单元0失败/错误/跳过；Lint0错误/104警告/1提示；受影响9项+明确opt-in冷启动2项 | 本次未重跑，不称零告警。release76原日志、unit/lint-summary和affected-ui/cold各分批记录 |
| 旧候选16f92a7 / APK17b7470e… | API36视觉78次分批通过；10张归属原图 | 仅限定继承未改视觉源；不冒称db90重新跑全78；SharedData首次ComposeNotIdle和未opt-in assumption跳过保持 |
| 正式公开db90 / 同证书code73→75→76 | 旧记录公开升级链及合成加密缓存/历史/四偏好冷保留；公开附件大小/digest/SHA/SOURCE核对 | 不代表真实用户资料或手机升级；早期同code退役候选链与公开链分开 |
| 2026-10-16正式命名更正 / 同字节db90 | 12项JVM、最终公开APK上2项模拟器更新入口检查 | 新正式latest已停止旧兼容发现；旧更新器曾通过记录仅属更名前，需手动安装一次。首次匿名API403保留 |
| 旧0.2.53/code62 / a3f03cf基线 / 已安装SHA dff4af84… | 小米13Ultra人工启动、有限菜单/额度/设置/Wi-Fi HTTPS与前后台恢复 | 未安装/卸载/清数据，不算升级；font2裁切/动态字号疑点、蜂窝/完整SSE/软键盘矩阵仍缺，见档案 |
| 上轮整理da3edc2起点→cfab543 / DOCS_CLEANUP旧记录 | 定向10及完整514 JVM；额度/订阅7浅+7深；构建成功 | 两次并行Gradle失败保留；临时Provider适配测试包不等于最终PR测试包全跑；额外背景3项2过1失败未定位 |

原始 `evidence/desktop-072/`、`evidence/docs-cleanup/`、`evidence/phone-053/` 等不在Git内，本次不删除、不迁移、不宣称新checkout具备这些文件或已复验。首次Lint内部异常、磁盘不足、旧版本harness签名不兼容、截图省略、SharedData虚拟时钟问题及各候选指纹在固定历史提交仍可恢复。沿用结果的详细源路径见[历史索引](archive/INDEX.md)；合成截图/manifest及合法release-records逐字保留。

## 本轮文档治理实跑

检查范围：全文档本地目标/锚点、历史迁移引用、README授权/hash、禁止新增过程命名、档案新增限制、门禁正反例、语言资源与最终差异。实际通过：文档门禁及其正反例（新增重复命名、断链/锚点、README缺授权/旧授权复用、冻结档案新增、合法发布输入/测试夹具）；全仓相对Markdown及HTML图片链接、固定历史Git目标/锚点检查0问题；语言资源670对一致；git diff --check通过。22份保留审计逐字核对，除链接/HTML图片相对路径重定位外正文一致。既有非Markdown跟踪文件零变更：全部app源码/测试/夹具、Gradle输入、字体/截图及manifest、来源/签名和release-records保持字节不变。新增仅文档门禁及独立PR工作流，不触发APK构建。

数量按Git跟踪文件统计：962→854，Markdown156→46；移除114份旧路径（其中2个索引/方法入口原位重写），22份独有证据改为有限同一档案目录，普通逐版本过程用固定Git历史恢复。原主工作区3项未提交内容保持。工作流语法经结构检查；实际GitHub运行与自动审查须另记，未返回不能算通过。

本轮不做APK构建/安装、真机、Hub实网或生产部署。未完成事项与背景图失败只在 [HANDOFF](HANDOFF.md) 持续跟进，不因删历史流水而关闭。
