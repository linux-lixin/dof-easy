# dof-easy · 86 级基础容器

基于 [linux-lixin/dof](https://github.com/linux-lixin/dof/tree/60d58715beaa870d0314b5c7fe050bd78cb3f258) 的全新 86 级部署：普通 DP2 + Frida、MySQL 5.6.51、dof 后台和 Windows 登录器。**未启用晴空/QK 或花枝专用插件。**

已移除或停用原安装器授权、上报、第三方备份和清日志流程；原安装器不进入启动链。运行时使用随机凭据，持久化数据库、插件脚本和日志。游戏网络通过独立 netguard 限制主动外联。

本仓库提交源码、配置模板、测试和资源摘要。**不包含 PVF、游戏/插件二进制、完整客户端、编译好的登录器、离线镜像、运行密码或私钥。** 既有离线交付包可直接导入镜像；从 Git 构建需先准备资源。

## 从源码构建

需要 Python 3.9+、Linux amd64 Docker 与 Compose v2。Windows 可使用 Docker Desktop 的 Linux 容器模式。

```sh
git clone https://github.com/linux-lixin/dof-easy.git
cd dof-easy
# 将本次资源归档解压到仓库同级 resource-archive 后执行：
python scripts/vipai86/prepare_source.py
# 没有归档时，可显式下载公开的固定版本输入：
# python scripts/vipai86/prepare_source.py --download
```

准备脚本逐项验证 SHA-256，只恢复白名单资源并生成经过修改的普通插件目录，不执行下载的程序。下载源不可用时可使用相同摘要的本地归档；也可用 `--resources /path/to/resource-archive` 指定位置。PVF 自行提供，不自动下载。

```sh
mkdir -p services/game/vipai86-pvf
cp /path/to/Script.pvf services/game/vipai86-pvf/Script.pvf
docker build -t vipai86-db:local deploy/vipai86/db
docker build -f services/admin/docker/Dockerfile -t vipai86-admin:local services/admin
docker build -f services/game/docker/ubuntu/Dockerfile -t vipai86-game:local services/game
docker build -t vipai86-netguard:local deploy/vipai86/netguard
cd deploy/vipai86
python configure.py --public-ip 你的服务器IPv4
docker compose up -d
```

游戏 Dockerfile 固定本次已验证 PVF 的 SHA-256 为 `2e65c17d075afec73203ba8f2bc9f5508f3b44706300f4ae7f9c13b5e0687fc5`。更换 PVF 时需明确更新摘要并重新验证兼容性。

默认仅绑定本机；对外部署、密码位置、端口和持久化规则见 **[86 级部署说明](deploy/vipai86/README.md)**。公网登录需配置自己的 HTTPS 入口。保留的 `deploy/ubuntu` 与部分历史文档来自上游，不作为本项目 86 级部署入口。

Windows 登录器源码位于 `clients/launcher`，使用 .NET 10 Windows SDK 构建；见 [登录器 README](clients/launcher/README.md)。它通过自己的后台地址登录，没有作者域名回退；记住密码使用 Windows 当前用户 DPAPI。

## 验证范围

2026-10-22 基础版本已验证空库启动、双频道原生进程、DP2/Frida 实际加载、后台注册登录和 RSA、公钥匹配、容器重建后数据保留、外联限制及离线镜像导入。Linux 回归 95 项（92 通过、3 跳过），Windows 回归 95 项（90 通过、5 跳过）；部分平台/集成检查另行执行。

本次 Git 发布另外复验 101 项：Windows 96 通过、5 跳过；精简 Linux 运行镜像 96 通过、5 跳过（包括因未安装 gcc 而跳过的两项 Frida 原生编译测试）。资源恢复首次执行与重复执行均成功，新增损坏输入/路径越界/不覆盖文件测试，并修复 UDP 辅助代码在 DNS 完成与关闭并发时的退出竞态。

**完整客户端进服、战斗、组队/P2P 和生产稳定性尚未实测。** 预编译二进制未获得完整源码级安全证明；固定摘要不等于无后门证明。保留的 QK 适配代码不代表已启用或已确认晴空兼容。

## 来源

- [linux-lixin/dof](https://github.com/linux-lixin/dof)：本次修改基线 `60d58715beaa870d0314b5c7fe050bd78cb3f258`。
- [1995chen/dnf](https://github.com/1995chen/dnf)：游戏部署与资源，保留 [LICENSE](services/game/LICENSE)。
- [onlyGuo/dnf-server-public](https://github.com/onlyGuo/dnf-server-public)：后台。
- [onlyGuo/DNFLogin](https://github.com/onlyGuo/DNFLogin)：登录器。

固定输入见 [vipai86-inputs.lock.json](metadata/vipai86-inputs.lock.json)，更早来源见 [source-lock.json](metadata/source-lock.json)。本仓库不为第三方游戏资源添加新的授权声明。
