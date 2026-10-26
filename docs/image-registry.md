# 使用自己的镜像仓库

本页是通用四镜像安装器流程，示例默认 `dof` 模式。原生固定输入和正式资源已随 Git 提供，见 [内置原生资源](dof/launcher.md)，现有 86 级游戏和网页使用 [各自限定部署入口](../deploy/README.md)。已记录的私有联调镜像不等于公开镜像仓库发布；版本见 [状态页](dof/status.md)。

镜像仓库参数包含域名、可选端口和命名空间，不包含 `https://`、账号密码或具体镜像名。例如 `ghcr.io/your-name`、`docker.io/your-name`、`harbor.example.com:5000/dnf`。标签默认 `latest`，发布时建议使用明确的版本号，如 `v1`。

外置 MySQL 模式只需要 game、admin、netguard 三个镜像。`build --database external` 跳过 db 镜像，`install --database external` 也不拉取或要求 db 镜像；其他资源和版本一致性要求不变。完整参数见 [宝塔部署指南](baota-deployment.md)。

## 配置与一键安装

```sh
# 私有仓库使用 Docker 自己的登录流程
docker login ghcr.io
bash dof.sh images --registry ghcr.io/your-name --tag v1
bash dof.sh install --level 70 --ip 192.168.1.100 --pvf /data/Script.pvf
```

在 Linux 主机执行上述命令。也可将 `--registry`、`--tag` 直接放到 `install` 命令中，不需要提前保存默认配置。交互菜单首次安装可直接输入镜像地址，高级 `images` 命令可保存默认地址。

默认安装拉取以下四个 Linux amd64 镜像；缺少任何一个或架构不匹配时停止，不执行源码构建、不改写已有实例的镜像配置：

```text
ghcr.io/your-name/dof-easy-db:v1
ghcr.io/your-name/dof-easy-admin:v1
ghcr.io/your-name/dof-easy-game:v1
ghcr.io/your-name/dof-easy-netguard:v1
```

`keys` 任务复用 admin 镜像，`ingress` 复用 game 镜像。备份辅助容器也使用该实例的 game 镜像，不要求机器上另有 `dof-easy-game:local`。

## 构建及自行上传

```sh
# 显式构建并打好你自己的标签；此命令不会上传或启动实例
bash dof.sh build --registry ghcr.io/your-name --tag v1

# 由你执行上传
for component in db admin game netguard; do
  docker push "ghcr.io/your-name/dof-easy-${component}:v1"
done
```

已有 `dof-easy-*:local` 镜像时，可自行用 `docker tag` 重新标记，再上传四个镜像。发布的镜像应由同一版 dof-easy 源码构建。`install --build` 保留“源码构建后安装”的显式入口；未配置仓库时构建本地 `dof-easy-*:local`。

## 配置优先级及已有实例

- `install` 显式传入的 `--registry`、`--tag` 优先。
- 未传入的值使用已有实例保存的 `IMAGE_REGISTRY`、`IMAGE_TAG`。
- 新实例使用本机 `.dof-easy/images.json` 的默认配置。
- 新实例未配置仓库时，普通安装报错；显式源码或离线模式可以使用本地标签。

实例 `.env` 同时保存四个 `DOF_*_IMAGE` 引用。安装器调用 Compose 时强制使用实例配置，避免宿主机同名环境变量意外覆盖镜像选择。`images` 不操作 Docker、不改已有实例；`start`、`restart` 不拉取或构建。旧实例没有镜像字段时继续使用原来的本地标签。

更新已有实例：

```sh
bash dof.sh install --instance main --tag v2
# 如迁移仓库，再加 --registry 新仓库/命名空间
```

配置在四镜像准备成功后更新，重建容器沿用现有卷、密码、PVF、插件修改和日志。可先执行 `backup` 留存更新前的数据；镜像版本变化本身不保证数据库结构可向后回滚。

## 离线模式

```sh
bash dof.sh install --offline --registry ghcr.io/your-name --tag v1 \
  --images /data/images.tar --resources /data/levels \
  --ip 192.168.1.100 --pvf /data/Script.pvf
```

归档应包含对应的四个完整镜像引用。已有本地镜像时省略 `--images`。旧 `:local` 归档使用 `--registry= --tag local`。离线模式不拉取镜像或下载等级包，也不会构建源码。

镜像引用格式和 Docker 拉取行为依据 [Docker tag](https://docs.docker.com/reference/cli/docker/image/tag/) 与 [Docker pull](https://docs.docker.com/reference/cli/docker/image/pull/)。运行 Compose 没有 `build` 定义，管理器启动仍显式使用 `--no-build --pull never`，镜像获取由安装步骤统一处理。
