# 网页管理后台

通用后台与原生网页管理共用源码，部署入口不同。最新验证与未完成项见 [当前状态](../../docs/dof/status.md)。

| 场景 | 构建／启动入口 | 数据初始化 |
|---|---|---|
| 默认 `dof` 新实例 | 通用 `Dockerfile`、`entrypoint.sh`，根目录安装器 | 受保护的首次 `init.sql` 初始化 |
| `native` 隔离网页 | `compile_admin.py` / `package_admin.py`、`Dockerfile.dof-prebuilt`、`dof-entrypoint.sh` | 显式准备专用身份与 `dof-admin.sql` 管理表，升级不重放首次准备 |

原生部署按 [部署与恢复](../../docs/dof/deployment.txt)，API 为 `/api/v1/admin/dof`，页面为 `/admin/dof`。以下通用镜像命令及首次初始化说明不能作为已有原生测试实例升级步骤；不要重跑 `init.sql` 或重置游戏业务库。

前端在 `frontend/`，Java 后端在 `backend/`，后台库种子在 `database/init.sql`，
容器入口和 Nginx 模板在 `docker/`。各游戏角色共用这一套后台。

从仓库根目录构建：

```bash
docker build -f services/admin/docker/Dockerfile -t dnf-admin:latest services/admin
```

Docker 构建包含前端和后端，不要求宿主机另装 Java/Node。不要只把 `docker/` 作为构建上下文。
容器中的 `/app/data`、初始化标记和密钥路径保持不变。

默认入口：[一键安装](../../README.md)；手动模板：[整体部署](../../deploy/ubuntu/all/README.md)、[独立后台](../../deploy/ubuntu/split/admin/README.md)。

开发操作及原上游使用说明见 [后台文档](../../docs/admin/README.md)。

## 首次部署

先等待游戏服务完成基础库导入，再启动后台。后台需要已有 `d_taiwan.accounts`；
首次初始化会增加 `admin`、`parent_uid`、`isRobot` 列并创建 `dnf_service`。
当前 DAO 使用固定的 `dnf_service` 库名，部署时 JDBC URL 必须选择该库。
MySQL 5.6.51 可配合 `MYSQL_INIT_SSL_MODE=disabled` 使用。

- `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 必须由部署注入，没有上游数据库地址或密码默认值。
- `DNF_PUBLIC_IP` 用于首次内辅配置的服务器地址，省略时只使用 `127.0.0.1`；初始等级上限为 86。配置文本接口是 `/api/v1/admin/assist/config-text`。
- 部署同时设置 `ADMIN_PASSWORD`（12–128 字符）和 `ADMIN_TEMP_SECRET`（16–64 位 ASCII 字母、数字、下划线或连字符）。首次 SQL 导入成功后创建 `admin` 管理账号；若已有管理员或同名账号则保留。临时授权表非空时也保留原值。该管理员仅用于后台，不具备完整游戏账号关联表。
- 两个管理员变量都不设置时允许手动初始化；只设置其中一个会在导入前报错。它们只用于初始化，不能通过修改环境变量重置已有密码。
- `/app/data` 保存匹配的 `private.key`、`publickey.pem` 和 `.db_initialized`。部署由密钥服务预先生成密钥；已有不完整密钥对会阻止启动。不要删除初始化标记来重跑 SQL。发现部分导入会停止，不自动覆盖数据库。

初始更新列表和轮播图为空，客户端下载地址为空。管理员填写自己的地址后正常使用，重启不会清空这些设置。静态页面默认不加载第三方字体、地图或脚本。短信默认关闭，只有显式设置 `ALIYUN_SMS_ENABLED=true` 以及自己的 `ALIYUN_ACCESS_KEY_ID`、`ALIYUN_ACCESS_SECRET`、`ALIYUN_SMS_SIGN_NAME`、`ALIYUN_SMS_TEMPLATE_CODE` 后才会发送。
