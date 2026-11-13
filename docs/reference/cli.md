# 命令参考

在仓库根目录运行 `sudo bash dof.sh` 进入菜单。完整参数以 `bash dof.sh --help` 和相应子命令的 `--help` 为准，例如：

```bash
bash dof.sh install --help
bash dof.sh config --help
```

## 新实例默认值

| 设置 | 参数 | 默认值 |
|---|---|---|
| 等级 | --level | 86 |
| 频道 | --channels | 15,52 |
| 游戏绑定 | --bind | 0.0.0.0 |
| 登录器 | --launcher | qingkong |
| 数据库 | --database | external |
| 插件 | --plugins | none |

`--raid-extension` 只能为 `off`，晴空原版团本由原包配置控制。菜单默认晴空输入目录为 `/opt/dof-inputs/qingkong-original`。默认值不代替首次安装必须提供的公网 IP、PVF、数据库连接和原版配套材料。

## 命令用途

| 命令 | 用途与边界 |
|---|---|
| `install` | 新建实例，或为已有晴空实例选择镜像并启动；已有实例不重新建库 |
| `build` | 从源码构建 game 镜像，不上传、不切换运行实例 |
| `images` | 查看或设置默认镜像仓库和标签，已有实例保留自己的设置 |
| `versions` | 查看资源目录中的可选等级，不代表各等级均已验证 |
| `resources` | 校验公共构建输入并下载全部等级包 |
| `start` / `stop` / `restart` | 启动、停止或重建并重启实例 |
| `status` / `logs` | 查看状态与日志，`logs --follow` 持续输出 |
| `config` | 修改公网 IP、监听地址、频道等已有实例配置 |
| `backup` | 短暂停服后导出项目库与实例归档 |

大多数实例操作通过 `--instance qingkong86` 指定实例；省略时默认实例名为 `main`，不是 Git 分支切换。

## 安装与镜像选项

| 选项 | 用途 |
|---|---|
| `--build` | 显式从源码构建，适合首次使用当前 main |
| `--offline` | 只使用本地已有镜像和所需等级资源，不联网补齐 |
| `--images 文件` | 配合 `--offline` 导入已有镜像包 |
| `--registry 仓库`、`--tag 标签` | 选择镜像来源和版本；本地构建/离线可用空仓库字符串 |
| `--qingkong-assets 目录` | 新实例首次导入晴空原包，不能覆盖已有实例 |
| `--mysql-config 文件` | 首次初始化使用的宿主机私有管理员客户端配置 |
| `--db-host`、`--db-port`、`--db-allow-host` | 外置数据库地址、端口及专用 Docker 网段授权 |

`--build` 与 `--offline` 互斥。不带这两个参数时尝试获取配置的发布镜像；不要假设旧发布镜像包含当前 main 的修改。`config` 没有数据库密码轮换参数，内部密码调整见[日常管理](../operations/daily-management.md#数据库密码与密钥)。

[安装示例](../operations/qingkong-baota.md) · [日常命令](../operations/daily-management.md) · [升级示例](../operations/upgrade.md) · [文档目录](../README.md)
