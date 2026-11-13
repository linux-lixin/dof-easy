# 测试入口

| 类型 | 覆盖范围 | 环境说明 |
|---|---|---|
| [单元测试](unit/README.md) | 安装、菜单、密码与身份校验、Ubuntu 兼容层 | Python；部分测试需要资源或 i386 工具链 |
| [结构测试](structure/README.md) | 共享哈希、Docker COPY 输入、部署结构 | 完整工作区及 Docker Compose |
| [隔离数据库测试](integration/README.md) | SQL 种子修复、历史引用保护和月表 | Docker 与指定 MySQL 5.6 测试镜像 |

统一命令见[开发维护](../docs/development/README.md#按改动选择检查)。缺少环境的检查记录为失败或跳过；测试不连接线上玩家数据库，也不能替代[Windows 和客户端验收](../docs/status/README.md)。
