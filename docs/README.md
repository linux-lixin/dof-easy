# 文档首页

按读者任务组织文档。先确认登录模式、实例来源与证据日期，再使用命令。本文档基于 `main@ecd4505` 的源码和既有证据重组，未重新部署或提升功能验收状态。

## 六条阅读路径

| 任务 | 阅读顺序 | 完成信号与边界 |
|---|---|---|
| 首次安装 | [环境](getting-started/prerequisites.md) → [模式](getting-started/login-modes.md) → [首次启动](getting-started/quickstart.md) → [安装详情](operations/install.md) | 容器健康、后台登录与实例绑定；游戏流程另验 |
| 已有实例升级 | [现状](status/deployment.md) → [升级](operations/upgrade.md) → [故障处理](operations/troubleshooting.md) | 镜像和保护数据核对，不把 start 当升级 |
| 后台日常管理 | [管理入口](operations/admin/README.md) → [记录](operations/admin/records.md) → [活动](operations/admin/activities.md) → [权限与协议](reference/admin-api.md) | 预览、保存、回读和回执；消费效果另验 |
| 备份恢复 | [备份恢复](operations/backup-restore.md) → [当前保留情况](status/deployment.md) | 归档校验、隔离恢复；管理备份不是全玩家库备份 |
| 客户端发布 | [兼容性](reference/clients/compatibility.md) → [原生发布](operations/native-client-publication.md)或[WPF](operations/wpf-client.md) → [验收边界](status/native-client.md) | 格式及批次匹配；完整进服仍待验 |
| 开发构建 | [贡献](../CONTRIBUTING.md) → [开发](development/README.md) → [后台打包](development/admin-packaging.md) → [测试](../tests/README.md) | 区分 compile、package、类型构建和条件跳过 |

## 分类索引

- [开始使用](getting-started/README.md)：环境、输入材料和第一次访问。
- [运行维护](operations/README.md)：安装、MySQL、镜像、管理、备份及发布。
- [参考](reference/README.md)：CLI、配置、端口、API 与原生格式。
- [架构](architecture/README.md)：组件、持久化、网络和执行归属。
- [开发](development/README.md)：构建、测试、派生规则和文档维护。
- [当前状态](status/README.md)：当前结论与证据。
- [验收索引](validation/README.md)：88 份原始 JSON 与分批记录。
- [历史](history/README.md)：已结束批次、旧方案、上游原文。

目录与迁移：[迁移清单](migration-inventory.md)、[机器目录](catalog.json)、[事实修正表](status/fact-corrections.md)。原路径只作直接迁移提示；历史截图仍是历史截图。
