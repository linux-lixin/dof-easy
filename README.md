# dof-easy

Linux x86_64 游戏容器、Java/Vue 管理后台和两种独立登录配套。安装器提供 13 个等级选项，默认 86；当前原生联调范围以 86 级证据为准，其他等级和完整玩家流程需分别验收。`releaseReady=false`。

## 首次使用

准备 Linux 主机、自己的配套 PVF、可用镜像或构建条件，按[环境准备](docs/getting-started/prerequisites.md)核对材料。在仓库根目录运行：

```bash
sudo bash dof.sh
```

菜单默认原生 `native`、本机构建、网页 `0.0.0.0:18080`。高级 `install` 命令默认 `dof`、拉取镜像、网页 `127.0.0.1:18080`，两者不能混读。原生正式派生资源随 Git 提供；原件、PVF、实例密钥和玩家数据不入库。安装后按[首次访问](docs/getting-started/quickstart.md)检查健康、登录后台并确认实例身份。

## 文档与当前范围

- [统一文档入口](docs/README.md)：安装、升级、管理、备份恢复、客户端发布、开发构建六条路径。
- [登录模式选择](docs/getting-started/login-modes.md)：原生配套和 WPF 登录器的边界。
- [当前状态](docs/status/README.md)：部署事实、客户端进度和未完成事项。
- [贡献与必跑检查](CONTRIBUTING.md)、[源码结构](docs/architecture/overview.md)。

后台已部署的构建源码为 `a6ade435`；文档基线为 `ecd4505`。最终镜像与限制以[部署索引](docs/status/deployment.md)为准；合入 main 不代表实际部署或完整替代通过。本次文档整理不部署服务器。

源项目为 [linux-lixin/dof](https://github.com/linux-lixin/dof/tree/60d58715beaa870d0314b5c7fe050bd78cb3f258)，保留[游戏许可证](services/game/LICENSE)、[第三方声明](services/game/THIRD_PARTY_NOTICES.txt)及[上游后台档案](docs/history/upstream/admin/README.md)。第三方资源的原有授权不因本仓库文档而改变。
