# CLI 与新建默认值

用途与读者：命令行操作者。适用范围：dof / native；具体差异见正文。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[manage.py](../../scripts/container/manage.py)；[menu.py](../../scripts/container/menu.py)；[raid_assets.py](../../services/game/runtime/ubuntu/gateway/assets.py)。限制和当前验收边界见[当前状态](../status/README.md)。


在仓库根目录通过 `bash dof.sh 子命令 --help` 查询参数。以下默认值只适用于新建实例；已有实例读自己的配置。

| 项 | 菜单 | 高级 install |
|---|---|---|
| 登录模式 | native | dof |
| 镜像来源 | build | pull |
| 网页绑定 | 0.0.0.0 | 127.0.0.1 |
| 网页端口 | 18080 | 18080 |
| 频道 | 15,52 | 15,52 |
| 游戏绑定 | 0.0.0.0 | 0.0.0.0 |
| 等级 | 86 | 86 |
| 数据库 | internal | internal |
| 插件 | dp2-frida | dp2-frida |
| 团本扩展 | off | off |

install 的 `--launcher`、`--database`、`--level`、`--ip`、`--pvf`、`--channels`、`--bind`、`--admin-bind`、`--admin-port` 定义见[参数源码](../../scripts/container/manage.py)。`--offline` 与 `--build` 互斥；默认拉取失败不回退构建。外置参数 `--db-host` 必须为容器可达私网 IPv4，端口默认 3306，初始化管理员文件权限 0600。

| 命令 | 作用和副作用 |
|---|---|
| images / build | 配置新建镜像默认值 / 显式本机构建，不上传 |
| install | 新建或更新实例，可能初始化及重建服务 |
| start / stop / restart | 使用实例镜像启停或重建，不拉取 |
| config | 修改实例配置并核对/重建 |
| status / logs | 查看运行状态与服务日志 |
| backup | 暂停写入服务、导出并归档后恢复原运行服务 |
| resources / versions | 缓存等级资源 / 列出支持等级 |
| plugin-db | 用明确提供的本机管理员配置准备插件数据库权限 |

`--instance` 默认 main，适用于实例命令。没有通用 restore 子命令。安装器频道 15,52 与[手动端口模板](ports.md)默认 11,52 的适用范围不同。成功与失败处理见对应[运维指南](../operations/README.md)。
