# Ubuntu 单平台清理验收

本轮删除 CentOS 构建和部署、内置数据库初始化、NetBird/Tailscale、旧登录网关、未启用的 forward 代理及网关专用 OpenSSL 0.9.8/MySQL 5.0 兼容库。
游戏继续使用 socat 数据库代理，Relay/STUN 联机服务保留；MySQL 由用户自行部署。

已验证的 Ubuntu 初始化、运行脚本和 Supervisor 配置直接保存为源码，移除构建时从旧平台字符串剪裁的转换逻辑。游戏二进制、SQL 种子、PVF 和账号业务不因此改变。
历史文档和旧审计记录保留为历史证据，当前构建入口不引用已删除的平台或组网组件。

构建检查通过：27 个游戏/工具 ELF、3 个必要兼容库无未解析依赖；394 个保留资源的 SHA256 匹配。

| 验证 | 结果 |
|---|---|
| 离线回归 | 39 项通过 |
| 结构、资源、部署模板及文档链接 | 11 项通过，5 个 Compose 入口 |
| 32/64 位原生进程 | 2 项测试通过：输出脱敏、PID、退出状态、直接及进程组信号 |
| Shell 语法 | 38 个启动/初始化脚本通过 |
| 现有 Ubuntu 升级 | 21 个受管脚本哈希与之前镜像一致 |
| ALL 整体部署 | 初始化、后台 API、两频道本次五国、启动完成提示、频道和数据库故障恢复、三次重启通过 |
| CORE/GAME/P2P 拆分 | CORE 初始化、P2P 注册、后台、频道、故障恢复和三次重启通过 |
| 隔离资源清理 | 两轮容器、网络和卷均已按各自状态文件清理；未操作生产服务器 |

两轮稳定性采样均为 **30 秒**，不是重新执行历史 30 分钟长时验收。真实 Windows 客户端登录、进图、组队和自动更新不在本轮验收范围。

ALL 测试镜像与最终镜像仅有清理空目录的差别，576 个运行文件哈希一致；拆分验收直接使用最终镜像。
镜像 ID、启动时间和资源采样见 [ALL 记录](validation/ubuntu-only-all.json) 与 [拆分记录](validation/ubuntu-only-split.json)。
当前依赖证据：[游戏 ELF](validation/ubuntu-only-elf-report.json)、[兼容库](validation/ubuntu-only-compat-report.json)、[系统包](validation/ubuntu-only-packages.tsv)。
