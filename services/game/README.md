# Ubuntu 游戏镜像

本服务运行原版晴空网关、插件与游戏进程，使用宝塔等外置 MySQL 5.6。输入由[安装器](../../scripts/container/README.md)准备，不直接执行底层脚本部署。

- assets：原始共享游戏程序、模板与初始化种子。
- runtime/ubuntu：Ubuntu 兼容层、进程管理、数据库身份与晴空包装器。
- docker/ubuntu：镜像构建与固定兼容库来源。
- [resources](resources/README.md)：晴空凭据适配所需的 Frida Gadget。

原版 QK、插件和依赖由用户私下提供，安装时严格校验。保留[LICENSE](LICENSE)和[第三方说明](THIRD_PARTY_NOTICES.txt)；资源哈希见[metadata](../../metadata/README.md)。
