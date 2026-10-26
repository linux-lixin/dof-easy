# 登录模式与登录器兼容

`LAUNCHER_MODE=dof` 是默认 WPF 登录链；`LAUNCHER_MODE=native` 使用固定免授权原生配套。两种协议不互通，不用默认 WPF 登录器替代原生 Windows 读取端。原生发布入口见 [文档索引](../dof/README.md)，完成情况见 [当前状态](../dof/status.md)。

默认登录器管理接口已包含全量及增量资源 SHA-256、数字点分版本比较和默认关闭的增量开关。网页显示当前模式；WPF 读取端仍按字符串排序补丁，未消费 SHA-256，不能将后台元数据改造写成客户端已完成。设置保存和下载元数据可用仍不等于端到端更新已验收。

原生网页已访问 `dof_native` 和 `DOF_` 管理表，PVF 上传、绑定导入和加密有实际记录。现有客户端生成器仍使用原配套格式，Windows 新名读取端尚待适配；生成、参数应用、原生读取和实际游戏分别验收。

## 历史默认登录器数据库工具

当前项目未上线，不要求数据库迁移。`scripts/operations/migrate_launcher_checksum.py` 保留用于明确需要导入的旧默认登录器数据库：先备份并停止指定隔离网页服务，再显式传入 `--instance`。它只为 `CLIENT_LAUNCHER_CONFIG`、`CLIENT_LAUNCHER_VERSION` 增加可空 `SHA256` 字段，保存独立备份，重复执行不重复添加。不要将 `services/admin/database/init.sql` 当作升级脚本，它包含重置语句。

更新元数据和下载地址使用可信加密通道；哈希验证完整性，不能单独证明发布者身份。原生配套不接受旧字段作为正式运行时回退。
