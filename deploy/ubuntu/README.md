# Ubuntu 24.04 部署目录

Ubuntu 镜像仅连接自建外部 MySQL，使用 onlyGuo 后台；不含内置 MySQL、NetBird/Tailscale 或旧统一登录器网关。
本目录是导航入口，执行 Compose 时先进入具体部署目录。

| 目录 | 用途 |
|---|---|
| [build](build/README.md) | 从 Git 源码构建游戏与后台镜像 |
| [all](all/README.md) | 一台主机整体部署：game（ALL）+ admin |
| [split](split/README.md) | CORE → P2P → GAME → 后台，跨主机角色部署 |
| [split/core](split/core/README.md) | 核心服务，可选同机后台 |
| [split/game](split/game/README.md) | 游戏频道节点 |
| [split/p2p](split/p2p/README.md) | relay/STUN 节点 |
| [split/admin](split/admin/README.md) | 独立后台节点 |
| shared | 共享游戏 Compose 定义，不直接启动 |

每个实际部署目录都自带 `.env.example`。把它复制为该目录的 `.env`，填写后在同一目录运行
`docker compose config --quiet` 和 `docker compose up`。CORE/GAME/P2P 复用同一游戏镜像，后台使用独立镜像。

默认按全新部署使用各角色独立的 `runtime/`，初始化管理员统一为 `admin`。游戏镜像为 `dnf-game:ubuntu24.04`，后台为 `dnf-admin:latest`，均在服务器从源码构建。

## 端口与地址


以下对应示例大区 3、频道 11 和 52，值来源于仓库内各服务 cfg 模板及 relay/网关配置。
改大区/频道后要同时调整端口映射，不能只改 `OPEN_CHANNEL`。
在仓库根目录可运行 `python3 scripts/diagnostics/ubuntu_ports.py --group 3 --channels 11,52` 重算；
若仍位于本指南的 `deploy/ubuntu` 目录，运行 `python3 ../../scripts/diagnostics/ubuntu_ports.py --group 3 --channels 11,52`。
当前生成结果见 [角色端口表](../../docs/ubuntu/ports.md)。

| 角色 | 用途 | 协议/端口 | 来源 |
|---|---|---|---|
| CORE | 玩家频道列表 | TCP/UDP 7001 | `channel/cfg/server.cfg` |
| GAME | 玩家频道 | TCP 30011、30052；UDP 31011、31052 | `game/cfg/server.template` |
| P2P | 玩家中继 | TCP/UDP 7300 | `relay/cfg/relay.cfg` |
| P2P | STUN | UDP 2311–2313 | `stun/cfg/server.cfg` |
| CORE | 节点间 bridge | TCP/UDP 7000 | `bridge/cfg/server.cfg` |
| CORE | relay 注册 | TCP 5003 | `ubuntu/relay.py`，公式 500 + 大区 |
| CORE | monitor/guild | TCP/UDP 33303、33403 | `monitor`、`guild` cfg |
| CORE | statistic/doublecheck | UDP 33503、33703 | `statics`、`coserver` cfg |
| CORE | 拍卖/点券拍卖 | TCP/UDP 33803、33603 | `auction`、`point` cfg |
| CORE | community | TCP 33100 | `community/cfg/server.cfg` |
| CORE | 内部 dbmw/manager | 20203、20303、20403、40403 | 相应 cfg；不映射到宿主机 |
| CORE/GAME/ALL | 本地数据库代理 | TCP 127.0.0.1:3306、3307 | mysql_proxy；不映射到宿主机 |
| 全部 | Supervisor 管理 | 容器内 127.0.0.1:180 / Unix socket | supervisord；不映射到宿主机 |
| 后台 | HTTP/API | TCP 18080 → 80 | Compose |

节点间端口绑定 `NODE_BIND_IP`，仅允许可信 GAME/P2P 节点访问；玩家端口按需要放行。
容器不使用 TUN、NET_ADMIN、privileged；非空 `NB_SETUP_KEY`/`TS_AUTH_KEY` 会被拒绝。
默认 Compose 模板要求显式 `PUBLIC_IP`，仅在 `.env` 留空或添加 AUTO/DDNS 参数不能启用地址发现。
若使用运行时的自动发现功能，须先编辑当前使用的 Compose 文件及其引用的 `shared/common.yaml`：
移除该节点所有 `${PUBLIC_IP:?…}` 强制非空引用，并将以下变量透传到对应服务的 `environment`：

```yaml
PUBLIC_IP: ${PUBLIC_IP:-}
AUTO_PUBLIC_IP: ${AUTO_PUBLIC_IP:-false}
DDNS_ENABLE: ${DDNS_ENABLE:-false}
DDNS_DOMAIN: ${DDNS_DOMAIN:-}
DDNS_INTERVAL: ${DDNS_INTERVAL:-10}
```

然后在 `.env` 留空 `PUBLIC_IP`，选择 `AUTO_PUBLIC_IP=true` 或 `DDNS_ENABLE=true`，重新解析 Compose 后用 `up -d` 重建服务。
ALL 模板的 `P2P_PUBLIC_IP` 当前也引用必填 `PUBLIC_IP`，启用发现时还须改为独立、明确的玩家可达 P2P IPv4。
`CORE_PUBLIC_IP`、`MAIN_BRIDGE_IP`、`P2P_PUBLIC_IP` 及 `GAME_CHANNEL_HOSTS` 仍须按拓扑配置；
本机 PUBLIC_IP 的发现不会自动更新其他节点的这些地址。
显式 IPv4 始终优先；AUTO 模式获取一次地址，DDNS 模式持续检查。
`DDNS_DOMAIN` 非空时解析 A 记录；为空时沿用外网地址探测脚本。首次获取有时间上限，运行中变更会触发相关服务重启。

## 首次初始化与运行目录

全新部署使用所选目录下的 `runtime/`。ALL/GAME 首次启动前只需手动准备配套 `Script.pvf`，
后台首次启动自动创建 RSA 密钥对，再按[整体部署第 3 节](all/README.md#3-启动后台初始化管理员配对密钥)将公钥同步给游戏端。
不要提前创建受管 `run/` 脚本或未知配置；启动程序会生成本版运行文件及 `/data/.dnf-ubuntu.json` 状态标记。

正常停止、重启继续使用同一个运行目录，保留 PVF、配置、密钥和数据库初始化标记。
受管脚本有哈希校验，自定义修改冲突时会列出文件并停止，不会静默覆盖；平台标记不匹配时同样拒绝启动。
PVF、密钥和自定义 hook 不作为普通脚本覆盖。不要删除标记或清空数据来绕过启动错误。
后台使用 `restart: "no"`，服务器重启后等待游戏健康，再执行 `docker compose up -d admin`。

## 可重复验收

从仓库根目录执行，要求 Python 3.9+、Docker 和足够内存：

```bash
python3 tests/unit/test_ubuntu_runtime.py
python3 tests/integration/test_built_images.py --ubuntu --game-image dnf-game:ubuntu24.04 --mode all --state-file dist/ubuntu-all-state.json --stable-seconds 1800
python3 tests/integration/test_built_images.py --ubuntu --game-image dnf-game:ubuntu24.04 --mode split --state-file dist/ubuntu-split-state.json --stable-seconds 1800
```

每个测试使用独立名称、网络、卷和随机凭据，无公网端口、不访问现有服务器。
失败保留现场；证据在对应 state 文件旁的 `*-evidence/`。state 文件含临时测试凭据，不提交、不分享。
确认结果后只清理该次资源：

```bash
python3 tests/integration/test_built_images.py --ubuntu --state-file dist/ubuntu-all-state.json --cleanup
python3 tests/integration/test_built_images.py --ubuntu --state-file dist/ubuntu-split-state.json --cleanup
```
