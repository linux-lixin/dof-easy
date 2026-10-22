# 后台开发与使用

源码入口：[services/admin](../../services/admin/README.md)。日常部署按 [Ubuntu 完整流程](../../deploy/ubuntu/all/README.md) 操作；后台生成密钥、初始化标记和现有数据库保护规则不变。

| 内容 | 位置 |
|---|---|
| Java API、账户、更新业务 | `services/admin/backend/src/main/java` |
| Java 配置与模板 | `services/admin/backend/src/main/resources` |
| Maven 依赖 | `services/admin/backend/pom.xml` |
| Vue 门户和管理页面 | `services/admin/frontend/src` |
| Npm 配置及锁文件 | `services/admin/frontend/package.json`、`package-lock.json` |
| 容器启动及数据库初始化保护 | `services/admin/docker/entrypoint.sh` |
| 后台种子 SQL | `services/admin/database/init.sql` |

使用镜像构建最直接，它已固定各构建阶段使用的 Node/Maven/JDK 镜像。
开发时安装兼容工具链后，也可分别构建：

```bash
# 在仓库根目录执行
cd services/admin/frontend
npm ci
npm run build

# 另一个终端，从仓库根目录执行
cd services/admin/backend
mvn package -DskipTests
```

前端 `npm run build` 包含项目自身的类型检查；现有 Dockerfile 则按原流程直接调用 Vite。
两者的检查范围不同，不因此次目录整理修改构建策略。Maven 包装脚本虽从上游保留，但仓库没有完整 `.mvn/wrapper`，
不要依赖 `mvnw`；使用 Docker 构建或已安装的 Maven。

页面、管理员、RSA 和客户端版本管理的图文说明保留在 [上游说明存档](upstream/README.md)。
存档中的旧默认值与旧路径不是当前部署入口。当前默认管理员名为 `admin`，密码需自行设置；后台不是玩家完整客户端包的替代物。
