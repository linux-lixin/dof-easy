# 晴空实例管理

入口 `sudo bash dof.sh`；[manage.py](manage.py)和[menu.py](menu.py)共用晴空、外置数据库的安装流程。默认 86 级、原版晴空、MySQL 5.6、普通插件 none、派生团本 off。

安装前检查原包和配置、数据库版本及现有身份。仅构建 game 镜像，密钥初始化与 db-tools 复用同一镜像。已有 dof/native/internal 实例被拒绝，不静默迁移。

使用方法见[CLI](../../docs/reference/cli.md)和[部署](../../docs/operations/qingkong-baota.md)。
