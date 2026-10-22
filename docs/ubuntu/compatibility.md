# Ubuntu 24.04 运行依赖

仅支持 x86_64 宿主机，游戏程序主要为 i386。Ubuntu 原生 glibc 2.39 负责加载，未替换系统 loader、系统库或全局 `LD_LIBRARY_PATH`。
基础镜像和兼容资源的来源、版本与哈希见 [compat-lock.json](../../services/game/docker/ubuntu/compat-lock.json)。APT 软件源仍会更新，基础镜像固定不等于全部包可逐位重建。

| 对象 | 运行方式 |
|---|---|
| i386 游戏程序和 hook | Ubuntu i386 libc6/libstdc++/libgcc/zlib |
| libnxencryption.so | 固定哈希的游戏库，位于 `/opt/dnf/compat/i386` |
| libGeoIP.so.1 | 固定 GeoIP 1.6.12 源码编译的 i386 库及历史国家数据库 |
| liblegacy-shm.so | 本仓库源码构建，仅兼容游戏使用的旧目录式共享内存名称 |
| amd64 密码工具、STUN | Ubuntu 原生系统库；不加载旧 amd64 兼容库 |
| 数据库代理和客户端 | Ubuntu socat、MariaDB 客户端，连接你自建的 MySQL |

已移除旧网关以及它专用的 OpenSSL 0.9.8、MySQL 5.0 `.15` 库；系统 OpenSSL 和 MariaDB 客户端仍是必要组件。Relay/STUN 用于游戏连接，仍保留。

## 构建检查

从仓库根目录执行：

```sh
docker build -f services/game/docker/ubuntu/Dockerfile -t dnf-game:ubuntu24.04 services/game
```

构建执行 ELF 重定位检查、GeoIP 数据加载和共享内存探针。当前清理后检查覆盖 27 个游戏/模板/工具 ELF 和 3 个私有兼容库，均无未解析依赖。
游戏资源原始哈希见 [资源锁](../../metadata/game-assets.lock.json)。构建镜像内 `/opt/dnf/audit/` 保存当次 ELF、兼容库和系统包清单。

`runtime/ubuntu/prepare_image.py` 现在只安装 Ubuntu 工具、准备受管脚本以及移除镜像内自动 PVF 资源，不再剪裁 CentOS/VPN/旧网关源码。
游戏启动脚本直接保存在仓库中，正常使用前台 Supervisor；地址探测和依赖等待仍有超时。

## 兼容和日志边界

`run-game` 只给 i386 子进程设置私有库路径，按架构校验 hook；Python、Supervisor 和数据库客户端使用系统库。
共享内存兼容层只处理 `sec/tss_sdk_bus_<数字>`，其余调用转发给系统实现。保留路径遍历、符号链接拒绝和 CLOEXEC 检查。

游戏原生标准输出可能含数据库口令，launcher 用独立 reader 过滤标准输出/错误，保留进程 PID、退出码和信号行为。它不能过滤游戏自行写入的日志文件。
`audit/test_run_game_redaction.py` 和 C 探针用于验证这些边界。

GeoIP 数据保留 MaxMind 来源与许可证说明；兼容库许可证随镜像保存在 `/opt/dnf/licenses/`。上游游戏资产未因此获得新的授权。

历史 `image-*`、`ubuntu-*`、`native-*` 审计快照已移至 [历史审计目录](../history/ubuntu-abi/README.md)，不参与构建，不能作为当前镜像清单。当前结果以镜像内生成报告和 [验收记录](validation.md) 为准。真实客户端进服不由 ELF 检查代替。
