# 备份、隔离恢复与保留范围

用途与读者：运维与开发人员。适用范围：dof / native；具体差异见正文。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[manage.py](../../scripts/container/manage.py)；[database.py](../../scripts/container/database.py)；[admin_restore_drill.py](../../scripts/dof/admin_restore_drill.py)；[main-deployment-2026-10-28.json](../validation/main-deployment-2026-10-28.json)。限制和当前验收边界见[当前状态](../status/README.md)。


必须先区分两类归档：

| 对象 | 创建与范围 | 影响与已知限制 |
|---|---|---|
| 安装器实例备份 | `bash dof.sh backup --instance main`，SQL、实例配置、PVF、密钥、插件、日志 | 停止原本运行的 admin/game，导出后恢复它们；需数据库可用、足够空间和实例 game 镜像；包含凭据 |
| 当前测试服管理备份 | 既有单份 admin-data 与管理/原生配置、钱包 SQL | 不是完整玩家数据库；仅做过文件内容与元数据隔离恢复，未验数据库恢复或应用接管 |

## 安装器备份

在仓库根目录执行 `bash dof.sh backup --instance main`。输出在私有 `.dof-easy/backups/`，成功时得到 tar.gz 与同名 `.sha256`；失败的 `.partial` 不得当作完成。用 `sha256sum -c 文件名.tar.gz.sha256` 核对后将归档与校验文件安全移到独立存储。备份包含密码与私钥，不进 Git。外置模式只导出工具定义的业务库，不能推断包含整台 MySQL 的其他数据库。

## 当前管理备份文件恢复演练

仅 Linux/POSIX；需读归档、足够可用空间（还保留 2 GiB 基础余量）及恢复文件所有权所需权限。目标父目录必须存在，目标目录必须全新，并与现用 admin-data 隔离。先从自己保存的证据读取固定 SHA，不能用待恢复文件临时计算值替代来源核对。

```bash
python3 scripts/dof/admin_restore_drill.py   --archive /private/management-backup.tar.gz   --sha256 已核对的64位小写SHA256   --destination /private/drills/new-restore   --protected-tree /private/live/admin-data
```

上例只接受 admin-data 根结构的管理归档，不能用于安装器 `instance` 归档。工具不修改 live，失败输出保留供调查；成功必须核对报告的内容与元数据验证结果。此前泛用 tar 过滤恢复出现 15 项元数据不符，不能计为通过；后续专用工具验证 61 个成员通过，详见[原始证据](../validation/main-deployment-2026-10-28.json)。演练后先核对目标绝对路径与项目归属，再仅清理该临时恢复目录。

## 完整数据库与服务恢复边界

仓库没有通用 `dof.sh restore` 命令，也没有完整玩家库恢复通过记录。完整灾备须另做：固定备份和镜像 → 创建隔离同版本数据库和服务 → 导入 SQL、还原实例文件及权限 → 核对 UUID、表结构、玩家数据、登录及业务 → 才安排切换。未完成隔离验证前不可覆盖 live，也不可重跑初始化代替恢复。

当前保留只有一份管理备份；旧镜像已清理，无法直接旧镜像回滚；本次没有建立自动保留策略。副本数量、异地备份、数据库恢复与自动策略仍需独立实施和验收，见[当前部署](../status/deployment.md)。
