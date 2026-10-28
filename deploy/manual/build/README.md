# Ubuntu 镜像构建

用途与读者：本模块使用者与维护者。适用范围：以正文所列实例和模式为准。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[README.md](https://github.com/linux-lixin/dof-easy/blob/ecd4505d1ebdd28486b13a718cd991874efbe96f/deploy/manual/build/README.md)。限制和当前验收边界见[当前状态](../../../docs/status/README.md)。


适用范围：高级手动 Ubuntu 模板、默认 `dof` 登录链与外置 MySQL。默认一键安装及原生 86 级联调另见 [部署导航](../../README.md)；不要把本页首次初始化步骤用于现有原生测试实例升级。

在 Linux x86_64 服务器执行。源码、游戏资源和兼容依赖由同一 Git 仓库提供；构建仍需访问软件源。

```bash
git clone https://github.com/linux-lixin/dof-easy.git /opt/dof-easy
cd /opt/dof-easy
docker build -f services/game/docker/ubuntu/Dockerfile -t dnf-game:ubuntu24.04 services/game
docker build -f services/admin/docker/Dockerfile -t dnf-admin:latest services/admin
```

游戏镜像统一命名为 `dnf-game:ubuntu24.04`，后台为 `dnf-admin:latest`。这两个标签分别标识游戏基础系统和本次本地构建的后台；构建标签与全部署模板的 `.env.example` 保持一致。Compose 使用 `pull_policy: never`，不会下载同名远端镜像。
Dockerfile 和共享资源保留在 `services/game/`，后台源码保留在 `services/admin/`，不复制两份。
构建完成按 [平台入口](../README.md)选择整体或单独部署目录。
