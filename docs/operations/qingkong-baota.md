# 安装晴空服务端

这篇教程用于新建实例。已经运行的晴空实例请阅读[升级与回退](upgrade.md)。所有示例命令均在仓库根目录执行。

## 1. 确认环境

- Linux x86_64 主机。已完成实机验证的组合是 Ubuntu 24.04、Docker/Compose 和宝塔 MySQL 5.6.50。
- Python 3.11+、Git、可用的本机 Docker Engine 与 Compose；安装菜单可以检查和补齐受支持主机的依赖。
- 宝塔已有 **MySQL 5.6.x**。5.7/8.0 和 MariaDB 不作为等价版本接受；本项目不会安装数据库容器或替换宝塔数据库。
- 准备公网 IP、Docker 能访问的宿主机私网 IP，并检查磁盘空间。源码构建、PVF、备份和 Swap 都需要空间，以安装菜单检查结果为准。

主机准备代码接受 Ubuntu 24.04/26.04、Debian 12/13；这不代表每种系统和所有客户端组合都已通过完整游戏验收，详见[验证状态](../status/README.md)。

## 2. 准备配套文件

准备同一套晴空 2.8 材料、匹配的 PVF 和完整客户端。当前输入清单固定了已核验原包与配套替换文件的哈希，其他版本不会自动兼容。仓库不包含原包、Windows 后台、PVF 或完整客户端。

服务器上的原版输入目录示例：

```text
/opt/dof-inputs/qingkong-original/
├── QK
├── qk_plugin.so
├── libantisvrinline.so
├── qingkong/                 # 输入清单规定的 9 个依赖库
└── config/
    ├── config.ini
    ├── qk_plugin_config.ini
    ├── qk_plugin_antonRaid.ini
    ├── qk_plugin_lukeRaid.ini
    └── dungeon.lst
```

共 12 个二进制输入和 5 个配置文件；名称与内容以[晴空输入清单](../../services/game/runtime/ubuntu/qingkong/inputs.lock.json)为准。配置文件取自同一配套原包，不修改哈希绕过校验。PVF 单独存放，例如 `/opt/dof-inputs/86/Script.pvf`。

按[数据库指南](external-mysql.md#首次安装的管理员连接)准备本地 MySQL 管理员客户端配置。管理员凭据只用于首次预检、建库与账号授权，不放进游戏容器。

## 3. 获取代码并安装

```bash
git clone --branch main https://github.com/linux-lixin/dof-easy.git
cd dof-easy
sudo bash dof.sh
```

菜单中选择新建实例、86 级、晴空原版和源码构建，填写公网 IP、PVF、晴空目录及宝塔数据库连接。main 不提供旧登录器或内置数据库模式。

熟悉命令行时，也可使用下面的完整示例。先替换 IP 和文件路径；`203.0.113.10` 是示例公网地址，`10.0.0.10` 是示例宿主机私网地址。

```bash
sudo bash dof.sh install --instance qingkong86 \
  --launcher qingkong --level 86 --plugins none --raid-extension off \
  --channels 15,52 --ip 203.0.113.10 --bind 0.0.0.0 \
  --pvf /opt/dof-inputs/86/Script.pvf \
  --qingkong-assets /opt/dof-inputs/qingkong-original \
  --database external --db-host 10.0.0.10 --db-port 3306 \
  --db-allow-host 172.29.86.0/255.255.255.0 \
  --mysql-config /root/mysql-admin.cnf \
  --mysql-bin /www/server/mysql/bin/mysql \
  --build --registry '' --tag qingkong-local
```

使用 `--build` 从本次源码构建 game 镜像。密钥初始化和数据库工具复用该镜像，只有游戏服务长期运行。不要假设旧的发布镜像标签已包含 main 的修改；镜像获取方式见[命令参考](../reference/cli.md)。

新建实例会生成游戏库、`qk` 库、实例密钥和受 Docker 网段限制的 `game` 账号。已有项目库或账号冲突时会退出，不会覆盖数据。新实例的 `--qingkong-assets` 用于首次导入，后续升级不重复导入原包。

`--plugins none` 和 `--raid-extension off` 关闭的是旧的额外玩法模式。晴空原版插件及团本仍由原包配置控制。

## 4. 检查服务端

```bash
sudo bash dof.sh status --instance qingkong86
sudo bash dof.sh logs --instance qingkong86 --service game
```

游戏进程需要加载 PVF 和频道数据，启动不会立即完成。安装器会等待健康检查；一次性密钥容器正常显示 `Exited (0)`，长期运行的 game 容器应为 `healthy`。

按[端口表](../reference/ports.md)配置云安全组和宿主机防火墙。默认网关为 TCP 8887，频道为 15、52；3306 是数据库端口，不是玩家入口。

## 5. 连接后台和客户端

安装成功后，实例 `client/` 目录会导出 `publickey.pem` 和 `qingkong-endpoints.json`。后者只是连接信息，不是可以直接导入晴空登录器的配置包。

接着阅读[Windows 后台与登录器](windows-tools.md)，使用匹配客户端完成登录、选角、进城、进图和重登。服务端健康不能替代客户端实测。

日常启停、配置目录与密码位置见[日常管理](daily-management.md)。
