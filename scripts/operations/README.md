# 显式维护与历史迁移工具

用途与读者：维护已有 dof/WPF 实例的运维者；日常安装和升级不自动调用。

[migrate_launcher_checksum.py](migrate_launcher_checksum.py)只给自有 `dnf_service.CLIENT_LAUNCHER_CONFIG` 与 `CLIENT_LAUNCHER_VERSION` 增加 nullable `SHA256 VARCHAR(64)`。输入 `--instance` 必填，要求安装器管理的内置 db 服务、两个已存在表、管理员容器已停止；external 模式不能照用。

查看参数：`python scripts/operations/migrate_launcher_checksum.py --help`。明确需要迁移时在仓库根目录运行 `python scripts/operations/migrate_launcher_checksum.py --instance main`。工具先用 mysqldump 备份两张表到私有 `.dof-easy/backups/`，再做 DDL；DDL 跨表不原子，部分完成后可核对并重试，不执行破坏性 init.sql。

成功输出 added 或 alreadyCompatible，且两列类型/可空性一致。失败保留备份、停止写入并检查字段状态；不要删除已有表。新增列不代表当前 WPF 已消费 SHA-256，见[兼容性](../../docs/reference/clients/compatibility.md)。
