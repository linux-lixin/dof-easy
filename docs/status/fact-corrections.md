# 事实校准表

用途与读者：文档审查者。适用范围：dof / native；具体差异见正文。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[catalog.json](../catalog.json)；[main-deployment-2026-10-28.json](../validation/main-deployment-2026-10-28.json)。限制和当前验收边界见[当前状态](README.md)。


下表逐项说明本轮改动依据；旧结论保存在历史档案或原 JSON，不改证据。

| 事实 | 本轮校准 | 依据 |
|---|---|---|
| 构建输入数量 | 424 项，游戏资源 392 项，等级包 13 项 | [build-inputs](../../metadata/build-inputs.lock.json)、[assets](../../metadata/game-assets.lock.json)、[levels](../../metadata/levels.lock.json) |
| S46 与签到 | S46 代码已随后续 main 部署；最新签到 200 空样本，旧 409 原因未知 | [HTTP/browser 证据](../validation/main-deployment-2026-10-28.json) |
| 原生读取 | 格式 2 / 配方 2.2，不强制版本字段；真实消费仍待验 | [读取源码](../../scripts/dof/client_reader.py)、[客户端状态](native-client.md) |
| 新建默认 | 菜单 native/build/0.0.0.0；高级 dof/pull/127.0.0.1 | [menu](../../scripts/container/menu.py)、[manage](../../scripts/container/manage.py)、[CLI](../reference/cli.md) |
| 频道 | 安装器 15,52；手动端口模板 11,52 | [管理器](../../scripts/container/manage.py)、[端口生成器](../../scripts/diagnostics/ubuntu_ports.py) |
| 代码与镜像 | 仓库文档基线 ecd4505、应用源码 a6ade435；game 取最终 gameRecreation | [部署页](deployment.md)、[原证据](../validation/main-deployment-2026-10-28.json) |
| 备份与清理 | 单份管理备份，非完整玩家库；旧镜像已删，无自动策略 | [retention/cleanup](../validation/main-deployment-2026-10-28.json) |
| 构建边界 | 标准 Maven package 失败；fresh compile + 专用 packager；game 为只读快照恢复 | [build/gameImageRecovery](../validation/main-deployment-2026-10-28.json)、[打包指南](../development/admin-packaging.md) |
| WPF 更新 | 后台数字版本与哈希元数据不等于 WPF 已消费 | [WPF 源码](../../clients/launcher/MainWindow.xaml.cs)、[兼容表](../reference/clients/compatibility.md) |

保持 releaseReady=false，不新增功能完成台账，不触发服务器部署。所有事实仅覆盖所引证据范围；未完成事项见[功能边界](capabilities.md)。
