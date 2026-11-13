# 晴空原版配套与宝塔 MySQL

适用：Linux x86_64、新建 86 级测试实例、宝塔已有 MySQL 5.6.x。使用原版晴空登录器、网关、插件和 Windows 后台；不启动本项目 Java/Vue 后台。菜单和命令行均默认此模式，不再支持旧登录器或内置数据库。

事实来源：[安装器](../../scripts/container/manage.py)、[数据库初始化](../../scripts/container/database.py)、[原版运行包装器](../../services/game/runtime/ubuntu/qingkong/service.py)、[输入清单](../../services/game/runtime/ubuntu/qingkong/inputs.lock.json)。

## 准备材料

自行准备同一套晴空 2.8 配套材料及匹配的 PVF/完整客户端。当前固定输入是已核验的原版依赖和配套替换包，哈希由本分支的晴空输入清单固定；其他版本不会静默接受。仓库不包含原包、Windows 工具、PVF、实例密钥或玩家数据。

在服务器私有目录按以下结构放置文件：

```text
/opt/dof-inputs/qingkong-original/
  QK
  qk_plugin.so
  libantisvrinline.so
  qingkong/                 # 输入清单中的 9 个原版依赖库
  config/
    config.ini
    qk_plugin_config.ini
    qk_plugin_antonRaid.ini
    qk_plugin_lukeRaid.ini
    dungeon.lst
```

前三个配置文件及团本配置、地下城列表均取自匹配原包；按清单校验原版二进制，不对其改名或改协议。运行时单独补充固定基础镜像中的旧版 libnsl，32 位和 64 位依赖分开加载。

宝塔 MySQL 应可从新建 Docker 网段访问。管理员凭据只用于首次建库，在宿主机准备权限为 `0600` 的 MySQL 客户端配置；参见[外置 MySQL](external-mysql.md)。不要把管理员凭据写入玩家登录器。

## 新建实例

直接执行 `sudo bash dof.sh` 可进入安装菜单，等级默认为 86。菜单将提示宝塔连接信息；也可以在仓库根目录执行以下命令，替换示例 IP、输入路径和私网地址。这里显式列出默认模式参数，省略时仍使用晴空及外置数据库：

```bash
sudo bash dof.sh install --instance qingkong86 \
  --launcher qingkong --level 86 --plugins none --raid-extension off \
  --channels 15,52 --ip 203.0.113.10 --bind 0.0.0.0 \
  --pvf /private/Script.pvf --qingkong-assets /opt/dof-inputs/qingkong-original \
  --database external --db-host 10.0.0.10 --db-port 3306 \
  --db-allow-host 172.29.86.0/255.255.255.0 \
  --mysql-config /private/mysql-admin.cnf \
  --mysql-bin /www/server/mysql/bin/mysql --build --tag qingkong-local
```

`qingkong` 仅接受外置 MySQL 5.6，构建并运行游戏镜像，密钥初始化也使用该镜像。不会启动内置数据库或网页后台。游戏数据库和 `qk` 授权给专用网段的 `game` 账号；管理员密码不进入游戏容器。遇到已有项目库或 `game` 账号时拒绝覆盖，清理或迁移需另行处理。现有实例不支持直接切换登录协议。

晴空原版团本由原配置控制，保持 `--raid-extension off`；本分支保留该显式参数以兼容既有晴空安装命令，值只能为 off。本分支只接受 `--plugins none`，已移除普通 DP2/Frida 玩法，但保留晴空数据库凭据适配所需的单个 Frida Gadget。原 PVF 原样复制，不做 native 模式名称转换。

## 运维与客户端

```bash
sudo bash dof.sh status --instance qingkong86
sudo bash dof.sh logs --instance qingkong86 --service game
sudo bash dof.sh restart --instance qingkong86
sudo bash dof.sh backup --instance qingkong86
```

实例配置位于 `.dof-easy/instances/qingkong86/runtime/game/data/qingkong/config/`。网关数据库地址和密码由实例管理，修改插件或团本配置后重启。保留实例密钥；原网关初始化后会恢复并核对同一套密钥。

晴空网关为 TCP 8887；示例频道需要 TCP 7001、7300、30015、30052，UDP 7001、7300、2311–2313、31015、31052。云安全组按实际频道放行；玩家不需要数据库 3306。启动成功后，实例 `client/` 导出 `publickey.pem` 和 `qingkong-endpoints.json`，后者只是端口信息，并非原版登录器配置容器。

在 Windows 中使用匹配的原版晴空后台制作和配置登录器，配合完整游戏客户端及同一 PVF。不要混用仓库派生的 `dof-launcher.exe` 或其格式 2 配置。

## 验收边界

2026-11-12 的一次私有 Ubuntu 24.04 / 宝塔 MySQL 5.6.50 / 86 级实例验证了：镜像构建、原版组件哈希、网关初始化、两个频道加载、受限数据库凭据适配、运行密钥一致和五个公网 TCP 入口。实例地址、账号、密钥和原始日志保留私有。

Windows 后台操作、普通账号登录、选角、进城、进图、返城、重登和公网 UDP 游戏流量尚未验收。这些结果只适用于本次配套，不能推广到所有等级，也不代表完整客户端游戏流程已经通过。
