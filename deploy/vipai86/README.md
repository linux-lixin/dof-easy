# 历史 86 级离线交付说明

**下面描述的是最初的 `vipai86-*:local` 离线镜像交付，仅用于维护已有旧实例。当前通用版本请使用仓库根目录 `bash dof.sh`；选择 86 级也使用同一入口，见 [当前 README](../../README.md)。新的共享镜像不再内置固定等级程序或 PVF。**

本方案使用 vipai 的 86 级 `df_game_r`、数据库种子、普通 DP2 与 Frida，以及修改后的 linux-lixin/dof 后台和 Windows 登录器。不包含花枝专用插件。只适用于**全新数据库**，不要把既有生产数据卷接到首次初始化流程。

## 离线启动

当前仅在 Linux x86_64 主机维护，使用 Linux amd64 Docker Engine 与 Compose v2。两个频道建议先预留 8 GB 内存；实际容量以客户端实测为准。

1. 导入交付目录中的镜像：`docker load -i vipai86-images.tar`。
2. 进入此目录，执行 `python configure.py --public-ip 你的服务器IPv4`。该命令生成 `.env` 中的随机数据库、后台和管理密码，不向终端打印。管理员用户名为 `admin`。
3. 默认只监听本机。需要其他电脑连接时，在 `.env` 将 `BIND_IP` 改为服务器的监听地址，并确保 `PUBLIC_IP` 是客户端能访问的 IPv4。
4. 执行 `docker compose up -d`，然后 `docker compose ps`、`docker compose logs -f game`。首启会复制 PVF、导入数据库并生成独立 RSA 密钥，需要等待频道加载。
5. 后台地址为 `http://服务器地址:18080`。把 Windows 登录器放进**配套完整 86 客户端**目录，将 `login.ini.example` 复制成 `login.ini`，填写自己的后台地址（不加 `/api/v1`）。不要填写上游作者域名。注册验证码中的字母使用小写。

镜像已包含用户提供的 `Script.pvf`，首启仅在 `/data/Script.pvf` 不存在时复制。现有文件不覆盖。也可以在步骤 2 使用 `--pvf /自己的/Script.pvf`；不会接受百度网盘临时下载文件。已提供 PVF 并不等同于已验证与某个客户端兼容；交付不含完整 Windows 游戏客户端。

## 数据、插件与日志

- MySQL 存放在本 Compose 项目的 `database` 命名卷。`docker compose down` 保留数据；**不要使用 `down -v`**，它会删除数据库卷。
- `runtime/game/data/Script.pvf`、`df_game_r` 与 `dp/` 保存游戏资源及普通 DP2 脚本；`plugins/frida/` 保存 Frida 脚本及运行配置。首次复制后保留本地修改。
- `runtime/game/log/`、`runtime/game/frida-log/` 持久化游戏、DP2 和 Frida 日志。初始化日志轮换保留历史文件；没有定时清空日志或 shell history 的任务。容量需要运营者自行监控。
- `runtime/game/core-log/` 按组件持久化其他原生服务的日志；二进制和只读资源仍由镜像提供。
- `runtime/admin/data/` 保存登录密钥和初始化标记；账号与后台客户端配置保存在 MySQL 卷。私钥只交给后台，游戏只获取公钥。不要更换单边密钥，也不要公开此目录或 `.env`。
- `.env` 中 `PLUGIN_MODE` 支持 `dp2-frida`、`dp2`、`frida`、`none`，修改后执行 `docker compose up -d`。默认两者同时启用，Frida 的数据库口令由运行配置生成。
- `.dnf-ubuntu.json` 管理容器启动脚本。若手改这些脚本导致版本冲突，启动会停止并列出文件，避免静默覆盖；普通插件 Lua/JS 不受这一更新机制接管。

## 网络与登录器

后台和数据库位于内部网络。游戏与 `netguard` 共用一个独立的容器网络命名空间，由 Docker 直接映射游戏 TCP/UDP 端口，避免应用层 UDP 代理改变 STUN 地址。`netguard` 仅在该容器网络内安装防火墙：允许回环、指定数据库 TCP 3306 和已建立连接的回复，拒绝其他主动外联。游戏必须等待规则安装成功才能启动。

只有 `netguard` 获得 `NET_ADMIN`，游戏没有该权限且移除 `NET_RAW`；没有 `privileged` 或 host 网络模式，不修改宿主机防火墙。后台使用单独的 HTTP 入口代理；代理不挂载密码、密钥或数据库目录。数据库 3306、Supervisor 管理端口均不发布。

客户端入口：TCP 7001、7300、30015、30052；UDP 7001、7300、31015、31052、2311–2313；后台 TCP 18080。上线时还需配置服务器及云防火墙允许这些入口。真实客户端登录、组队和 P2P 仍需在实际 NAT/公网环境验收。

当前后台 HTTP 供本机/受控内网联调。公网使用前请在入口前配置自己的 HTTPS 反向代理，并将登录器地址设为该 HTTPS 地址。登录器已移除作者域名回退，地址缺失会停止登录；记住密码使用当前 Windows 用户的 DPAPI 加密。原有明文密码不会自动发送。

## 已移除或停用的行为

不执行原始 `pianai`、`abc`、`run` 或 `sqlbackup.sh`。安装器联网授权、外部统计 SQL、第三方备份、上报及清理日志不进入启动链。普通 DP2 的 `report.create/send/close` 已替换为无网络实现。后台去除作者下载包、外链轮播和第三方页面资源，短信默认关闭。自动公网 IP 查询与 DDNS 均关闭，使用显式 `PUBLIC_IP`。

原始下载文件留在独立资源档案供比对，不代表允许它们运行。预编译游戏和插件二进制没有完整源码审计；外联隔离和源码改动不能替代对二进制安全性的证明。

## 从源码重建

离线部署只需交付镜像；源码重建需下载操作系统、Maven、npm 等构建依赖，除非已有相应缓存。游戏和数据库基础镜像、主要原始资源已固定摘要；后台构建阶段仍使用上游镜像标签，交付清单另记录本次实际构建的镜像 ID。

将资源归档解压为此仓库同级的 `resource-archive`，在仓库根目录执行：

```sh
bash dof.sh install --instance my86 --level 86 --ip 你的服务器IPv4 --pvf /path/to/Script.pvf --resources ../resource-archive
```

原始数据库种子仅将 `DEFINER=game@127.0.0.1` 改为 `DEFINER=CURRENT_USER` 后用于初始化；附加 SQL 只补建 5 张缺失的维护模板表，不删除或覆盖原表。Frida 的旧 glibc 私有符号通过限定到游戏进程的 32 位兼容库处理，镜像构建执行 ELF 检查和无网络 Frida 原生 hook 测试。
