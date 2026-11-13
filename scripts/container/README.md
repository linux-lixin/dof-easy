# 实例管理脚本

用户通过 `dof.sh` 进入菜单或命令行，具体用法见[命令参考](../../docs/reference/cli.md)。

| 模块 | 职责 |
|---|---|
| [manage.py](manage.py)、[menu.py](menu.py) | 命令行和交互入口、实例生命周期 |
| [database.py](database.py)、[qingkong_database.py](qingkong_database.py) | 外置数据库身份、初始化、晴空基础结构 |
| [deployment.py](deployment.py) | 解析实例 Compose 与数据库工具配置 |
| [image_config.py](image_config.py) | 镜像仓库与标签选择 |
| [resources.py](resources.py) | 校验共享输入和等级资源 |
| [host.py](host.py)、[bootstrap.sh](bootstrap.sh) | 主机依赖与环境准备 |

入口共用晴空、外置 MySQL、86 级默认值。原包与实例身份校验在启动前执行；旧模式实例不会自动转换。运行数据保存于 `.dof-easy`，不与源码一起提交。

[开发维护](../../docs/development/README.md) · [部署模板](../../deploy/README.md)
