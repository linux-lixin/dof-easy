# Ubuntu 跨主机拆分部署

每个角色有独立 `compose.yaml`、`.env.example` 和 `runtime/`。先按 [构建说明](../build/README.md)在对应主机构建。


每台主机均 Git 拉取并构建 Ubuntu 镜像，使用各自新的运行目录。三台主机共享同一个外部测试数据库实例。
后台可跟随 CORE，也可使用 [独立后台目录](admin/README.md)。两种方式只选一个。

拆分时 CORE 的 `GAME_DB_ALLOW_IP` **必须填写**覆盖 CORE 和所有 GAME 节点数据库连接来源的地址模式，
例如可信内网 `10.2.4.%`。以 MySQL 实际看到的来源 IP 为准，容器跨主机连接可能经过宿主机 NAT。
留空自动识别只适用于 ALL；拆分模板会拒绝留空，GAME 也会使用 `game` 账号检查实际权限后才启动。
若节点不在一个可安全授权的网段内，应由数据库管理员逐个建立对应的 `game@host` 授权，
CORE 中仍填写它自身匹配的授权地址；不要为了省事默认开放 `%`。

CORE 另需设置 `GAME_CHANNEL_HOSTS`，例如 `11=10.2.4.11,52=10.2.4.11`。
左侧为频道号，右侧为 CORE 能到达的 GAME 主机内网 IPv4；覆盖 CORE 的全部 `OPEN_CHANNEL`，不允许重复或遗漏。
频道位于不同主机时分别填写地址。该映射用于 statics/coserver 的服务器间 UDP 路由，不能填 CORE 自身地址。
GAME 主机必须放行并映射对应频道 UDP 端口（例：31011、31052），允许 CORE 访问。
ALL 自动把自己的频道映射到容器 loopback，无需填写此映射。

多个 GAME 主机必须分别配置自己的频道子集。`../shared/common.yaml` 的 `OPEN_CHANNEL` 当前固定为 `11,52`，
只在 `.env` 添加 `OPEN_CHANNEL` 不会改变它；在每台 GAME 主机的现有 `game/compose.yaml` 中，
向 `services.game.environment` 添加 `OPEN_CHANNEL` 覆盖继承值，并编辑该服务的 `ports` 列表。
例如在 `10.2.4.11` 主机上保留其他已有字段，将相应部分设为：

```yaml
services:
  game:
    environment:
      OPEN_CHANNEL: "11"
    ports:
      - "30011:30011/tcp"
      - "31011:31011/udp"
```

这是现有服务的局部编辑示例，不是完整 Compose 文件。另一台 `10.2.4.12` 主机设置 `OPEN_CHANNEL: "52"`，
其端口列表只保留 TCP 30052 和 UDP 31052。CORE 保留全部频道 `OPEN_CHANNEL: "11,52"`，
并在 CORE 的 `.env` 中设置 `GAME_CHANNEL_HOSTS=11=10.2.4.11,52=10.2.4.12`。
每个频道只由一台 GAME 主机启动；新增频道时同时更新 CORE 的频道全集、映射及对应 GAME 的频道子集/端口。
编辑后在各主机运行相应的 `docker compose -f ... config --quiet` 和 `up -d` 使配置生效。

公网通告地址可与容器网卡不同。Ubuntu 为 auction/point 使用 `0.0.0.0` 监听，
manager/DBMW 的本机 CORE 路由使用 loopback；玩家通告仍使用原 PUBLIC_IP，协议和端口不变。

| 主机 | 启动文件 | 本机 PUBLIC_IP | 其他地址 |
|---|---|---|---|
| CORE | `core/compose.yaml` | 玩家可达的 CORE 地址 | `NODE_BIND_IP` 为 CORE 主机真实内网 IP |
| P2P | `p2p/compose.yaml` | 玩家可达的 P2P 地址 | `CORE_IP`、`BRIDGE_IP` 为 CORE 内网 IP |
| GAME | `game/compose.yaml` | 玩家可达的 GAME 地址 | `CORE_IP`、`BRIDGE_IP` 为 CORE 内网 IP；`P2P_IP` 为玩家可达 P2P 地址 |

P2P 的 Compose 文件不读取或传入数据库凭据；只需 GAME_IMAGE、PUBLIC_IP、CORE_IP、BRIDGE_IP、P2P_RELAY_INDEX。
同一主机上的多个角色也必须使用不同的运行目录，不能把 CORE 与 GAME 的 `/data` 指向同一个目录。
每个角色只使用本角色目录下的 Compose，不要用多个 `-f` 将角色文件合并。默认项目名和运行目录各自独立。

先在 CORE 主机准备 `core/.env`（复制其 `.env.example` 并填写），然后执行：

```bash
cd /opt/dof/deploy/ubuntu/split/core
cp -n .env.example .env
chmod 600 .env
nano .env
docker compose config --quiet
docker compose up -d core
docker compose logs -f --tail 100 core
docker compose exec core python3 /opt/dnf/ubuntu/health.py
```

CORE 完成数据库初始化后，在 P2P 主机执行：

```bash
cd /opt/dof/deploy/ubuntu/split/p2p
cp -n .env.example .env
chmod 600 .env
nano .env
docker compose config --quiet
docker compose up -d p2p
docker compose exec p2p python3 /opt/dnf/ubuntu/health.py
```

等待注册完成，再在 GAME 主机执行：

```bash
cd /opt/dof/deploy/ubuntu/split/game
cp -n .env.example .env
chmod 600 .env
nano .env
mkdir -p runtime/game/data
tar -xzkf ../../../../services/game/assets/home/template/init/Script.tgz -C runtime/game/data Script.pvf
docker compose config --quiet
docker compose up -d game
docker compose exec game python3 /opt/dnf/ubuntu/health.py
```

最后回 CORE 主机启动后台并按[整体部署第 3 节](../all/README.md#3-启动后台初始化管理员配对密钥)初始化：

```bash
cd /opt/dof/deploy/ubuntu/split/core
docker compose up -d admin
```

从 CORE 后台取出公钥，安全复制到 GAME 主机的 `runtime/game/data/publickey.pem`，再重启 GAME。
CORE 和 P2P 互通使用内网实际 IP；Docker 服务名不能用于不同主机之间的连接。
网关注册内部消息保持 `ip,index,port` 格式，Ubuntu relay 适配层使用有超时的 TCP 收发及确认，避免旧 nc 的 EOF 等待差异。


GAME 的 PVF 也可以手动上传到 `split/game/runtime/game/data/Script.pvf`，与解压示例二选一。
CORE 数据位于 `split/core/runtime/core/data`；P2P 数据位于 `split/p2p/runtime/p2p/data`。
后台跟随 CORE 时，RSA 密钥位于 `split/core/runtime/admin/data`；独立后台时位于 `split/admin/runtime/admin/data`。
将后台公钥复制到每台 GAME 主机的 `split/game/runtime/game/data/publickey.pem`，然后重启 GAME；私钥仅留后台。
管理员 SQL 统一在 `deploy/shared/bootstrap-admin.sql.example`，CORE 目录可执行
`cp ../../../shared/bootstrap-admin.sql.example bootstrap-admin.sql`，按注释修改后通过 CORE 的 mysql 客户端执行。
使用整体部署第 3 节的 SQL 命令时，将 `docker compose exec -T game` 改为 `docker compose exec -T core`。
各节点健康、启动提示与故障排查见 [Ubuntu 运维说明](../README.md)。
