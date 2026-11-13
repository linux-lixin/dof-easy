# 外置数据库初始化种子

本目录保留游戏所需 [seed.sql](seed.sql)、[compat-tables.sql](compat-tables.sql) 和 [generate_compat.py](generate_compat.py)。安装器使用[数据库模块](../../scripts/container/database.py)预检和初始化外置 MySQL 5.6，另由晴空模块建立 qk 数据库。

这些 SQL 用于新实例，不是覆盖旧玩家库的升级脚本。本分支不提供数据库 Dockerfile 或内置数据库配置。变更 SQL 后须在隔离 MySQL 5.6 验证。参见[数据库指南](../../docs/operations/external-mysql.md)。
