# 日常管理与配置目录

以下命令在仓库根目录执行，实例名按实际情况替换。

## 常用操作

| 操作 | 命令 |
|---|---|
| 查看实例状态 | `sudo bash dof.sh status --instance qingkong86` |
| 查看最近游戏日志 | `sudo bash dof.sh logs --instance qingkong86 --service game` |
| 持续查看日志 | `sudo bash dof.sh logs --instance qingkong86 --service game --follow` |
| 启动 | `sudo bash dof.sh start --instance qingkong86` |
| 停止 | `sudo bash dof.sh stop --instance qingkong86` |
| 重建容器并重启 | `sudo bash dof.sh restart --instance qingkong86` |
| 备份 | `sudo bash dof.sh backup --instance qingkong86` |

`restart` 会中断本实例连接；`backup` 也会短暂停止游戏服务。更新源码和镜像时按[升级流程](upgrade.md)操作。不要直接用根 Compose 启动，因为实例配置、数据库身份与端口由安装器生成。

## 隐藏目录保存什么

程序代码与运行数据分开保存。Linux 把以点开头的名称隐藏，`.dof-easy` 是实例数据目录，不是重复安装的程序或可以清除的缓存。

```text
仓库根目录/
├── dof.sh、scripts/、services/ ...    # Git 管理的程序代码
└── .dof-easy/                        # 不提交 Git 的本机运行数据
    ├── instances/qingkong86/
    │   ├── .env                     # 实例配置和内部数据库密码
    │   ├── instance.json            # 等级与资源身份
    │   ├── external-database.json   # 外置数据库绑定
    │   ├── deployment.json          # 安装器生成的容器配置
    │   ├── client/                  # 公钥与连接信息导出
    │   └── runtime/                 # 游戏数据、原包配置和密钥
    ├── backups/                     # 备份归档
    └── resources/                   # 默认等级包缓存，可指定其他位置
```

用 `ls -la` 或宝塔的“显示隐藏文件”查看。更新代码时保留整个 `.dof-easy`，不要复制其他实例的密钥或删除绑定文件来绕过校验。

## 配置从哪里改

| 配置 | 修改入口 |
|---|---|
| 公网 IP、监听地址、频道 | `dof.sh config`，参数见[命令参考](../reference/cli.md) |
| 晴空功能开关 | 实例下 `runtime/game/data/qingkong/config/qk_plugin_config.ini` |
| 晴空团本参数 | 同目录的 `qk_plugin_antonRaid.ini`、`qk_plugin_lukeRaid.ini` |
| 原版地下城配置 | 同目录的 `dungeon.lst` |
| 内部数据库密码 | 实例 `.env` 中的 `GAME_DB_PASSWORD`，修改要求见下文 |
| 镜像版本 | 通过[升级流程](upgrade.md)选择新标签 |

示例：

```bash
sudo bash dof.sh config --instance qingkong86 --channels 15,52
```

配置命令会保存回退副本并重新创建容器。手工修改原版插件配置后，也需要重启实例。网关 `config.ini` 中的数据库地址、密码和游戏路径由运行包装器管理，不作为独立的密码修改入口；`deployment.json` 同样是生成文件。

## 数据库密码与密钥

`.env` 的 `GAME_DB_PASSWORD` 是内部数据库密码来源，启动时注入 `DNF_DB_GAME_PASSWORD`、`DOF_DB_GAME_PASSWORD` 并同步到运行配置。当前旧游戏密码编码只处理前 8 位，因此内部密码必须是 **8 位字母或数字**；直接填写更长密码会被校验拒绝，不能通过删除校验来修复。

数据库中的内部 `game` 账号和实例配置必须一致。只改 `.env` 不会同步 MySQL 账号，也不会改变已经运行进程的环境。密码调整需要备份、同步数据库与实例配置、重新启动并验证连接；当前没有专用的一键密码轮换命令。

Windows 后台可以使用另一个来源范围的同名 `game` 账号及独立密码，两者不必统一，详见[数据库账号](external-mysql.md#数据库账号如何区分)。登录公钥和私钥是另一套身份材料，不能用数据库密码替代，也不要随意重新生成。

[返回文档目录](../README.md) · [备份与恢复](backup-restore.md) · [排错](troubleshooting.md)
