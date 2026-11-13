# dof-easy · 晴空原版服务端

在 Linux 上用 Docker 部署晴空原版网关、插件和游戏服务，连接宝塔已有的 MySQL 5.6。默认 86 级，使用晴空原版 Windows 后台和登录器。

你需要自行准备匹配的晴空原包、PVF 和完整游戏客户端。仓库提供部署工具与兼容层，不包含这些私有材料。

## 从这里开始

| 你要做什么 | 阅读入口 |
|---|---|
| 第一次部署服务端 | [安装教程](docs/operations/qingkong-baota.md) |
| 连接晴空后台、配置登录器 | [Windows 后台与登录器](docs/operations/windows-tools.md) |
| 启停、查看日志、找配置文件 | [日常管理](docs/operations/daily-management.md) |
| 更新现有服务端 | [升级与回退](docs/operations/upgrade.md) |
| 连接失败或游戏无法进入 | [排错指南](docs/operations/troubleshooting.md) |
| 修改代码或了解目录结构 | [开发维护](docs/development/README.md) |

已经准备好 Linux 主机和材料后，在仓库根目录运行：

```bash
sudo bash dof.sh
```

首次部署按[安装教程](docs/operations/qingkong-baota.md)选择源码构建，并填写宝塔数据库连接。已有实例请走升级流程，不要删除运行数据重新安装。

## 当前分支的范围

main 只维护晴空原版配套和外置数据库。Docker 运行游戏服务及初始化工具；Windows 晴空后台单独运行。旧 WPF/派生登录器、Java/Vue 网页后台、内置数据库及普通 DP2/Frida 玩法已移除。保留的 `frida.so` 用于晴空数据库凭据兼容，不是额外玩法插件。

旧 dof/native 实例继续使用 [原 main 备份](https://github.com/linux-lixin/dof-easy/tree/backup/main-before-qingkong-20261112)，不能原地切换为晴空。当前检查结果及未完成的客户端测试见[验证状态](docs/status/README.md)。

[完整文档目录](docs/README.md) · [贡献说明](CONTRIBUTING.md) · [资源来源](metadata/README.md) · [游戏许可证](services/game/LICENSE) · [第三方说明](services/game/THIRD_PARTY_NOTICES.txt)
