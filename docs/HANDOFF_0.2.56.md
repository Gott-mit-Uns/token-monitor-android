# 0.2.56 — 多任务统一整合与发布

2026-10-12，Asia/Shanghai。用户明确要求将其他任务更新合并一起发布。整合分支release/unified-056，工作区work/token-monitor-unified-056，起点为已正式发布0.2.55/main f4c3bc6。版本统一0.2.56/code65，不改写0.2.55 Tag/Release，不扩展至生产环境操作。

## 合入内容与来源

- 保留0.2.55的Auto Review会话混合排序，产品提交a1ba688。
- 来源与许可更正7a69170：上游归属改为项目仓库，开发版本/提交留在追溯文档；图标来源、67项运行依赖说明和生成模板一致。专项交接HANDOFF_SOURCE_LICENSE_ATTRIBUTION.md。
- 指定页面优化d20a509，经8cc99a8合入0.2.55基线，最终专项交接7a829dc：菜单去额外间距，48dp触控与选择/退出保留；标准系统/应用字号的精确Token在可用宽度内按实际字形测量适度适配，与缩写保持同排；大字体继续保留完整尺寸并分行。关于页显示已有health的公开构建修订及验证状态，缺失/异常保守展示，不将health.version协议号当软件SemVer。元数据通过既有验证读取传递与缓存，不增加读取或部署Hub。详见HANDOFF_SCOPED_UI_0.2.56.md。
- 回归验证修正c8c0614：只调整既有freshness测试的HTTP重新验证观察检查点，不改SSE/重连产品逻辑。

保留各任务完整提交历史。整合中解决来源说明与任务入口两处冲突，保留项目仓库URL、新增Hub构建信息MIT归属及双方交接，并同步通知生成器，避免重新生成丢失归属。没有删除其他任务的修改。

## 验证与首次失败

最终整合包在独立Release056/API36/emulator-5590运行；未操作手机或已有5556。419项Release单元通过，最终Lint0错误/93既有警告/1提示。67项依赖通知生成--check通过，直接检查APK内的项目来源URL和Hub构建信息MIT归属正确。

整合包12项页面测试通过（指定页面、会话排序、导航、关于/0.69页面）；23项菜单动效、health/缓存冷恢复、HTTP生命周期、TLS、网络替换与认证回归通过。末次合入增强health次数与freshness检查后，最终测试包4项freshness/HubLifecycle通过。阶段复跑不累计为独立测试数。另有正式0.2.55→0.2.56覆盖安装两阶段通过：应用UID不变，独立合成加密历史/偏好文件指纹保持，新版成功解密，已安装字节匹配交付文件。它不代表实体手机的所有真实缓存已验收。

首页专项先独立完成26组/78项真实系统配置矩阵：320/411dp、系统字号1.0/1.3/2.0、浅深/中英，另有应用最大字号两组；其42项相关回归通过。该矩阵使用专项候选，最终整合包因许可资产不同而哈希不同；UI/网络产品源码相同，最终包另执行上述整合检查，区分候选与正式包证据。

首次回归的freshness检查失败均保留。正式0.2.55基线也复现同样失败：EOF后客户端已有HTTP重新验证，夹具返回07:00快照，原断言错误地要求保留重连前08:01对象且HTTP次数仍为1。修正后明确等待第二HTTP、权威快照及恰好多一次保存完成，再保留严格assertSame/写次数不变/live=false与HTTP次数=2；后续新full/freshness业务引用断言完整保留。0.2.55基线和0.2.56均通过修正后的严格检查，没有用assertEquals替代身份断言，也未修改产品重连策略。

专项首失败、基线对照、矩阵及包指纹保留在work/token-monitor-ui-quality-055/evidence/scoped-ui-quality-055/。统一构建、419单元、35项初始整合检查、末次4项、原freshness首失败、升级/签名/安装、截图和下载回执在evidence/unified-release-056/。证据不入Git，截图全部为公开fixture；新版真机、蜂窝/Lucky、生产Hub字段和长期性能未验证。

## 正式交付与停止

包名dev.bing.tokenmonitor.nativeclient；沿用既有正式证书SHA256 e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202。正式APK SHA256 d41dc0a075a83824f48100a54f6f0a78cc84a7f371c4d7398631e2319ae51a1f，最终测试APK SHA256 f03218536c8f1d3e683af1563b38f9707ed1e8c227eac0d7707d45f03a39eee5。测试修正不改变产品APK。

交付dist/release-0.2.56/：正式APK、SHA256SUMS、release-manifest、VALIDATION；只上传这四个文件，不上传测试包、私人配置、密钥或截图。Tag及提交由发布清单绑定，实际发布与下载核验回执见GITHUB_PUBLICATION.md。旧Debug迁移和新版真机保持独立边界，不卸载或清数据绕过签名。

本次到统一GitHub Release发布及回读校验后停止，不自动选择新任务或修改NAS/Hub/Agent/Lucky/路由/桌面客户端。
