# 后台编译、前端构建与打包

用途与读者：Java/Vue 构建维护者。适用范围：dof / native；具体差异见正文。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[package.json](../../services/admin/frontend/package.json)；[Dockerfile](../../services/admin/docker/Dockerfile)；[package_admin.py](../../scripts/dof/package_admin.py)；[main-deployment-2026-10-28.json](../validation/main-deployment-2026-10-28.json)。限制和当前验收边界见[当前状态](../status/README.md)。


工作目录明确如下；源代码与产物不能混写版本。前提为可用 JDK、Maven 依赖与 Node/npm，私有样本仅在本地提供。

```bash
cd services/admin/backend
mvn -Dmaven.test.skip=false test
mvn clean package -DskipTests
```

标准构建产物位于 backend/target，测试前核对 MySQL 契约和私有样本条件；缺条件记跳过。Docker 构建使用 Maven 3.9 / JDK 24，运行 JRE 21，详见[Dockerfile](../../services/admin/docker/Dockerfile)与[pom](../../services/admin/backend/pom.xml)。

```bash
cd services/admin/frontend
npm ci
npm run build
```

前端 `npm run build` 包含 `vue-tsc -b` 与 Vite，输出 dist。容器 Dockerfile 用 `npm install` 和 `npx vite build`，没有隐含类型检查。不能用 Docker 前端构建通过代替 vue-tsc。依赖问题先处理证书/镜像源，不静默跳过检查。

专用工具在仓库根运行 `python scripts/dof/compile_admin.py --help`、`python scripts/dof/package_admin.py --help`。专用 packager 基于固定既有 loader/依赖底座，替换新编译业务类和资源并核对清单；它不是标准 Maven package。产物不得携带私有测试资源、密钥或实例配置。镜像构建入口为[后台说明](../../services/admin/README.md)及[镜像指南](../operations/images.md)。

当前部署证据显示：标准 Maven package 未完成（离线 jar 插件缺失、JDK 信任库问题）；fresh Maven compile 的 185 源文件、334 class 与专用确定性打包通过；前端类型和生产构建通过，仍有大包告警。保留这些限制，详见[部署证据](../validation/main-deployment-2026-10-28.json)。本次文档校验不重复宣称这些历史构建已重跑。

成功信号：明确构建路径、源码提交、类/静态文件清单及产物哈希，受影响回归通过；完整游戏与客户端消费另验。失败应记录具体阶段，不能将 compile 成功写成 package 成功。
