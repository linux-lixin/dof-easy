# 后台配置、客户端与自动更新

适用范围：`LAUNCHER_MODE=dof` 的 WPF 登录器与默认后台。原生模式的配置容器、PVF 加密和读取端另见 [原生文档](../dof/README.md)。下表采用手动模板频道 11、52；通用安装器默认 15、52，以实际生成配置为准。

先完成 [Ubuntu 游戏和后台部署](../../deploy/ubuntu/all/README.md)，再按本文配置玩家端。拆分部署的端口以各节点 Compose 和端口表为准。

## 1. 开放端口并配置后台

云安全组和宿主机网络规则需要允许以下端口。测试阶段可以把来源限制为测试电脑公网 IP：

| 用途 | TCP | UDP |
|---|---|---|
| 后台及登录器 API | 18080 | — |
| 游戏连接 | 7001、7300、30011、30052 | 7001、7300、31011、31052、2311–2313 |
| 可选下载服务 | 18081 | — |

Docker 发布端口的流量可能绕过 UFW 的常规规则，公网来源限制应同时在云安全组或适当的 Docker 防火墙规则中落实，参见 [Docker 防火墙说明](https://docs.docker.com/engine/install/ubuntu/#firewall-limitations)。

浏览器访问 `http://服务器公网IP:18080/login`，账号为 `admin`，密码是部署时设置的管理员密码。

登录后：

1. 在辅助配置／内辅管理中，将服务器地址改为游戏服务器公网 IP。
2. 等级等配置与实际客户端版本保持一致，本地 GM 功能保持关闭。
3. 第一轮保持更新列表为空。
4. 默认门户、图片、下载地址按自己的部署修改，不能直接使用作者默认地址作为自己的资源。

检查登录器获取的配置：

```bash
curl --fail http://127.0.0.1:18080/api/v1/admin/assist/config-text
```

返回的服务器地址应是你的地址。登录器使用这条匿名配置接口，不能仅因路径里有 `admin` 就在代理层一律禁止访问。

## 2. 在 Windows 编译登录器并配置客户端

WPF 登录器在 Windows 编译。安装 **.NET 10 SDK**，仅安装 Runtime 不够，参见 [微软安装说明](https://learn.microsoft.com/en-us/dotnet/core/install/windows)。

以下假设 Windows 已将同一仓库克隆到 `D:\VScodeProjects\dof-easy`；如果没有，先在 PowerShell 执行 `git clone https://github.com/linux-lixin/dof-easy.git D:\VScodeProjects\dof-easy`。已有目录不要重复克隆。

```powershell
dotnet --list-sdks
dotnet publish "D:\VScodeProjects\dof-easy\clients\launcher\DNFLogin.csproj" `
  -c Release `
  -r win-x64 `
  --self-contained true `
  -p:PublishSingleFile=true `
  -o "D:\VScodeProjects\dof-easy\artifacts\launcher"
```

将发布目录内的全部发行文件放进配套游戏客户端根目录：

```text
客户端目录/
├─ DNF.exe
├─ Script.pvf
├─ ImagePacks2/
├─ DNFLogin.exe
├─ login.ini
└─ 其他游戏文件
```

**仓库不包含完整 Windows 游戏客户端。** 需要另外准备与服务端版本、PVF 配套的客户端。不要用 `DNFLogin.exe` 替换 `DNF.exe`。

在游戏根目录创建 UTF-8 的 `login.ini`，将地址替换成真实服务器地址：

```ini
[Login]
BaseUrl=http://REPLACE_WITH_PUBLIC_IPV4:18080
WindowTitle=我的DNF
GameVersion=1.0.0

[Credentials]
RememberPassword=False
```

`BaseUrl` 不加 `/api/v1`。`GameVersion` 对应实际采用的资源基线，不能随意标成最新版；本次修改后的登录器在配置缺失或错误时停止联网并提示修正，不会回退到作者域名。

在 Windows 检查客户端 PVF：

```powershell
Get-FileHash "D:\你的客户端目录\Script.pvf" -Algorithm SHA256
```

在 Linux 检查服务端 PVF：

```bash
sha256sum /opt/dof-easy/deploy/ubuntu/all/runtime/game/data/Script.pvf
```

两边需要使用配套资源，这里的 PVF 内容应一致。服务端能启动不代表任意客户端都能登录；客户端还需要完整的 EXE、DLL、ImagePacks2 等文件。

## 3. 测试注册、登录和进游戏

运行 `DNFLogin.exe`：

1. 注册一个新的普通测试账号，不使用后台管理员进游戏。
2. 推荐人填写 `admin`，或留空使用当前默认推荐人。
3. 当前验证码有大小写问题，字母先按小写输入。
4. 注册成功后登录。
5. 依次测试创建角色、进入频道、进入副本、退出重登。
6. 再用第二台测试电脑验证连接和组队。

登录器会从后台获取配置并写入 `DNF.toml`，服务器地址应在后台配置好，不能只改本地 TOML。API 返回凭据不代表游戏端已经接受；失败时检查游戏日志、关联账号表、密钥配对、PVF、IP 和 TCP/UDP 放行情况。

## 4. 配置完整客户端下载与自动更新

先完成正常进游戏测试，再单独启动静态下载容器（首次需要拉取 Nginx 镜像）：

```bash
cd /opt/dof-easy/deploy/ubuntu/all
mkdir -p packages
docker run -d --name dnf-download --restart unless-stopped \
  -p 18081:80 \
  --mount "type=bind,source=$PWD/packages,target=/usr/share/nginx/html,readonly" \
  nginx:stable-alpine
```

下载容器独立管理：日志用 `docker logs dnf-download`，停止用 `docker stop dnf-download`，
已创建后再次启动用 `docker start dnf-download`。拆分部署可在下载节点单独运行，将包路径和公开地址改为该节点的实际值。

开放 TCP `18081`。将配套客户端打成真正的 ZIP，根目录直接放游戏文件：

```text
client-1.0.0.zip
├─ DNF.exe
├─ Script.pvf
├─ ImagePacks2/
└─ 其他游戏文件
```

不要额外套一层“客户端”目录，不要把 7z 改后缀当 ZIP，也不要包含账号密码、私钥或玩家保存的登录信息。本轮不通过这个包替换正在运行的登录器自身。

上传到：

```text
/opt/dof-easy/deploy/ubuntu/all/packages/client-1.0.0.zip
```

确保文件服务可读：公开下载目录通常为 755、ZIP 文件为 644，不要递归放宽 `runtime` 私钥目录的权限。

在后台完整包配置中填写：

```text
版本：1.0.0
地址：http://服务器公网IP:18081/client-1.0.0.zip
```

先确认链接能下载，再用一个只放登录器发行文件及正确 `login.ini` 的空目录测试下载、解压和登录。现有更新器可能仅因发现 `Script.pvf` 就跳过完整包检查，所以要人工确认客户端文件齐全。

测试补丁时，先制作一个仅含少量资源或测试文本的小 ZIP `patch-1.0.1.zip`，放到同一个 `packages` 目录，在后台添加并启用 `1.0.1`：

```text
http://服务器公网IP:18081/patch-1.0.1.zip
```

重启登录器，检查下载、文件落盘和 `GameVersion` 更新。首次不要直接用大型 PVF 或 DNF.exe 验证更新器。

网页后台已增加数字版本比较及 SHA-256 元数据管理；当前 WPF 下载器仍按字符串排序补丁，未读取并校验 SHA-256。这两端尚未完成一致的更新链路，版本排序、下载完整性、失败恢复和登录器自身更新需继续实现并实际验收。不能因后台接口可用就宣称 Windows 更新流程已通过。
