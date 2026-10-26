# dof-easy · 通用一键容器端

对照原 `pianai` 安装脚本的菜单，使用同一套镜像部署 **55 / 60 / 65 / 70 / 75 / 80 / 85 / 86 / 90 / 95 / 100 / 110 / 120** 级服务端。**新安装菜单可选择等级，直接回车默认 86**。等级程序和自己的 PVF 放在实例数据目录，换新服无需修改 Dockerfile 或重新烘焙 PVF。

普通插件可选 **DP2 + Frida / DP2 / Frida / 不启用**。当前有两条独立登录链，不能混用客户端或把一条链的验收结果套用到另一条：

| 模式 | 配套与入口 | 当前边界 |
|---|---|---|
| `dof`（高级命令默认） | 网页后台与 [WPF 登录器](clients/launcher/README.md)，按下文一键部署 | 按所选等级另行验证实际客户端 |
| `native`（新菜单默认） | 固定免授权输入派生的网关、插件与网页原生管理，见 [原生模式文档](docs/dof/README.md) | 86 级服务端加载、网页配置和 PVF 处理已有记录；Windows 读取端与完整游戏流程尚未完成 |

**最新状态和剩余工作统一见 [当前状态](docs/dof/status.md)**。代码已合入 main 不等于完整游戏链路已验收；下方新菜单默认启用原生网关；高级命令示例明确区分登录模式。

## 菜单式一键端

在 Linux amd64 Ubuntu 24.04／26.04 或 Debian 12／13 上准备项目代码、配套 PVF 和固定免授权材料，然后运行：

```sh
cd dof-easy
sudo bash install.sh
```

脚本按需补齐 Python、Docker、Compose 和 Buildx。菜单包含安装、启停、日志、地址与频道、插件和团本开关、备份、Swap、防火墙、网页入口和实例切换。宝塔与宿主机 Nginx 均为可选；网页自带容器 Nginx。

安装选择等级（**回车默认 86**）、插件组合、团本扩展、内置或同机外置 MySQL 5.6。默认本机构建，也可拉取自己的镜像。确认页展示磁盘预算、Swap 和防火墙操作；确认前不创建实例。Python 缺失时，入口先单独询问是否安装菜单所需的 Python。

默认网页直接通过 `http://服务器IP:18080` 访问，也可选择仅本机监听供反向代理。密码隐藏输入，临时 MySQL 管理员配置在结束或异常后删除。已有实例沿用配置，初始化中断不会自动重新灌库。普通插件关闭时保留原生登录必需组件。

默认尝试关闭识别到的 UFW／firewalld并记录恢复状态；已有 Docker/自定义规则可能被 firewalld 清除时，提前拦截并提示保留防火墙。云安全组需按最终端口清单设置。外置 MySQL 监听所有接口时，先改私网绑定或选择保留防火墙。Swap 只补足缺少部分，不删除已有 Swap。

完整说明见 [容器一键端指南](docs/container-installer.md)，宝塔用户另见 [可选宝塔部署](docs/baota-deployment.md)。两个频道建议至少 8 GiB 内存；增加 Swap 不等于性能已达标。

## 高级命令部署

所有新建入口默认 86 级；已有实例保留原等级。高级命令默认 `dof` 模式，菜单入口默认 `native`。数据库都默认内置，外置使用 `--database external`。

高级命令默认拉取自己的镜像，先运行 `bash dof.sh images --registry 你的仓库/命名空间 --tag 版本`；本机源码构建显式加 `--build`。不自动从拉取失败回退构建。

无需交互的例子（省略 `--level` 即为 86）：

```sh
bash dof.sh install --level 70 --ip 192.168.1.100 --pvf /data/Script.pvf --plugins dp2-frida
# 也可在同一条安装命令中指定，省略前面的 images 配置步骤：
# bash dof.sh install --registry registry.example.com/your-project --tag v1 --ip 192.168.1.100 --pvf /data/Script.pvf
# 86 只是同一入口的一个选项：
# bash dof.sh install --instance my86 --level 86 --ip 192.168.1.100 --pvf /data/86/Script.pvf
```

默认频道 `15,52`，可以用 `--channels "11,12,52"` 指定（保留引号）；端口映射和容器防火墙同步生成。游戏默认监听 `0.0.0.0`，本机测试可加 `--bind 127.0.0.1`。网页默认监听 `127.0.0.1`，通过 Nginx 反代访问；需要改变时显式指定 `--admin-bind`。`--admin-port` 默认 `18080`。`--ip` 填客户端实际能访问的服务器 IPv4。

安装结束会显示后台地址和密码文件位置。管理员账号 `admin`，密码在 `.dof-easy/instances/main/.env` 的 `ADMIN_PASSWORD`。登录器公钥和地址配置示例自动导出到该实例的 `client/`。将 [dof 登录器](clients/launcher/README.md) 放进配套客户端目录，使用自己的后台地址；公网部署使用自己的 HTTPS 入口。

四个镜像名固定为 `仓库/命名空间/dof-easy-db:标签`、`dof-easy-admin:标签`、`dof-easy-game:标签`、`dof-easy-netguard:标签`，均为 Linux amd64，使用同一版本标签。源码构建及上传命名示例见 [镜像仓库配置](docs/image-registry.md)。

## 日常管理

```sh
bash dof.sh status
bash dof.sh logs --service game --follow
bash dof.sh stop
bash dof.sh start
bash dof.sh restart
bash dof.sh config --plugins frida --raid-extension off
bash dof.sh config --ip 192.168.1.101
bash dof.sh backup
bash dof.sh versions
```

以上命令都可加 `--instance 名称`，默认 `main`。停止、重启和重跑安装不会清库、换密码或覆盖插件脚本。已有实例不允许用 `install --level` 改成另一版本；用新实例名创建新服，原实例保留。相同宿主机监听地址下的游戏端口不能被两个实例同时占用，切换前先停止旧实例。

实例保存自己的镜像地址及标签；修改全局配置不会改变已有实例。`start`、`restart` 和备份均使用该实例的镜像，不拉取、不构建。需要更换已上传的镜像版本时运行 `bash dof.sh install --instance main --tag v2`，所选模式的镜像全部获取成功后才更新实例配置并启动。

备份会短暂停止写入服务，将 SQL、PVF、密钥、配置、插件和日志打包到本机 `.dof-easy/backups/`，然后恢复原先运行的服务。备份包含密码和私钥，不要提交 Git。

## 资源与离线部署

公共构建输入已经随 Git 提供：服务端公共文件、初始化 SQL、普通 DP2、Frida、Lua/JS 和配置模板都在仓库中。需要自己构建时显式运行 `bash dof.sh build`（只构建，不安装或上传），或在安装命令加 `--build`。普通安装直接拉取成品镜像，不检查或编译这些构建输入。

只有等级程序从固定的 [resources-v1 Release](https://github.com/linux-lixin/dof-easy/releases/tag/resources-v1) 按需下载，每次安装约 10.6 MB，不访问原网盘或上游下载源。13 个等级解压后共约 473 MB，单个实例只用其中一个，因此不全部塞进每次 Git 检出。需要缓存全部等级时：

```sh
bash dof.sh resources --resources /data/dof-resources
```

已有缓存可以在安装时传 `--resources /data/dof-resources`。文件名和 SHA-256 未改变，旧缓存中的等级包仍可复用。仓库内公共输入和下载的等级包都验证大小及 SHA-256，不匹配时停止，不覆盖修改过的文件。清单见 [等级资源](metadata/levels.lock.json) 和 [仓库内构建输入](metadata/build-inputs.lock.json)。PVF 必须自行提供。

仓库直接维护禁用上报后的插件代码，Frida 配置没有固定数据库密码，运行时由实例注入。Release 中早期上传的五个公共归档保留供旧提交使用，当前入口不再引用。具体分配和维护规则见 [资源布局](docs/resource-layout.md)。系统软件包、npm、Maven 和 Docker 基础镜像仍由各自依赖源提供。

离线安装可加 `--offline --images /data/dof-easy-images.tar --resources /data/dof-resources`，自动导入镜像后初始化启动。镜像名称须与所配置的仓库及标签一致，已经导入时可省略 `--images`。旧 `dof-easy-*:local` 归档可显式加 `--registry= --tag local` 使用。离线模式不下载、不拉取、不构建；与 `--build` 互斥。缺镜像或资源会报错并保留实例数据。只有源码构建需要系统软件、npm、Maven 等构建依赖源。

## 数据和行为

- 数据库使用每个 Compose 项目独立的命名卷；PVF、等级程序、插件、密钥和游戏日志放在 `.dof-easy/instances/<名称>/runtime/`。
- 原脚本的联网授权、外部统计 SQL、第三方备份、上报和清日志流程不进入启动链。DP2 上报模块已替换为无网络实现；保留历史日志。
- 游戏只能连接内部数据库并回复客户端，其他主动外联由独立 netguard 容器阻止。没有宿主机网络模式，不改宿主机防火墙，不公开数据库 3306。
- 初次建库将后台等级设置为所选版本；改 IP 同步修改后台客户端配置。后台已有其他设置和插件 Lua/JS 保留。

Git 包含公共服务端和插件构建输入，不包含 PVF、所选等级程序、实例运行数据、实例密码或私钥。部分共享实现仍保留在 `deploy/vipai86`、`scripts/vipai86` 下以兼容早期代码；**当前入口是根目录 `dof.sh`，不是旧的手动 86 级部署步骤**。

## 验证与来源

通用安装器历史记录见 [通用容器验收](docs/container-verification.md)，原生 86 级联调结果见 [当前状态](docs/dof/status.md)。游戏进程、端口、插件加载及后台测试不等同于完整客户端进服、战斗、组队/P2P 的兼容验证；这些仍需使用所选版本的配套客户端验收。

基线为 [linux-lixin/dof 的 60d58715](https://github.com/linux-lixin/dof/tree/60d58715beaa870d0314b5c7fe050bd78cb3f258)，保留 [1995chen/dnf 许可证](services/game/LICENSE) 以及后台、登录器来源说明。本仓库不为第三方游戏资源添加新的授权声明。
