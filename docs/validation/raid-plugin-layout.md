# 团本资源归类验证（2026-10-26）

团本模板统一保存在 `services/game/resources/plugins/raid/config/`，移除 `native/config/` 和网关 `defaults/` 下的重复模板。两份模板内容逐字节保持不变，加入公共构建输入锁。共享原生二进制及 15 个载荷的锁未改动。

安装器、派生器、正式打包与 Docker COPY 已使用新资源位置。实例运行配置继续位于 `/data/dof-easy/config/`，与原生读取路径一致；已有实例配置不覆盖。团本默认关闭、需要 DP2；普通插件及原生登录的开关含义保持不变。

## 本地检查

- 资源校验：424 个公共输入及 15 个原生载荷通过。
- 单元回归：86 项，84 通过、2 跳过。覆盖安装菜单、部署管理器、四种普通插件组合、团本依赖与默认值、已有配置保留、独立包安装、坏哈希／软链接拒绝、旧资源包布局明确拒绝和原件不覆盖。
- Compose 部署结构检查：6 项全部通过，使用本地 Compose CLI 渲染配置；未启动容器。
- `git diff --check` 通过。

执行命令：

```bash
python3 scripts/container/resources.py
python3 -m unittest tests.unit.test_container_menu tests.unit.test_container_manager tests.unit.test_plugins_runtime tests.unit.test_raid_extension tests.unit.test_bundled_native_assets tests.unit.dof.test_game_deployment
python3 -m unittest tests.structure.test_deploy_layout
```

两项跳过分别为需要显式原始材料的重新派生测试、需要 Node.js 的脚本运行测试。此次未重新派生二进制；已检查正式载荷哈希保持不变。本机无可用 Docker Engine，镜像构建、真实容器部署、原生加载及客户端进游戏均未在本次重新验证。未连接或改动服务器。

布局和使用方式见 [服务端资源布局](../resource-layout.md)。这份记录仅证明本次资源目录及安装／打包回归结果，不代表完整游戏链路验收通过。
