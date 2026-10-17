# 本地 Native 源码基线

2026-10-12 第二阶段，用户明确授权建立本地 Git。此前项目及相关父目录无 Git；本次保留当前源码快照，不重建项目，不伪造此前开发提交历史。

基线版本：0.2.51-dev / code60，applicationId dev.bing.tokenmonitor.nativeclient。带注释本地标签 `v0.2.51-dev` 指向基线提交；后续开发分支 `fix/antigravity-dual-windows`。提交 SHA 由本轮交接记录及 Git 标签查询，避免把提交本身 SHA 写入其内容形成循环。

## 安全纳入范围

源码/资源/所有测试与合成fixture、根构建文件、Gradle wrapper、工程文档、工具源码及既有.ui-craft规格。wrapper JAR 是必要构建启动文件；约2 MiB的固定周期JSON是必要测试输入，不是运行数据或归档。

提交前对候选文本进行了私钥、常见服务Token、JWT、URL内嵌凭据与长字面量赋值扫描，输出只记文件位置与类别。三个URL-userinfo命中均为测试占位：两个来自`public.example.invalid`虚构域名的连接显示fixture及其生成器，一个来自地址校验单元测试；不含真实登录数据。未发现需排除的真实密钥/凭据文件或数据库。扫描无法给出绝对无秘密保证，新增文件仍须重复安全检查。

不纳入`.gradle`、`.kotlin`、各build、local.properties、IDE状态、dist、work、evidence、APK/归档/日志、数据库、签名私钥、.env和凭据文件。原文件均留在磁盘，没有清理历史APK、失败日志或缓存。本地证据仍按相对路径可用，Git checkout本身不包含历史大型证据目录。

基线清单：`docs/baseline-0.2.51-files.sha256`列出实际纳入文件的SHA256，清单自身除外；生成后检查staged路径无上述排除类型。基线不证明旧APK完整字节可复现，仍继承接管审计的构建边界。

不创建remote，不push，不修改旧GitHub Android仓库。该基线仅为当前Native项目的本地版本管理起点。
