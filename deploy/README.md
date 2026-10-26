# 部署导航

游戏运行使用 Linux amd64 Ubuntu 镜像。先选择入口和登录模式，不混用不同入口的初始化脚本。

| 场景 | 入口 | 数据及数据库 |
|---|---|---|
| 新实例，默认 `dof` 模式 | [根目录一键部署](../README.md) | `.dof-easy/instances/<名称>/`；默认独立数据库容器卷；可选外置 MySQL |
| 同机宝塔 Nginx、Docker，86 级原生新服 | [完整部署指南](../docs/baota-deployment.md) | `--database internal` 或 `external`，首次选择后固定 |
| 新实例的 `native` 资源准备 | [原生资源导入](../docs/dof/launcher.md) | 固定免授权输入及正式锁；Windows 链尚待完成 |
| 当前绑定的 86 级测试游戏更新 | [限定目标部署](../docs/dof/game-test-deployment.md) | 私有 profile、保留恢复点，不重置游戏业务库 |
| 原生网页准备与升级 | [网页部署与恢复](../docs/dof/deployment.txt) | 仅操作指定网页容器；与游戏部署入口分离 |
| 高级手动部署、外置 MySQL | [ALL](ubuntu/all/README.md)、[拆分](ubuntu/split/README.md)、[独立后台](ubuntu/split/admin/README.md) | 所选目录 `.env`、`runtime/` 或 `RUNTIME_ROOT` |

手动 Ubuntu 模板采用默认 `dof` 登录链，保留 CORE/GAME/P2P 分角色方案。`build` 和 `shared` 不是独立运行入口；模板相对引用公共定义，应保留仓库层级。Relay/STUN 是游戏联机服务。

当前运行版本和验收边界以 [状态页](../docs/dof/status.md) 为准。历史 `vipai86` 文档不再是通用安装入口，一键安装的外置数据库应使用上述宝塔指南，不混用手动模板的初始化方式。
