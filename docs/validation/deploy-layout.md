# 部署目录整理验证

日期：2026-10-26。基线 main `db0c4bb`。仅在本地操作，未连接服务器、未更改现有实例。

## 改动

正式部署组件迁入 `deploy/database`、`network`、`bootstrap`、`nginx`、`shared`、`manual`；旧固定 86 级 Compose、配置工具与说明归档到 `docs/history/offline86`。当前构建、根 Compose、数据库导入、Nginx 模板生成与测试均使用新路径。数据库种子改名为 `seed.sql`，SQL 字节、长度与哈希不变。五个手动模板补齐其文档所需的无真实凭据 `.env.example`。

本地来源导入工具移至 `scripts/imports`，默认输出私有 `.dof-easy/imported-assets`；普通插件加载器改为 `plugins.py`，启动脚本同步更新；网络守卫容器入口改为 `dof-netguard.py`，镜像入口、健康检查及安装器检查同步更新。就绪模式只接受 `native` 和 `dof`，历史别名明确拒绝。相关测试按功能改名。锁文件更新两个 SQL 路径及 DP2 禁用外报桩的注释哈希（仅注释改名，不改变 Lua 行为）。原生二进制没有修改。

旧词仅保留在历史交付记录、说明、明确拒绝旧别名的测试，以及防止旧私有输入误提交／入镜像的 ignore 规则中；这些不构成当前运行依赖。旧镜像内部文件不会随 Git 更新，必须使用本提交构建的新游戏／网络守卫镜像。

## 已执行

- `scripts/container/resources.py`：422 项共享输入、15 项正式原生文件校验通过，不下载材料。
- 最终受影响单元测试 162 项：158 通过、4 项按环境跳过；0 失败。覆盖菜单、数据库模式、镜像构建入口、网页转发、网络规则逻辑、插件组合、团本依赖、就绪状态和本地归档导入。跳过项分别为显式启用的 Docker 网络实测、显式指定容器的网页 MySQL 实测、需要 Node.js 的 Frida JS 测试和需要显式私有原件的派生重现；未计为通过。最初模块调用暴露网页测试的导入路径问题，已修复并重跑通过。
- `tests.structure.test_deploy_layout`：6 项通过，使用真实 Compose CLI 检查五个手动模板、相对引用、运行目录覆盖、镜像内入口与消费者一致性及 Markdown 文档链接。
- `tests/integration/mysql/compose_matrix.py`：内置／外置数据库、dof／native、有效插件和团本组合共 24 项通过；核对新路径下的只读密钥／入口脚本挂载、keys 启动依赖、db-check 常驻命令与 db-tools 按需 profile。
- 全新 bubblewrap 网络／PID／挂载命名空间中的 MySQL 5.6.51 实测通过：新种子路径初始化 23 个原生表、重复初始化保留、自定义 socket／TCP 管理员、限定授权、UUID 拒绝、819,110 字节项目备份与恢复、无关站点保留。
- Shell 语法与 `git diff --check` 通过。

## 未执行

未重建完整游戏／网页镜像，未部署现有服务器，未执行客户端登录和玩法验证。容器职责以根 Compose 及动态外置数据库模型为依据，不以容器健康代替进游戏成功。宿主机防火墙、云安全组和数据库监听配置均未修改。
