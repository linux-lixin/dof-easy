# 游戏服务源码

该目录构建 Linux amd64 游戏镜像，包含 Ubuntu 兼容层、晴空原版运行包装器和共享游戏资源。部署使用[安装器](../../scripts/container/README.md)，不直接执行底层启动脚本。

| 目录 | 职责 |
|---|---|
| assets | 共享游戏程序、模板和初始化种子 |
| runtime/ubuntu | 进程管理、数据库身份、兼容层和晴空包装器 |
| docker/ubuntu | Dockerfile、固定依赖与兼容性探针 |
| [resources](resources/README.md) | 晴空数据库凭据适配需要的运行组件 |

原版 QK、插件与依赖由用户私下提供，安装时校验固定清单。Windows 后台和完整客户端不在该镜像中。资源记录见[metadata](../../metadata/README.md)，保留[LICENSE](LICENSE)与[第三方说明](THIRD_PARTY_NOTICES.txt)。

[开发维护](../../docs/development/README.md) · [用户安装教程](../../docs/operations/qingkong-baota.md)
