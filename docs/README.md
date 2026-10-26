# 文档索引

先按登录模式选入口。`dof` 是默认 WPF 登录链，`native` 是固定免授权输入派生的原生网关链；客户端、协议和部署步骤不能交叉套用。

| 需求 | 文档 |
|---|---|
| 同机宝塔 Nginx、Docker，选择内置／外置 MySQL | [完整部署步骤](baota-deployment.md) |
| 最新进度、部署版本与剩余工作 | [当前状态](dof/status.md) |
| 默认模式一键安装及日常管理 | [根 README](../README.md) |
| 原生模式、网页 PVF 与客户端联调 | [原生模式文档](dof/README.md) |
| 选择部署方式 | [部署导航](../deploy/README.md) |
| 镜像配置和资源分配 | [镜像仓库](image-registry.md)、[资源布局](resource-layout.md) |
| 网页后台开发与管理边界 | [后台文档](admin/README.md) |
| 默认 WPF 登录器 | [客户端配置](launcher/deployment.md)、[工程说明](launcher/README.md) |
| 手动 Ubuntu 单机／分角色部署 | [ALL](../deploy/manual/all/README.md)、[CORE / GAME / P2P](../deploy/manual/split/README.md) |
| Ubuntu 依赖与端口 | [兼容说明](ubuntu/compatibility.md)、[端口表](ubuntu/ports.md) |
| 源码和维护 | [项目结构](architecture.md)、[贡献说明](../CONTRIBUTING.md) |
| 测试、工具和来源 | [tests](../tests/README.md)、[scripts](../scripts/README.md)、[metadata](../metadata/README.md) |

## 如何阅读验收记录

[dof/status.md](dof/status.md) 统一说明当前结论；其链接的 JSON 保存分批测试及部署证据，不把跳过或未执行改成通过。端口、进程、网页任务完成和玩家实际进入游戏是不同的验收层次。

[通用容器验收](container-verification.md) 和 [早期 Ubuntu 验收](ubuntu/validation.md) 保留当时的测试范围和日期。`history/`、`admin/upstream/`、旧布局及清理审计用于来源追溯，不作为当前安装指令。原始品牌、版权、上游 URL 和历史镜像摘要保持真实，不按新名称改写。
