# Ubuntu 游戏服务

唯一游戏镜像基于 Ubuntu 24.04，支持 ALL、CORE、GAME、P2P 四种角色，必须连接外置数据库。

| 目录 | 用途 |
|---|---|
| `docker/ubuntu` | Dockerfile、必要兼容源码和许可证 |
| `assets` | 游戏程序、模板、初始化 SQL、手动 PVF 测试包 |
| `runtime/ubuntu` | Ubuntu 入口、校验、健康检查和启动完成提示 |

在仓库根目录构建：

```bash
docker build -f services/game/docker/ubuntu/Dockerfile -t dnf-game:ubuntu24.04 services/game
```

启动脚本已直接保存为 Ubuntu 版本，不再依靠构建时剪裁旧平台脚本。
部署见 [整体](../../deploy/ubuntu/all/README.md) / [拆分](../../deploy/ubuntu/split/README.md)，依赖见 [兼容说明](../../docs/ubuntu/compatibility.md)。
`assets` 是构建输入，不是线上运行目录。实际 PVF、公钥、配置和用户 hook 保存在部署的 `runtime` 中。

## 内置 QK 配置

`assets/root/` 中的三个配置直接打入镜像的 `/root/`，无需额外挂载：

| 文件 | 用途 |
|---|---|
| `qk_plugin_antonRaid.ini` | 安徒恩团本配置 |
| `qk_plugin_lukeRaid.ini` | 卢克团本配置 |
| `qk_plugin_config.ini` | 镶嵌、装备解锁等通用功能配置 |

这些文件保留提供时的配置值。部署前按实际账号和 PVF 修改 GM UID、NPC、地图及奖励道具等参数。
晴空 2.8 的 `qk_plugin.so` 使用硬编码绝对路径 `/root/qk_plugin_*.ini`。
这里是**容器内部的 `/root`**，与宿主机项目放在哪里、游戏进程的工作目录无关。
只放 INI 或只安装 DP2 都不会实现晴空团本。

文件路径：

```text
源码 assets/root/qk_plugin_*.ini
  → 容器 /root/qk_plugin_*.ini
```

有配套晴空服务端插件时，可将 `qk_plugin.so` 放入构建输入 `assets/root/`。
启动器检测到容器 `/root/qk_plugin.so` 后，仅为 `df_game_r` 追加加载；
与 DP2 同时存在时按 DP2、QK 的顺序追加，并保留原兼容库和 hook。
插件必须为匹配该游戏版本的 32 位 Linux ELF。
晴空总包还包含 `QK` 网关、配套库及后台配置流程；插件加载、INI 读取和实际团本功能须分别验收。
不要把客户端的 `qingkong.dll` 当成 Linux 服务端插件。

### 已验证的晴空 2.8 接入

本项目可选启动流程只接受此次提供的配套替换版，避免给其他版本套用固定地址适配。
本地构建输入如下；二进制不进入 Git，但留在服务器源码目录，后续普通构建会继续包含：

| 本地构建输入 | 容器位置 |
|---|---|
| `assets/root/QK` | `/root/QK` |
| `assets/root/qk_plugin.so` | `/root/qk_plugin.so` |
| `assets/root/qingkong/` | `/root/qingkong/` |
| `assets/root/dungeon.lst` | `/root/dungeon.lst` |
| `assets/home/template/neople/game/libantisvrinline.so` | `/home/neople/game/libantisvrinline.so` |

支持的 SHA-256：

```text
QK            67553a3b45de80ebea5f767f6e3a51d577d2cbb6c6647bd4c97037cb50a41f9b
qk_plugin.so  33cd006a364d4c3e0b8738fb2333cf16771fb796443368be4eee7105e40aadfa
```

在已安装配套 DP2 的 ALL/GAME 节点上，启动时从现有环境生成
`/data/qk/config.ini`（权限 600），并链接为 `/root/config.ini`。
不需要额外挂载；数据库密码沿用 `DNF_DB_GAME_PASSWORD`，网关直接连接现有组数据库。
机器人和副本检查功能默认关闭。Supervisor 管理 QK 守护进程；
频道等待网关就绪，使网关先完成其游戏文件补丁。网关失败会影响健康检查。

网关还需要与游戏公钥配对的私钥，否则可能自动生成另一套密钥覆盖游戏公钥。
一体部署使用项目后台已有密钥，在 `deploy/ubuntu/all` 执行一次：

```bash
sudo install -d -m 700 runtime/game/data/qk
sudo openssl rsa -in runtime/admin/data/private.key -traditional \
  -out runtime/game/data/qk/privatekey.pem
sudo chmod 600 runtime/game/data/qk/privatekey.pem
sudo cp runtime/admin/data/publickey.pem runtime/game/data/publickey.pem
```

启动会验证密钥配对，复制到网关要求的游戏路径。私钥始终保留在服务器运行目录，
不进入 Git 或镜像。拆分部署仅将同一套密钥通过安全通道送到相应节点。

提供的插件使用旧数据库密码。`qk-db-adapter.js` 在现有 `df_game_r.js` 前只安装一次：
仅拦截调用方为 `qk_plugin.so`、账号为 `game`、目标库为 `qk` 或
`taiwan_cain_2nd` 的 `MySQL_open`，从进程环境读取现有密码并在内存中替换参数，
以及配套 `libantisvrinline.so` 使用 `game` 账号连接
`qk`、`taiwan_cain`、`taiwan_cain_2nd` 三个库的调用，
不输出或保存该密码。其他调用原样执行。版本哈希、i386 架构和目标函数机器码均受检查；
更换游戏或插件版本时必须重新验证，不能直接改掉检查值。

网关和插件会自行创建/升级表。此次服务器验证已生成 `qk.raid_anton`、
`qk.raid_luke`；仅有表或启动成功仍不能代替客户端建团、阶段切换和奖励测试。
8887 默认只在容器内部监听；若使用晴空后台远程管理，应按实际部署配置受限访问。

修改后，在仓库根目录重新构建镜像，再进入所使用的部署目录重建游戏容器。例如一体部署：

```bash
docker build -f services/game/docker/ubuntu/Dockerfile -t dnf-game:ubuntu24.04 services/game
cd deploy/ubuntu/all
docker compose up -d --force-recreate game
```

拆分部署在 `deploy/ubuntu/split/game` 中重建 `game` 服务。修改源码文件或只重启旧容器不会更新镜像中的配置。

## 可选 DP2 加载

有配套 DP2 时，将包内 `dp2/` 的内容放到已有运行目录 `runtime/game/data/dp/`，
使容器中存在 `/dp2/libdp2pre.so`、`/dp2/libdp2.so`、`/dp2/libdp2.xml` 及配套库和脚本。
启动器仅为 `df_game_r` 自动追加 `/dp2/libdp2pre.so`，保留原来的 `/dp2/libhook.so`；
其他服务不会自动加载 DP2。未安装这个文件时沿用原启动方式。

插件文件使用项目已有的 `/data` 持久化目录，无需新增挂载，也不随源码提交第三方插件、
运行日志或数据库密码。脚本所用的游戏版本、数据库地址和凭据须与当前部署匹配。
尤其不能直接使用插件包附带的旧数据库密码；修改服务器上的 Frida 配置，
Lua 中的数据库连接可使用进程已有的 `os.getenv("DNF_DB_GAME_PASSWORD")`。
插件脚本可能在首次启动时创建自己的数据库和表，接入前保留旧镜像及插件目录备份。

安装或更新后重建游戏容器，并检查游戏健康、实际加载模块和插件日志。
DP2 加载成功不代表它实现了上述 QK INI 的解析或团本功能，两者需要分别验证。
