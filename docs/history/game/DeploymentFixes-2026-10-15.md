> 历史记录：描述当时的分析、路径和验收范围；当前部署请从仓库 README 与 deploy 入口开始。

# 日志初始化修复（2026-10-15）

`build/dnf_data/home/template/init/prepare_login_seed.sh` 在首次导入前修正已知原始 taiwan_login.sql 中 log_query_ref 的耗尽编号。它保留 394 条查询和 SMALLINT 类型，将已知查询 ID 65535 调整到 50939，自增值改为 50940。自定义种子不自动修改；原始 init_sql.tgz 保持不变，转换脚本便于审查。

`init.sh` 在解压后、数据库初始化前调用该脚本；已有数据库不会被重新导入。已存在的原始错误数据可离线运行 `repair_existing_log_ref.sh`，必须停止全部相关游戏频道并设置 GAME_CHANNELS_STOPPED=yes，工具只处理已知且无关联引用的原始数据。

`daily_job/response_time_tables.sh` 根据实际日期创建当月及下月日志表，使用 log_response_time_201604 原始结构；重复执行不删除表。日期计算失败会退出。daily_job.sh 定期调用，不覆盖用户自定义脚本。

本地集成目录的 deploy/*/fixes 为固定旧镜像提供兼容覆盖；不要将本分支完整 init.sh 覆盖到旧镜像。代码修改需重建镜像或使用部署覆盖才会生效。

验证：MySQL 5.6.51 隔离容器中复现 1467，确认修复后可分配 50940、保留原始记录、拒绝改动被引用的编号、重复执行安全，12 月和次年 1 月月表均可创建。
