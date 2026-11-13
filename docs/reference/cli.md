# 命令与默认值

仓库根目录执行 `sudo bash dof.sh` 进入菜单。`bash dof.sh --help` 与子命令 `--help` 是完整参数来源。

| 设置 | 参数 | 默认值 |
|---|---|---|
| 等级 | --level | 86 |
| 频道 | --channels | 15,52 |
| 游戏绑定 | --bind | 0.0.0.0 |
| 登录器 | --launcher | qingkong |
| 数据库 | --database | external |
| 插件 | --plugins | none |

`--raid-extension` 只能为 `off`；晴空原版团本使用原包配置。`--qingkong-assets` 指向原版资源目录，菜单默认 `/opt/dof-inputs/qingkong-original`。首次安装仍需提供匹配 PVF、公网 IP、外置数据库地址和初始化凭据，详见[部署](../operations/qingkong-baota.md)。

| 命令 | 用途 |
|---|---|
| install | 新建实例或沿用已有晴空实例启动 |
| build | 从源码构建 game 镜像，不上传 |
| images | 查看或配置镜像仓库、标签 |
| resources / versions | 下载等级资源 / 查看资源目录支持的等级 |
| start / stop / restart | 实例生命周期操作 |
| status / logs | 查看状态和日志 |
| config | 修改 IP、监听地址或频道等配置 |
| backup | 导出实例数据库和备份材料 |

`install --build` 显式源码构建；`install --offline` 只用本地资源和镜像，可用 `--images` 导入预先准备的镜像包。两者互斥。不带这两个参数时按配置获取发布镜像；本次清理未同时发布新镜像，应先用 `--build`。不创建 Java 后台、数据库镜像或旧登录器文件。
