# dof-easy · 通用一键容器端

对照原 `pianai` 安装脚本的菜单，使用同一套镜像部署 **55 / 60 / 65 / 70 / 75 / 80 / 85 / 86 / 90 / 95 / 100 / 110 / 120** 级服务端，默认 **70**。等级程序和自己的 PVF 放在实例数据目录，换新服无需修改 Dockerfile 或重新烘焙 PVF。

普通插件可选 **DP2 + Frida / DP2 / Frida / 不启用**。使用 [linux-lixin/dof](https://github.com/linux-lixin/dof) 后台和登录器；未启用晴空或花枝专用插件。

## 一键部署

需要 Python 3.9+、Linux amd64 Docker 和 Compose v2；Windows 使用 Docker Desktop 的 Linux 容器模式。两个频道建议先分配至少 8 GB 内存。准备与自己客户端配套、已经下载完成的 PVF。

```sh
git clone https://github.com/linux-lixin/dof-easy.git
cd dof-easy
bash dof.sh
```

在菜单输入等级、服务器 IPv4 和 PVF 路径，安装器会连续完成：**下载并校验资源 → 构建四个共享镜像 → 初始化数据库 → 生成独立密码和 RSA 密钥 → 安装所选插件 → 启动并等待游戏及后台就绪**。

Windows 对应入口：

```powershell
.\dof.ps1
```

无需交互的例子（省略 `--level` 即为 70）：

```sh
bash dof.sh install --level 70 --ip 192.168.1.100 --pvf /data/Script.pvf --plugins dp2-frida
# 86 只是同一入口的一个选项：
# bash dof.sh install --instance my86 --level 86 --ip 192.168.1.100 --pvf /data/86/Script.pvf
```

默认频道 `15,52`，可以用 `--channels "11,12,52"` 指定（PowerShell 中须保留引号）；端口映射和容器防火墙同步生成。默认监听 `0.0.0.0`，本机测试可加 `--bind 127.0.0.1`。`--admin-port` 默认 `18080`。`--ip` 填客户端实际能访问的服务器 IPv4。

安装结束会显示后台地址和密码文件位置。管理员账号 `admin`，密码在 `.dof-easy/instances/main/.env` 的 `ADMIN_PASSWORD`。登录器公钥和地址配置示例自动导出到该实例的 `client/`。将 [dof 登录器](clients/launcher/README.md) 放进配套客户端目录，使用自己的后台地址；公网部署使用自己的 HTTPS 入口。

## 日常管理

```sh
bash dof.sh status
bash dof.sh logs --service game --follow
bash dof.sh stop
bash dof.sh start
bash dof.sh restart
bash dof.sh config --plugins frida
bash dof.sh config --ip 192.168.1.101
bash dof.sh backup
bash dof.sh versions
```

以上命令都可加 `--instance 名称`，默认 `main`。停止、重启和重跑安装不会清库、换密码或覆盖插件脚本。已有实例不允许用 `install --level` 改成另一版本；用新实例名创建新服，原实例保留。相同宿主机监听地址下的游戏端口不能被两个实例同时占用，切换前先停止旧实例。

备份会短暂停止写入服务，将 SQL、PVF、密钥、配置、插件和日志打包到本机 `.dof-easy/backups/`，然后恢复原先运行的服务。备份包含密码和私钥，不要提交 Git。

## 资源与离线部署

安装默认从本仓库固定的 [resources-v1 Release](https://github.com/linux-lixin/dof-easy/releases/tag/resources-v1) 下载所选等级及公共构建输入，不再连接原网盘或上游源码下载源，也不回退到旧源。需要归档全部 13 个等级时：

```sh
bash dof.sh resources --resources /data/dof-resources
```

已有缓存可以在安装时传 `--resources /data/dof-resources`。每个资源都按仓库内固定大小和 SHA-256 验证，解包不执行原安装脚本；不匹配时停止。清单见 [等级资源](metadata/levels.lock.json) 和 [公共构建输入](metadata/vipai86-inputs.lock.json)。PVF 必须自行提供，安装器不把任意一个 PVF 冒充所有等级的配套文件。

Release 提供 18 个资源文件：13 个等级包、服务端基础包、SQL、普通 DP2、Frida，以及固定版本的 dof 构建输入归档，附 `resources-manifest.json` 和 `SHA256SUMS`。这些是锁定的原始输入；安装器解包时应用本仓库的上报禁用及运行配置修改。Release 不包含操作者提供的 PVF、实例数据或容器镜像。系统软件包、npm、Maven 和 Docker 基础镜像仍由各自依赖源提供。

离线安装可以在同一命令加 `--offline --images /data/dof-easy-images.tar --resources /data/dof-resources`，自动导入预构建的 `dof-easy-game/admin/db/netguard:local` 四个镜像后初始化启动。镜像已导入时可省略 `--images`。离线模式不访问下载源、不运行镜像构建；缺镜像或资源会报错并保留实例数据。联网源码构建还需要访问系统软件、npm、Maven 的依赖源。

## 数据和行为

- 数据库使用每个 Compose 项目独立的命名卷；PVF、等级程序、插件、密钥和游戏日志放在 `.dof-easy/instances/<名称>/runtime/`。
- 原脚本的联网授权、外部统计 SQL、第三方备份、上报和清日志流程不进入启动链。DP2 上报模块已替换为无网络实现；保留历史日志。
- 游戏只能连接内部数据库并回复客户端，其他主动外联由独立 netguard 容器阻止。没有宿主机网络模式，不改宿主机防火墙，不公开数据库 3306。
- 初次建库将后台等级设置为所选版本；改 IP 同步修改后台客户端配置。后台已有其他设置和插件 Lua/JS 保留。

代码和构建输入保持分离：Git 不包含 PVF、第三方游戏/插件二进制、运行数据、密码或私钥。部分共享实现仍保留在 `deploy/vipai86`、`scripts/vipai86` 下以兼容早期代码；**当前入口是根目录 `dof.sh` / `dof.ps1`，不是旧的手动 86 级部署步骤**。

## 验证与来源

验证记录见 [通用容器验收](docs/container-verification.md)。游戏进程、端口、插件加载及后台测试不等同于完整客户端进服、战斗、组队/P2P 的兼容验证；这些仍需使用所选版本的配套客户端验收。

基线为 [linux-lixin/dof 的 60d58715](https://github.com/linux-lixin/dof/tree/60d58715beaa870d0314b5c7fe050bd78cb3f258)，保留 [1995chen/dnf 许可证](services/game/LICENSE) 以及后台、登录器来源说明。本仓库不为第三方游戏资源添加新的授权声明。
