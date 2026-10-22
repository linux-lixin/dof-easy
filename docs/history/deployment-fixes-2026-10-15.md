> 历史记录：描述当时的分析、路径和验收范围；当前部署请从仓库 README 与 deploy 入口开始。

# 2026-10-15 部署修复与使用说明

本次同步的是服务器上已定位的启动修复，以及避免同类失败的初始化检查。没有写入服务器密码、RSA 私钥、管理员密码，也没有修改 clients/launcher 或注册、更新器业务逻辑。

## 修改位置

| 修复 | 源码 | 固定镜像部署 |
|---|---|---|
| 原始日志种子 q_id=65535 导致自增耗尽 | services/game/.../init/prepare_login_seed.sh，由 init.sh 在导入前调用 | fixes/game-entrypoint.sh 在原镜像入口前准备种子 |
| 响应日志缺少当月表 | init/daily_job/response_time_tables.sh，由 daily_job.sh 定期调用 | 挂载相同维护脚本 |
| 后台 CLI 要求 TLS，而旧 MySQL 无 TLS | services/admin/docker/entrypoint.sh 支持 MYSQL_INIT_SSL_MODE | 挂载修复入口，Compose 默认 disabled |
| SQL 失败后仍启动 Java | 初始化失败立即退出；成功后才写标记 | 相同入口覆盖 |
| 部分初始化重试可能 DROP 表/重复 ADD 列 | 标记缺失但后台库或字段已存在时，停止自动导入 | 相同入口覆盖 |
| 管理员模板 UPDATE 无法创建空授权表记录 | deploy/*/bootstrap-admin.sql.example 改为 INSERT ... ON DUPLICATE KEY UPDATE | 空表和已有 id=1 均适用 |

源码与正在使用的 centos7-2.1.9.fix1 镜像并非同一版本，因此不能把新版 init.sh 整个覆盖到旧镜像。部署附件仅用兼容包装入口和维护脚本，不覆盖旧版整体初始化流程。

## 全新部署

1. 上传整个 `deploy/centos/legacy/external-mysql/`（或者内置数据库方案 `deploy/centos/legacy/bundled-mysql/`），必须包含 `fixes/` 子目录。已有云服务器使用的目录是 `/opt/dnf`。
2. 根据 `.env.example` 填写 `.env`，准备与游戏端匹配的 RSA 密钥。
3. 对当前专用旧 MySQL 测试实例保留 `MYSQL_INIT_SSL_MODE=disabled`。这只影响后台命令行初始化的 TLS；JDBC 的 `useSSL=false` 是独立设置。需要客户端默认 TLS 行为时用 `auto`。
4. `docker compose config --quiet`，然后 `docker compose up -d game`。首次导入前修复原始日志种子：只将已知查询的 ID 65535 改为未占用的 50939，自增起点改为 50940，保留全部 394 条查询及 SMALLINT 类型。
5. 核对频道日志和实际进程稳定后，`docker compose up -d admin`。初始化成功才会出现 `runtime/admin/data/.db_initialized`，失败则容器退出。
6. 编辑并分步执行 `bootstrap-admin.sql.example`：确认 123123 不存在，替换两处独立随机密码，创建管理员及临时授权记录。不要原样执行占位值，不要重复创建账号。
7. 验证 `curl --fail http://127.0.0.1:18080/api/v1/client/version/1.0.0`。返回 `[]` 只表示暂无更新项，尚未完成玩家进服和更新配置。

修改本地源码不会自动改变远端或 Docker Hub 镜像。当前固定镜像通过上传并使用这份 Compose 和 fixes 目录获得修复；自行重新构建镜像时则使用源码中的修复。

## 已有部署

当前云服务器已完成数据库修复、月表维护和后台管理员初始化，不要重新导入 init.sql、不要删除 .db_initialized，也不要再次创建 123123。上传新的部署文件时保留服务器的 `.env`、`runtime/` 和密钥；本轮仅修改本地文件，没有再次修改远端。

种子修复只改变首次导入文件，不会自动改动已有数据库。其他仍存在原始 65535 溢出问题的部署，可在停止所有使用 taiwan_login 的游戏频道后使用下面的离线工具。仅支持主库和大区库在同一 MySQL 实例、原始 394 条记录且相关编号未被统计表引用的情况；自定义数据会拒绝自动处理，不会清表或扩大字段类型。

下面命令针对本部署的固定镜像和 cain 大区。先上传带 fixes 的 Compose 并创建容器，确认 Supervisor 进程名与下面一致；多容器部署必须先停止所有频道。

```bash
cd /opt/dnf
docker compose exec game supervisorctl stop dnf_channel:game_siroco11 dnf_channel:game_siroco52
docker compose exec -e GAME_CHANNELS_STOPPED=yes game bash /opt/dnf-fixes/repair_existing_log_ref.sh -h 10.0.0.20 -P 3306 -uroot -p
# 输入数据库 root 密码。仅在修复成功或明确显示无需修复后启动频道。
docker compose exec game supervisorctl start dnf_channel:game_siroco11 dnf_channel:game_siroco52
```

工具不自动备份。它会核对已知种子、目标编号和引用情况，再修改一条查询记录及自增值。已修复且最大编号小于 65535 时不做修改。

## 后台初始化中途失败

- 如果仅 SSL 握手失败，数据库和新增字段均不存在：使用新入口及正确 TLS 设置重新启动即可。
- 如果已创建部分表/字段但没有标记：新入口会停止，必须检查并补齐缺失结构，不能直接重复运行含 DROP TABLE 的 init.sql。
- 如果原来已完整初始化且标记存在：直接跳过导入。
- 缺失一半 RSA 密钥时停止，不会重生成密钥覆盖已有登录凭据。

## 查看游戏五国与运行状态

```bash
docker compose exec game supervisorctl status
grep -E 'started successfully|GeoIP Allow Country' runtime/game/log/siroco11/Log$(date +%Y%m%d).init | tail -n 6
docker compose exec game sh -c 'ps -eo pid,etime,comm | grep df_game'
```

Supervisor 的 RUNNING 和 Docker 的 healthy 不足以证明进服成功。还需确认进程 PID 持续不变、没有新增 SIGSEGV、客户端注册登录及进入地图成功。

## 本地验证

`python tests/unit/test_deployment_fixes.py`：隔离 shell 行为测试，Windows 使用 Git Bash；不连接服务器。

`python tests/integration/test_mysql56_fixes.py`：本地 Docker/MySQL 5.6 集成验证，临时容器不发布端口，结束自动删除测试容器及其匿名卷；不连接部署服务器。



## 2026-10-15 本地镜像构建补充

构建入口为游戏 `build/Centos7-DNF/Dockerfile` 和后台根目录 `Dockerfile`。本次实际构建发现并修复：后台失效的 `COPY libs`、Windows 检出游戏 shell 脚本的 CRLF，以及 CentOS 7 旧 HTTP 软件源。游戏改为官方 7.9.2009 HTTPS 归档，并在更新 centos-release 后恢复归档源，防止 RPM 写回失效源。修复未推送远端。

产物及最终结果见 `dist/README.md`、`dist/build-manifest.json`、`dist/image-test-report.json` 和 `dist/game-readiness-report.json`。自建镜像使用 `deploy/centos/all`，不挂载旧镜像专用的 fixes 目录。管理员 SQL 仍需单独执行；现有持久化启动脚本和密钥需要单独核对，不能把换镜像标签等同于已有数据迁移。
