# 后台开发与使用

源码入口：[services/admin](../../services/admin/README.md)。默认模式按 [一键部署](../../README.md)，原生网页按 [专用部署说明](../dof/deployment.txt)。手动 Ubuntu 模板是高级默认模式入口，不能用其首次建库步骤升级已绑定的原生实例。最新结果见 [当前状态](../dof/status.md)。

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
两者的检查范围不同，应记录实际执行的入口。Maven 包装脚本虽从上游保留，但仓库没有完整 `.mvn/wrapper`，
不要依赖 `mvnw`；使用 Docker 构建或已安装的 Maven。

页面、管理员、RSA 和客户端版本管理的图文说明保留在 [上游说明存档](upstream/README.md)。
存档中的旧默认值与旧路径不是当前部署入口。当前默认管理员名为 `admin`，密码需自行设置；后台不是玩家完整客户端包的替代物。

活动配置入口与验收边界见[签到、战令与收集箱管理](activities.md)。

免授权版全量替代的实施进度、团本与插件文件管理接口、部署挂载及未完成项见[admin 替代实施记录](replacement-implementation.md)。

原生网页适配的发布范围、私有输入及验收限制见 [源码发布说明](../dof/publication.txt)，隔离部署见 [部署与恢复](../dof/deployment.txt)，PVF 与配套包接口见 [协议与操作说明](../dof/protocol.txt)。
