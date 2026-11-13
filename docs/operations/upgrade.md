# 升级与回退

本流程适用于已经运行的晴空＋外置 MySQL 实例。旧 dof/native 或内置数据库实例不能原地转换，继续使用 [原 main 备份](https://github.com/linux-lixin/dof-easy/tree/backup/main-before-qingkong-20261112)维护。

更新源码不会自动更新正在运行的镜像，也不会重启服务。需要分别完成备份、代码更新、镜像构建和实例切换。

## 1. 记录旧版本并备份

在仓库根目录检查本地修改，记录提交和旧镜像引用：

```bash
git status --short
git rev-parse HEAD
sudo docker inspect dof-qingkong86-game-1 --format '{{.Config.Image}}'
sudo bash dof.sh backup --instance qingkong86
```

有本地修改时先处理差异，不强制覆盖。备份会短暂停服；保留旧源码、旧镜像标签和备份归档，以便回退。镜像容器名称按实际实例名调整。

## 2. 更新代码并构建新标签

确认当前在 main，然后获取最新代码：

```bash
git branch --show-current
sudo git pull --ff-only
```

下面使用本地镜像方式，空的 `--registry` 表示不使用远程镜像仓库。每次升级使用新的标签，不覆盖保留用于回退的旧标签：

```bash
sudo bash dof.sh build --registry '' --tag qingkong-update-1
```

只执行构建不会切换线上容器。构建失败时先解决失败原因，当前运行镜像不需要改动。

## 3. 切换已有实例

安排短暂停服，使用刚构建的同一仓库设置和标签：

```bash
sudo bash dof.sh install --instance qingkong86 \
  --offline --registry '' --tag qingkong-update-1
sudo bash dof.sh status --instance qingkong86
sudo bash dof.sh logs --instance qingkong86 --service game
```

已有实例会沿用等级、PVF、晴空输入、数据库身份和密钥。不传新实例的 `--qingkong-assets`，也不要删除 `.dof-easy` 或重新执行首次建库步骤。启动需要重新加载游戏资源，等待 game 容器健康后再检查后台和客户端。

## 4. 核对或回退

升级后检查网关、频道、数据库连接、密钥一致性与玩家入口。只看到新源码提交不等于新镜像已经运行，应同时确认容器镜像和健康状态。

如果切换失败，保留失败日志与现场，使用记录的旧源码、旧镜像和对应实例配置恢复启动。涉及数据结构变更时，先确认数据兼容性；不要直接覆盖玩家库。当前没有自动完成整套升级回退的命令，也未承诺旧多模式实例迁移。

[返回文档目录](../README.md) · [备份与恢复](backup-restore.md) · [排错](troubleshooting.md)
