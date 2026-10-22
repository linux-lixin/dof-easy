# Ubuntu 24.04 整体部署（ALL + 后台）

本文默认是全新服务器首次部署：新建运行目录、初始化专用数据库、创建 `admin` 管理员。
本项目仅提供 Ubuntu 游戏镜像。
仅支持 amd64 和外部 MySQL；本轮验收基线为 MySQL 5.6.51。后台与登录器沿用现有版本。
实际测试范围和未验收项见 [Ubuntu 验收报告](../../../docs/ubuntu/validation.md)，
依赖来源见 [兼容库审计](../../../docs/ubuntu/compatibility.md)。

Docker 管理容器生命周期；本版保留 Supervisor 管理角色内部的多个旧服务进程。
日常用 `docker compose up/stop/logs`，排障或单独重启内部服务时才用容器内的 `supervisorctl`。
宿主机不需要安装 Supervisor。子进程退出由 Supervisor 恢复；健康检查失败本身不会触发 Docker 重启容器。
每个内部服务各用一个容器、移除 Supervisor 属于后续架构改造，不在本版范围。

## 1. 从 Git 获取源码，在服务器构建

以下单机流程以 root 用户、仓库路径 `/opt/dof` 为例，MySQL 由你自行部署。
执行顺序：构建两个镜像 → 填写环境变量 → 手动放置 PVF → 启动游戏并检查健康 → 启动后台 → 初始化管理员 → 同步公钥。
自建 MySQL 不代表游戏节点拆分；同一台服务器运行 game/admin 使用第 1–3 节即可。

准备已安装 Docker Engine/Compose 的 Linux amd64 服务器，以及独立管理的 MySQL 5.6 实例。
构建及 Compose 使用 Docker 默认平台，不再显式指定 `--platform` 或 `platform:`；以下流程用于 x86_64 服务器。
构建需要访问 Docker 镜像仓库、Ubuntu APT、Maven/npm 源；游戏资源与兼容依赖源码已在 Git 内。
不需要单独下载游戏资源、运行资源下载脚本或下载源码 ZIP。

```bash
cd /opt
git clone https://github.com/linux-lixin/dof.git
cd dof
docker build -f services/game/docker/ubuntu/Dockerfile -t dnf-game:ubuntu24.04 services/game
docker build -f services/admin/docker/Dockerfile -t dnf-admin:latest services/admin
cd deploy/ubuntu/all
cp .env.example .env
chmod 600 .env
nano .env
```

游戏镜像统一为 `dnf-game:ubuntu24.04`，后台为 `dnf-admin:latest`；两个标签与 `.env.example`、Compose 默认值一致。
`ubuntu24.04` 标识游戏基础系统，`latest` 表示本次源码构建的后台镜像；Compose 禁止自动拉取同名远端镜像。
以下全部 Compose 命令都在 `/opt/dof/deploy/ubuntu/all` 执行。默认数据写入该目录的 `runtime/`，全新部署不需要设置 `RUNTIME_ROOT`。

填写真实 `PUBLIC_IP`、`DB_HOST`、`DB_PORT` 和各密码。`203.0.113.*` 仅是文档示例。
`GAME_DB_PASSWORD` 恰好 8 位字母/数字，可用 `openssl rand -hex 4` 生成。
其余密码使用独立随机字母/数字值，例如 `openssl rand -hex 16`。
本套旧 SQL/配置模板不支持任意标点密码，入口会明确拒绝不符合要求的值，不会静默截断。
`DB_ROOT_PASSWORD` 填数据库现有 root 密码，不能随意另填一个随机值；`SUPERVISOR_PASSWORD` 设置独立值。
Ubuntu 使用 onlyGuo 后台登录流程，已移除旧统一登录器网关 `tongyi_gate` / `DnfGateServer`，无需 `GATE_PASSWORD` 或 `GM_*` 参数。
本目录的 `.env.example` 只包含整体部署参数；拆分节点地址在各自的 `split/` 目录配置。

数据库必须是专用测试实例，或专用测试副本；初始化会管理多个 schema 和 `game` 用户。
ALL/CORE 使用的数据库 root 需要全局权限及 GRANT OPTION。按实际容器出口 IP 授权，
不要把 `127.0.0.1` 当作容器可达的宿主机数据库地址。同机原生 MySQL 使用宿主机内网 IP。
MySQL 监听、防火墙、用户 host 授权必须允许所有游戏节点与后台节点连接。
不要让两套独立测试栈同时初始化同一个业务数据库。

`MYSQL_INIT_SSL_MODE=disabled` 为旧数据库 CLI 关闭 TLS；`auto` 保留 MariaDB 客户端默认行为。
后台 JDBC 的 `useSSL=false` 是独立选项。这里使用 MariaDB **客户端**连接外部 MySQL，镜像中没有数据库服务端。

在 `/opt/dof/deploy/ubuntu/all` 检查配置及数据库连接（输入数据库 root 密码）：

```bash
docker compose config --quiet
docker compose run --rm --no-deps --entrypoint bash game -c \
'exec mysql -h "$MAIN_MYSQL_HOST" -P "$MAIN_MYSQL_PORT" -uroot -p -e "SELECT VERSION(), USER(), CURRENT_USER();"'
```

连接成功不等于拥有初始化权限，仍需满足上面的 ALL/CORE 全局授权要求。此连接检查不会导入游戏数据。

## 2. ALL 单机启动

本流程使用 `deploy/ubuntu/all/runtime/` 作为全新运行目录。
ALL/GAME 首次启动前必须手动放置与客户端匹配的 `Script.pvf`，文件名大小写必须一致。
镜像不会自动解压 PVF；缺少文件、文件为空或误建成目录时，会在连接和修改数据库之前退出。
CORE/P2P 不需要该文件。启动过程不会覆盖你放置的 PVF。

**先测试仓库自带版本：直接使用下面的手动解压命令。** 仓库已经包含
`/opt/dof/services/game/assets/home/template/init/Script.tgz`，里面就是 `Script.pvf`，无需额外下载。
“手动放置”指启动脚本不会自动解压，并不要求你必须另找一个文件。

```bash
cd /opt/dof/deploy/ubuntu/all
mkdir -p runtime/game/data
tar -xzkf ../../../services/game/assets/home/template/init/Script.tgz \
  -C runtime/game/data Script.pvf
ls -lh runtime/game/data/Script.pvf
```

`-k` 防止覆盖已有文件；如果提示文件已存在，保留它并核对版本，不要直接删掉。
**如果使用自己的版本，则跳过解压命令**，通过 SFTP/宝塔上传到
`/opt/dof/deploy/ubuntu/all/runtime/game/data/Script.pvf`。两种方式选一种即可。
确认 `ls` 显示文件且大小不为 0；如果提示 `No such file or directory`，尚未准备好，不能继续启动。

默认挂载关系为 `./runtime/game/data:/data`，无需另外增加单文件挂载。公钥在后台首次启动后按第 3 节同步。
文件检查只能确认存在且非空，版本是否匹配仍需实际启动和客户端验证。

```bash
docker compose config --quiet
docker compose up -d game
docker compose logs -f --tail 100 game
```

`Ctrl+C` 只退出日志跟随。

当游戏容器所需进程、端口、数据库连接及角色对应的 P2P 检查通过，并且所有启用频道的本次“五国”日志齐全后，
连续三次检查成功会在 `docker compose logs -f game` 中打印一条明确提示：

```text
[启动完成] 游戏服务已全部就绪（ALL；频道：siroco11、siroco52）
```

每次容器启动只打印一次，不逐条转发“五国”，也不回放历史初始化日志。等待超过 `STARTUP_TIMEOUT`（默认 600 秒）
会打印 `[启动未完成]` 和具体失败原因。这表示游戏容器在该时刻就绪，后台仍按第 3 节单独启动；
后续运行状态看 Docker health。`startup_notice` 是一次性通知进程，成功后显示 EXITED 属于正常情况。

另一个终端验证：

```bash
docker compose ps
docker compose exec game python3 /opt/dnf/ubuntu/health.py
docker compose exec game bash -c 'grep -h "GeoIP Allow Country Code" /home/neople/game/log/siroco*/Log*.init'
```

健康检查包含真实子进程、角色端口、外部数据库和 P2P 注册；Supervisor 的 RUNNING 字样不足以判定完成。
`health.py` 成功时可能没有输出；紧接着执行 `echo $?` 应为 `0`，并确认 `docker compose ps` 中 game 为 healthy。
启动仍在进行时稍后再检查；若反复重启或持续 unhealthy，先看错误日志，不继续初始化后台。
每个依赖阶段最多等 600 秒，单次 TCP 探测最多 2 秒。错误信息会指出失败依赖。

## 3. 启动后台、初始化管理员、配对密钥

```bash
docker compose up -d admin
docker compose logs -f --tail 100 admin
```

后台首次启动会初始化 `dnf_service`；失败不写成功标记。已有库但没有标记时拒绝重放破坏性初始化。
后台正常启动后，按 `Ctrl+C` 退出日志查看。首次启动会自动生成 `/app/data/private.key` 和
`/app/data/publickey.pem`，宿主机对应 `runtime/admin/data/`。已有完整配对密钥会复用，不需手动重新生成。

后台管理员统一为 `admin`，没有固定默认密码。首次初始化前先查询，防止重复执行创建步骤：

```bash
docker compose exec -T game bash -c \
'MYSQL_PWD="$MAIN_MYSQL_ROOT_PASSWORD" exec mysql -h "$MAIN_MYSQL_HOST" -P "$MAIN_MYSQL_PORT" -uroot' <<'SQL'
SELECT UID, accountname, admin FROM d_taiwan.accounts
WHERE accountname = 'admin';
SQL
```

查询无记录后准备初始化 SQL；如果已存在 `admin`，说明创建步骤已经执行，不要重复插入：

```bash
cp ../../shared/bootstrap-admin.sql.example bootstrap-admin.sql
chmod 600 bootstrap-admin.sql
nano bootstrap-admin.sql
```

初始化账号为 `admin`，普通玩家未填写推荐人时也默认使用 `admin`。管理员密码由你填写，没有固定默认密码。
修改 SQL 中两个占位值：

- `CHANGE_ME_NEW_PRIVATE_ADMIN_PASSWORD`：后台 `admin` 的登录密码。
- `CHANGE_ME_DIFFERENT_PRIVATE_TEMP_SECRET`：另一个独立随机密串，用于临时授权，可用 `openssl rand -hex 16` 生成。

两者使用字母、数字，不能保留占位文本。不要提交编辑后的 SQL 或把密码粘贴到日志。
保存后通过游戏容器自带客户端执行，不要求宿主机额外安装 mysql 命令：

```bash
docker compose exec -T game bash -c \
'MYSQL_PWD="$MAIN_MYSQL_ROOT_PASSWORD" exec mysql -h "$MAIN_MYSQL_HOST" -P "$MAIN_MYSQL_PORT" -uroot' < bootstrap-admin.sql
```

确认执行成功后再 `rm bootstrap-admin.sql`。此账号用于后台管理及默认推荐关系，不是完整游戏账号。

后台生成自己的 RSA 密钥对后，将公钥同步给游戏端：

```bash
docker compose cp admin:/app/data/publickey.pem ./runtime/game/data/publickey.pem
docker compose restart game
```

重启后等待游戏重新变为 healthy，再执行 `docker compose exec game python3 /opt/dnf/ubuntu/health.py`。
后台 `/app/data/private.key` 保持私密。所有 GAME 节点都需要与后台私钥对应的公钥。
后台的 `private.key` 和游戏的 `publickey.pem` 必须配对。

浏览器打开 `http://服务器公网IP:18080/login`，使用 `admin` 和 SQL 中自行设置的管理员密码登录。
确保云安全组和主机防火墙允许 TCP 18080；游戏所需端口见 [端口与运维说明](../README.md#端口与地址)。
在辅助配置／内辅管理中设置“服务器地址”：ALL 填玩家可达的本机地址，
拆分部署填玩家可达的 CORE 频道入口地址。容器的 `PUBLIC_IP` 不会自动写入后台配置；
不修改时后台仍可能下发上游示例地址。保持本地 GM 关闭，等级等配置与客户端/PVF 一致，首轮更新列表保持为空。
在后台所在主机核验实际下发内容：

```bash
curl --fail http://127.0.0.1:18080/api/v1/admin/assist/config-text
```

确认返回的“服务器地址”为自己的玩家入口，且玩家可访问后台 API 和[角色端口表](../../../docs/ubuntu/ports.md)列出的游戏端口。
完整的后台配置、客户端包、登录器配置和更新发布步骤见 [客户端部署文档](../../../docs/launcher/deployment.md#1-开放端口并配置后台)。
本轮没有验证真实 Windows 客户端注册、登录、进副本、组队与自动更新下载。


拆分部署见 [CORE / GAME / P2P](../split/README.md)；端口、运行目录和测试见 [Ubuntu 目录说明](../README.md)。
后台使用 `restart: "no"`，服务器重启后等待游戏健康，再执行 `docker compose up -d admin`。
