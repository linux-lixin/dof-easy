# 已有实例升级与版本核对

用途与读者：运维与开发人员。适用范围：dof / native；具体差异见正文。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[manage.py](../../scripts/container/manage.py)；[image_config.py](../../scripts/container/image_config.py)。限制和当前验收边界见[当前状态](../status/README.md)。


适用：由根目录安装器创建的实例。手动 Compose 实例按[手动部署](manual-deployment.md)，隔离测试实例按其专用指南；不能交叉使用路径。升级会重建服务，安排停机窗口。

1. 运行 `bash dof.sh status --instance main`，记录模式、等级、镜像引用、容器与数据库身份。另用 `docker inspect 容器名` 记录实际 Image ID，不能只记录可变标签。
2. 按[备份恢复](backup-restore.md)生成并验证适合本实例的备份。当前测试服只有一份管理备份、无旧镜像回滚；不能将它当作通用升级保障。
3. 在独立检出中核对目标 main 提交与发布镜像来源，镜像三件套使用同一标签；external 不需要 DB 镜像。先验证目标镜像可取和资源齐全。
4. 已发布镜像升级例子（仓库根目录，替换版本）：

```bash
bash dof.sh install --instance main --tag v2
bash dof.sh status --instance main
bash dof.sh logs --instance main --service game
```

实例保留自己的镜像仓库；修改全局 `images` 不会更改已有实例。`start`、`restart`、`backup` 不拉取或构建；`install --tag` 获取所选数据库模式全部镜像成功后才更新配置。源码构建须明确 `--build`，离线使用 `--offline`，两者互斥。不能用 `install --level` 原地换等级；另建新实例且避免端口重叠。

成功信号：目标实际镜像、后台登录、游戏健康、实例 UUID、保护数据与客户端配套均核对。记录源码提交、构建来源、镜像 ID 和验收时间。出现失败先保留日志、配置与卷；已变更数据库时不能仅切旧镜像推断恢复成功。只有已保存且确认兼容的旧镜像和备份才能执行回退；无通用自动数据库回滚。恢复步骤先在隔离副本验证。
