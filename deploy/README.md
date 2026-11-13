# 部署模板

这里存放安装器使用的部署材料。实际安装请阅读[安装教程](../docs/operations/qingkong-baota.md)，不直接运行根 Compose。

| 文件或目录 | 用途 |
|---|---|
| [compose.yaml](../compose.yaml) | game 与一次性密钥初始化服务的基础定义 |
| [bootstrap](bootstrap/README.md) | 初始化与核对实例密钥 |
| [database](database/README.md) | 新实例游戏库的兼容种子 |

[实例管理器](../scripts/container/README.md)结合 `.env`、端口和数据库身份，生成实例自己的容器配置及 `db-tools` 服务。基础模板不包含内置数据库或 Java/Vue 后台。

[开发维护](../docs/development/README.md)
