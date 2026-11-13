# 数据库初始化种子

| 文件 | 用途 |
|---|---|
| [seed.sql](seed.sql) | 游戏库基础种子 |
| [compat-tables.sql](compat-tables.sql) | 兼容表定义 |
| [generate_compat.py](generate_compat.py) | 兼容定义生成工具 |

[数据库模块](../../scripts/container/database.py)负责外置 MySQL 5.6 的预检与初始化，晴空模块从固定原版网关提取 `qk` 基础结构。这里的 SQL 用于新实例，不是覆盖旧玩家库的升级脚本，也不包含数据库容器定义。

修改 SQL 后运行[隔离数据库验证](../../tests/integration/README.md)。实际连接、账号与身份说明见[数据库指南](../../docs/operations/external-mysql.md)。
