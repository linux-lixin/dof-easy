# 原生服务端与网页联调文档

先看 [当前状态与剩余工作](status.md)。本目录描述 `LAUNCHER_MODE=native`；现有 `dof` 登录模式及 WPF 登录器继续保留，两种配套不能互换。

| 要做的事 | 入口 |
|---|---|
| 新装 86 级原生服务，选择内置／宝塔 MySQL | [完整部署步骤](../baota-deployment.md) |
| 查看已合并代码、部署版本、通过项和未完成项 | [当前状态](status.md) |
| 核对服务端证据和测试范围 | [服务端验收](server-runtime-validation.md)、[机器摘要](server-runtime-validation.json) |
| 导入固定免授权材料、生成正式资源包 | [资源导入](launcher.md) |
| 替换已存在的 86 级测试游戏实例 | [游戏部署与恢复](game-test-deployment.md) |
| 构建、准备、更新隔离网页后台 | [网页部署与恢复](deployment.txt) |
| 查看网页交付范围和测试方法 | [发布说明](publication.txt) |
| 操作 PVF、客户端包、参数应用与恢复 | [网页流程与接口](protocol.txt) |
| 对接新服务端协议 | [服务端协议契约](server-protocol-contract.md) |
| 检查装备广播触发条件和实现边界 | [装备广播](equipment-broadcast.md) |
| 核对网页测试与实际操作结果 | [网页验收摘要](web-integration-validation.json) |

项目尚未上线，不安排数据库迁移。原生服务端只接受锁定的免授权输入；保留第三方来源和版权信息。历史离线导入工具用于显式处理本地副本，不属于日常启动链。

`READY_FOR_TEST` 表示产物校验通过，`PERSISTED` 表示配置已保存并回读，容器 `healthy` 表示服务健康。三者均不能代替玩家实际登录和玩法验证。
