> 上游说明存档：界面与历史用法供参考，原路径和默认值不作为当前部署步骤。当前结构与构建请从 [后台文档](../README.md) 开始。

# DNF Server Docker 使用指南

本目录包含构建和运行 DNF Server Docker 容器所需的文件。

## 功能特性

- **一体化**: 提供统一的GM管理服务, 包括GM基础功能(游戏账号、角色、邮件等管理)以及游戏网关(登录、注册)等。
- **默认平台构建**: 使用当前 Docker Builder 的默认架构，不再指定或同时构建多个平台。
- **自动初始化**:
    - **数据库**: 首次运行时根据提供的连接设置自动初始化数据库结构。
    - **密钥**: 如果缺少 RSA 密钥 (`private.key`, `publickey.pem`)，则会自动生成。
- **数据持久化**: 密钥和其他数据通过 Docker 卷进行持久化保存。

## 前提条件

- 系统已安装 Docker。
- Docker可以访问到游戏的数据库(MySql5)。

## 快速开始

### 1. 运行容器

请将环境变量替换为您实际的数据库配置。

```bash
docker run -d \
  --name dnf-server \
  -p 80:80 \
  -v $(pwd)/data:/app/data \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://YOUR_DB_IP:3306/dnf_service?useUnicode=true&characterEncoding=utf8&autoReconnect=true" \
  -e SPRING_DATASOURCE_USERNAME="root" \
  -e SPRING_DATASOURCE_PASSWORD="your_password" \
  guoshengkai/dnf-server:latest
```

> **注意**: 容器默认监听 **80** 端口。

### 2. 验证

- **Web 界面**: 在浏览器打开 `http://localhost`。
- **日志**: 查看日志 `docker logs -f dnf-server`。

## 配置说明

### 环境变量

| 变量 | 描述 | 默认值 |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | JDBC 连接 URL。镜像将解析此 URL 以确定数据库主机、端口和数据库名称以进行初始化。 | (必填) |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名。 | (必填) |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码。 | (必填) |
| `NGINX_PORT` | Nginx 在容器内监听的端口。 | `80` |
| `SERVER_PORT` | Java 后端监听的端口 (内部)。 | `8080` |
| `TZ` | 时区设置。 | `Asia/Shanghai` |

### 挂载卷

| 路径 | 描述 |
|------|-------------|
| `/app/data` | 存储生成的 RSA 密钥 (`private.key`, `publickey.pem`) 和数据库初始化标记文件。映射到主机目录以在重启后保留密钥。 |

> **注意**: 确保映射的主机目录具有适当的权限，并确保以下条件:
> - 该目录存在且可写。
> - `data`目录下的公钥和私钥相互匹配。
> - 游戏`game`目录下的公钥与`data`目录下的公钥一致(如果更换了公钥, 需要重启游戏服务)。

## 自主构建镜像

### 1. 本地构建 (单架构)

如果您只需要在当前机器上运行镜像：

```bash
docker build -t dnf-server .
```

### 2. 构建并推送（默认平台）

提供的脚本使用当前 Docker Builder 的默认架构。`build-multi-arch.sh` / `.ps1` 保留历史文件名，但不再执行多架构构建或切换 Builder。
**注意**: 脚本会推送镜像，因此您必须登录 Docker Registry (如 Docker Hub)。

1.  登录 Docker Hub:
    ```bash
    docker login
    ```

2.  运行构建脚本:
    ```bash
    ./build-multi-arch.sh
    ```
    脚本会提示您输入 Docker Hub 仓库名称 (例如 `yourusername/dnf-server`)，然后自动构建并推送镜像。

## 数据库初始化

容器包含自动初始化数据库的机制：

1.  启动时，检查 `/app/data` 中是否有标记文件。
2.  如果标记缺失 (首次运行)，将解析 `SPRING_DATASOURCE_URL`。
3.  提取数据库名称 (例 `dnf_service`)，如果不存在则创建它。
4.  执行内置的 SQL 脚本以建立数据表。
5.  **成功**: 创建标记文件，以便后续重启跳过此步骤。

**警告**: 确保您的数据库用户具有 `CREATE DATABASE` 和 `CREATE TABLE` 权限。

## 自动生成密钥

应用程序运行需要 RSA 密钥。
- 如果 `/app/data/` 中未找到 `private.key` 和 `publickey.pem`，容器将自动生成一对 **2048位 PKCS#8** 密钥。
- 如果您已有密钥，只需在启动容器前将其放入映射的 `data` 卷目录即可。
- 你也可以在这里在线生成（长度:`2048bit` 格式:`PKCS#8`）：http://www.metools.info/code/c80.html
## 为什么不内置秘钥而是鼓励自己生成?
- 这涉及到游戏登录的Token计算, 如果内置了秘钥, 那么我认为90%的人都会直接使用内置秘钥, 那么这会带来非常高的安全隐患. 一旦别有用心的人知道了私钥, 就可以通过登录签名的算法跳过密码登录任意账号.


## 2026-10-15：旧 MySQL 初始化兼容修复

- 新增环境变量 `MYSQL_INIT_SSL_MODE`：默认 `auto`，保留 MariaDB 客户端的 TLS 默认行为；对于没有 TLS 的专用旧 MySQL 测试实例，显式设为 `disabled`，初始化 CLI 使用 `--skip-ssl`。JDBC URL 的 `useSSL=false` 不控制 CLI。
- 初始化 SQL 失败时立即停止，不再继续启动 Nginx/Java；成功后才写入 `/app/data/.db_initialized`。
- 标记缺失但后台库或 accounts 的 admin/parent_uid/isRobot 字段已存在时，不自动重跑含 DROP/ADD 的初始化 SQL，需先核对并补齐已有结构。
- 只缺失一半 RSA 密钥时停止，避免覆盖仍有效的另一半密钥。
- 本地集成目录的 `deploy/*/bootstrap-admin.sql.example` 使用授权记录 upsert，兼容 ADMIN_TEMP_PASSWORD 空表。管理员账号仍需检查不存在后创建，随机密码不存入源码。

单独构建本仓库时，按上述方式设置 TLS 环境变量；使用固定旧镜像时，需要集成目录提供的入口覆盖文件。这些修复不等于已完成玩家注册/进服或自动更新业务验证。
