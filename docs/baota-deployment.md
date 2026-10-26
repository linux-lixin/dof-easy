# 宝塔同机部署：可选内置或外置 MySQL

适用于**重装后的新实例**：Linux x86_64 + 宝塔 Nginx + Docker Compose，86 级固定免授权原生服务与网页后台。选择在首次安装时保存，启动、重启、备份沿用该选择。本文不要求运行 Windows 后台管理工具，也不执行旧数据库迁移。

| 选择 | 安装参数 | 数据库由谁管理 | 项目镜像 |
|---|---|---|---|
| 内置（默认） | `--database internal` 或省略 | 独立 MySQL 5.6 容器与卷 | db、game、admin、netguard 四个 |
| 外置 | `--database external` | 同一台宿主机的宝塔 MySQL 5.6.x | game、admin、netguard 三个；无项目数据库容器 |

Nginx 始终可以由宝塔提供 HTTPS 入口。游戏和网页运行在 Docker；内置数据库不映射宿主机 3306，因此可以与宝塔 MySQL 共存。外置模式不会停止、重启或修改宝塔 MySQL 的配置文件，也不操作其他宝塔站点的库。

## 可选宝塔接入与菜单用法

一键端不要求宝塔，完整菜单和主机准备说明见 [容器一键端指南](container-installer.md)。在项目目录运行 `sudo bash install.sh`：可选等级，回车默认 86；可选内置 MySQL 或同机 MySQL 5.6，后者可以由宝塔安装。

使用宝塔反代时，在向导选择“网页仅本机访问”，把域名反代到 `http://127.0.0.1:18080` 并启用 HTTPS。独立反代片段输出为实例目录中的 `nginx-location.conf.example`，不会覆盖已有站点。

安装向导默认关闭识别到的 UFW/firewalld，访问限制由你配置的云安全组负责；也可选择保留。MySQL 监听 `0.0.0.0` 不会阻止安装或所选的关闭操作。脚本不清空 Docker 与游戏网络规则，也不验证或修改云安全组。外置模式自动分配容器网段，需使该网段能访问 MySQL 私网地址；脚本不接管宝塔数据库配置或其他站点。

下文是宝塔环境准备和高级命令说明，不需要把每条安装命令重复执行。

## 1. 重装前保留材料

系统重装会丢失系统盘文件。先保存自己的完整 PVF、配套客户端；若要保留账号、角色或已有密钥，还应另行保存实例目录和数据库备份。项目尚未上线、准备全新建服时，不需要迁移旧库。

本文命令在**重装完成后**执行。本次代码适配没有连接或修改现有服务器。新的部署方式与实际玩家进游戏是不同验收项，详见 [本次本地验证](validation/mysql-deployment.md) 和 [整体状态](dof/status.md)。

## 2. 安装宿主机依赖

建议选 Linux amd64 Ubuntu 24.04，安装宝塔后在软件商店安装 Nginx、Docker；选择外置才需要安装 **MySQL 5.6.x**。不要用 MariaDB、MySQL 8 或其他版本替代本轮已适配的外置服务。

宿主机需要 Git、Python 3.11+（原生资源工具要求）以及 Docker Compose v2。宝塔安装 Docker 后核对 Compose 插件是否齐全；缺少时按 Docker 官方对应发行版方式安装 Compose 插件。

```sh
uname -m
python3 --version
docker version
docker compose version
```

应分别为 `x86_64`、Python 3.11+、可工作的 Linux amd64 Docker Engine 和 Compose v2。两个频道建议至少 8 GB 内存，另为镜像构建、PVF 的原始／加密／下载副本和备份留足磁盘；至少保留 2 GiB 余量。

菜单安装最低允许标称 1 GiB（系统可见内存预留 10% 容差），不再以不足 4 GiB 拒绝本机构建。低于 4 GiB 默认建议补足总 Swap 至 8 GiB，并在构建前启用；这只是安装策略，1 GiB 主机完整构建及游戏性能尚未验证。

公网 IPv4 在安装向导中自动获取，回车采用，也可改成游戏盾／映射地址；检测失败时手动输入。外置 MySQL 的宿主机私网地址仍须按下文核对填写，不能用检测出的公网地址替代。

```sh
sudo mkdir -p /opt/dof-inputs
sudo chmod 700 /opt/dof-inputs
cd /opt
sudo git clone --depth 1 --branch main --single-branch https://github.com/linux-lixin/dof-easy.git
cd /opt/dof-easy
sudo git switch main
sudo git pull --ff-only
```

后续示例以能操作 Docker、读取私有材料的管理员身份执行。将已准备的 `Script.pvf` 放到 `/opt/dof-inputs/86/Script.pvf`。文中的公网地址、域名和输入路径都须换成自己的实际值。

## 3. 准备固定免授权原生资源

仓库已包含正式免授权资源及普通插件，原件由维护者本地保管。菜单自动选择内置资源，不再询问资源目录；下面高级安装命令省略 `--dof-assets` 即使用仓库的 `services/game/resources/native`。

无需从旧系统单独复制原生二进制。安装仍检查正式锁，不接受任意同名文件。需要复现派生时，见 [原生资源导入](dof/launcher.md)；第三方版权保留在项目说明。PVF 仍需自己提供。

## 4. 外置模式才做：准备宝塔 MySQL

### 确定地址、端口和容器网段

`--db-host` 是**容器可访问的宿主机私网 IPv4**，不能填容器里的 `127.0.0.1`。优先使用宿主机固定私网地址；也可以使用现有 `docker0` 地址（常见为 `172.17.0.1`，必须先核实）。

```sh
ip -4 addr
ip -4 route
docker network ls
```

为本实例选择一个与现有 Docker、宿主机、VPC/VPN 路由均不重叠的私网，例如 `172.29.86.0/24`。安装器会创建该 Docker 网段，并授权 `game@172.29.86.0/255.255.255.0`。宿主机数据库地址不能属于这个新建网段。

在宝塔 MySQL 配置中确认：

- 版本确为 5.6.x，使用 TCP 端口（示例 3306），未启用 `skip-networking`。
- `bind-address` 能接收上述宿主机私网地址的连接。若使用 `0.0.0.0`，必须配合来源限制。
- `max_allowed_packet` 至少 64M；旧游戏使用零日期，`sql_mode` 使用兼容设置，例如 `NO_ENGINE_SUBSTITUTION`。已有其他站点时先评估其依赖，不盲目替换整个配置文件。
- 宝塔防火墙及宿主机 INPUT 规则仅允许本实例 Docker 网段访问数据库端口。云安全组不要对公网开放 3306；网页用 HTTPS，数据库保持私网访问。

保存 MySQL 配置后由你在宝塔中应用。本项目不会改 MySQL 服务配置；UFW/firewalld 按安装向导选择关闭或保留，不清空 Docker 与游戏网络规则。

### 菜单安装只填写四项

运行 `bash install.sh` → 安装服务端 → 数据库选择 `2 同机 MySQL 5.6`：

| 输入 | 填写方式 |
|---|---|
| IP | 已核实的宿主机私网 IPv4，例如实际存在的 `172.17.0.1`；不能填监听通配地址 `0.0.0.0` |
| 端口 | 默认 `3306`，按实际配置修改 |
| 账号 | 默认 `root`，也可使用具备建库、创建用户及授权权限的管理员账号 |
| 密码 | 确认安装后隐藏输入上述账号的 MySQL 密码 |

无需手填客户端、socket 或数据目录，也无需自己建立密码文件。安装器优先检测宝塔客户端，再检测常规安装位置与 PATH；从客户端配置及常见位置发现 socket，核对监听地址、端口后连接，连接失败自动尝试 IP/端口 TCP。填写的管理员只用于首次初始化，运行时继续使用项目生成的受限账号。密码仅在安装期间写入 0600 临时文件，成功或失败均清除。

### 仅高级 CLI：提供管理员连接文件

使用上面的菜单可跳过本节与下节高级安装命令。仅直接运行 `dof.sh install ...` 时需要自行提供以下文件。

不在命令行、Git 或项目 `.env` 写宝塔 root 密码。创建私有文件并用编辑器填写：

```sh
sudo install -m 600 /dev/null /root/dof-mysql.cnf
sudo nano /root/dof-mysql.cnf
```

内容示例，`socket` 使用宝塔实际路径：

```ini
[client]
user=root
password="填写宝塔 MySQL 管理员密码"
socket=/tmp/mysql.sock
```

核对版本和实际身份，输出不要公开：

```sh
sudo /www/server/mysql/bin/mysql --defaults-file=/root/dof-mysql.cnf \
  -N -e 'SELECT VERSION(),@@server_uuid; SHOW DATABASES;'
```

首次初始化要求该管理员具有建库、建用户和授权权限。安装器只允许在**所有项目库尚不存在、没有已有 game 账号**时导入；同一 MySQL 内无关站点库可以保留。若发现已有项目库，安装会拒绝，绝不自动清库。

初始化会创建 18 个原游戏种子库，加上桥接所需 `d_channel_cain`、`dof_native`、`dnf_service`，共 21 个。同一 MySQL 服务只能绑定一个本项目实例，因为这些游戏库名固定。游戏与网页使用实例生成的 `game` 账号，授权只覆盖这 21 个库（库名中的 `_` 按字面匹配）；不授予全局权限、授权权限或其他站点访问权限。数据库 UUID 会写入私有实例绑定文件。

## 5. 二选一安装 86 级原生实例

下面使用 `--build` 从当前 main 构建，避免依赖尚未发布的示例仓库。请使用新版本镜像，旧镜像没有外置数据库辅助入口及原生网页启动适配。新游戏镜像还包含从固定摘要 MySQL 5.6 镜像提取、按 SHA-256 校验的备份客户端，避免较新 MariaDB 导出工具与 5.6 不兼容；这只增加构建阶段来源，不运行额外数据库服务。

**A. 内置 MySQL：**

```sh
cd /opt/dof-easy
bash dof.sh install --instance native86 --level 86 \
  --ip 你的服务器公网IPv4 --pvf /opt/dof-inputs/86/Script.pvf \
  --launcher native \
  --plugins dp2-frida --raid-extension off --channels '15,52' \
  --database internal --admin-bind 127.0.0.1 --admin-port 18080 --build
```

**B. 同机宝塔 MySQL 5.6：**

```sh
cd /opt/dof-easy
sudo bash dof.sh install --instance native86 --level 86 \
  --ip 你的服务器公网IPv4 --pvf /opt/dof-inputs/86/Script.pvf \
  --launcher native \
  --plugins dp2-frida --raid-extension off --channels '15,52' \
  --database external --db-host 172.17.0.1 --db-port 3306 \
  --db-allow-host 172.29.86.0/255.255.255.0 \
  --mysql-config /root/dof-mysql.cnf --mysql-bin /www/server/mysql/bin/mysql \
  --admin-bind 127.0.0.1 --admin-port 18080 --build
```

上述两条只执行所选的一条。地址、网段经过第 4 步核实后再填；如宝塔端口不是 3306，同步填写 `--db-port`。示例默认关闭团本；需要时改为 `--raid-extension on`，且必须包含 DP2。完整团本玩法仍需客户端实际验收。

默认模式仍是 `dof`，默认数据库仍是 `internal`。选择外置数据库不意味着自动启用原生登录，故原生示例明确带 `--launcher native`。已有实例不能通过重跑安装改数据库模式、地址或授权网段；需要另一种模式时创建新的空实例，同时避免游戏端口冲突。

若在另一台构建机准备镜像，可运行 `bash dof.sh build --database external --registry 你的仓库/命名空间 --tag 新标签`，上传 game/admin/netguard 三个镜像，再把安装命令的 `--build` 换成真实 `--registry ... --tag ...`。内置模式还需要 db 镜像，见 [镜像配置](image-registry.md)。

## 6. 用宝塔 Nginx 接网页

安装器不占用宿主机 80/443，网页入口默认为 `127.0.0.1:18080`。在宝塔新建自己的域名站点，配置 HTTPS 和访问来源限制，将站点反向代理到 `http://127.0.0.1:18080`。

现成片段：[nginx-location.conf.example](../deploy/nginx/nginx-location.conf.example)。把它放入该站点已有 `server {}` 内，替换冲突的 `location /`；不要整个覆盖宝塔全局 Nginx 配置。它保留完整 URI，并放宽 PVF 上传大小和长任务超时、关闭上传临时缓冲。若修改后台端口，同步修改 `proxy_pass`。

使用宝塔的配置检查功能通过后重载 Nginx。外部访问 `https://你的域名/`，登录后进入 `/admin/dof`；API 为 `/api/v1/admin/dof`。账号 `admin`，初始密码仅在本机 `.dof-easy/instances/native86/.env` 的 `ADMIN_PASSWORD` 中查看，不贴到公开日志。

游戏端口由 Compose 单独映射，不能用 HTTP 反代代替。频道 15、52 的例子：TCP 8887、7001、7300、30015、30052；UDP 7001、7300、2311～2313、31015、31052。云安全组与宿主机规则按实际客户端来源放行，参见 [端口说明](ubuntu/ports.md)。不要把数据库或 Supervisor 管理口发布到公网。

## 7. 核对服务与网页

```sh
bash dof.sh status --instance native86
bash dof.sh logs --instance native86 --service game --follow
bash dof.sh logs --instance native86 --service admin --follow
curl --fail http://127.0.0.1:18080/api/v1/admin/assist/config-text
```

状态会显示所选数据库模式。外置模式的 `db-check` 核对版本、UUID 与业务表访问，游戏启动跳过旧 root 初始化和授权脚本，只使用实例账号；游戏健康检查同样验证数据库身份。宝塔 MySQL 日志由宝塔查看，`logs --service db` 只适用于内置模式。

原生程序继续使用 `/opt/dof-easy/`，原生配置在容器 `/data/dof-easy/config/`，原生库表为 `dof_native.config/config_v2`，管理表为 `dnf_service.DOF_*`。宿主机持久数据在 `.dof-easy/instances/native86/runtime/`。

网页验证顺序：登录 → 原生管理读取配置 → 保存并回读 → 检查审计 → 从绑定的当前 `Script.pvf` 导入并处理。两种数据库模式都建立原生实例／数据库绑定，保留冲突检测。客户端包生成仍需要与新密钥匹配的私有输入；缺少输入会明确拒绝，不拿旧实例材料回退。具体流程及尚未完成的 Windows 读取链见 [网页协议](dof/protocol.txt) 与 [当前状态](dof/status.md)。本部署指南不把网页生成成功算作玩家已进游戏。

## 8. 停启、更新与备份

```sh
bash dof.sh stop --instance native86
bash dof.sh start --instance native86
bash dof.sh restart --instance native86
bash dof.sh backup --instance native86
```

外置模式的这些操作不会停启宝塔 MySQL。备份短暂停止本实例游戏、网页和入口写入，按项目库范围导出（含 MyISAM 表锁），再恢复原先运行的服务；不导出 `mysql` 系统库或其他站点库。生成的 `.dof-easy/backups/*.tar.gz` 含实例密钥、密码和玩家数据，应私有保存。备份失败不发布成功归档。

备份面向项目内置表结构与数据，不承诺保存你另行添加的 MySQL 全局任务或自定义存储过程。外置归档不含宿主机 root 连接文件，也不包含 MySQL 系统用户；另行通过宝塔备份自己的 MySQL 配置、服务身份与账号授权。恢复应先停止项目写入，验证 SQL 与实例文件来自同一份备份，在保留 UUID 的同一数据库服务上恢复本实例数据和权限。若重建了 MySQL 导致 UUID 改变，现有实例会拒绝启动；不能直接编辑 UUID 冒充原服务，也不能通过重跑 `install` 覆盖已有库。本轮提供的是新建服入口，不自动迁移或接管旧实例。

镜像更新前先备份，使用当前代码构建新的明确标签后执行 `install --instance native86 --tag 新标签`（私有仓库按实际配置）；不再传 `--dof-assets` 或改变数据库参数。运行中的模式与密码不会因全局镜像配置改变。未上传的本地构建使用 `--build --registry= --tag 新标签`。

首次建库如中断，`external-database.json` 保留 `initializing` 状态，禁止自动重放包含 DROP 的初始 SQL。应保留日志并核对哪些库刚由该次空实例创建，决定修复或人工清理这次未成功的空实例；不要删除标记后盲目重试，也不要清理宝塔的无关业务库。不存在自动清理或系统重装操作。
