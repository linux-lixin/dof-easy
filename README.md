# dof-easy · 晴空原版服务端

main 维护 **晴空原版登录器、网关、插件及 Windows 后台**配套。默认 86 级、宝塔已有的外置 MySQL 5.6，Docker 只构建和运行游戏服务及初始化工具。晴空原包、PVF 和完整客户端需要自行提供。

```bash
sudo bash dof.sh
```

菜单会要求填写公网 IP、匹配的 PVF、晴空原包目录，以及宝塔数据库连接。晴空目录默认 `/opt/dof-inputs/qingkong-original`。首次使用本分支请选择源码构建；尚未发布可保证包含本次修改的新镜像标签。

- [部署教程](docs/operations/qingkong-baota.md)：原包目录、安装命令和客户端配置。
- [文档目录](docs/README.md)：数据库、备份、升级、排错、命令和端口。
- [验证边界](docs/status/README.md)：服务器检查与 Windows 游戏验收的区别。
- [贡献说明](CONTRIBUTING.md)：测试和提交约定。

本分支已移除旧 WPF/派生登录器、Java/Vue 网页后台、DP2 与普通 Frida 玩法插件及其专用工具。保留的 `frida.so` 仅用于晴空数据库凭据兼容，原版晴空二进制不改名、不修改协议。旧代码保存在 [原 main 备份](https://github.com/linux-lixin/dof-easy/tree/backup/main-before-qingkong-20261112)；此前同时支持多种模式的版本保存在 [晴空接入分支](https://github.com/linux-lixin/dof-easy/tree/qingkong-baota-20261112)。旧 dof/native 实例应继续使用对应旧分支，不能原地切换为晴空。

资源来源及许可证见 [metadata](metadata/README.md)、[游戏许可证](services/game/LICENSE)和[第三方说明](services/game/THIRD_PARTY_NOTICES.txt)。
