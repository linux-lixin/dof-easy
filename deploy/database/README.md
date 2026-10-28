# 内置 MySQL 镜像与兼容种子

用途与读者：维护内置数据库镜像者。适用 internal 模式；external 初始化由[database.py](../../scripts/container/database.py)管理，不启动此镜像。

[Dockerfile](Dockerfile)固定 MySQL 5.6 基础镜像，将 [seed.sql](seed.sql) 与 [compat-tables.sql](compat-tables.sql)按 10/20 顺序放入初始化目录；[my.cnf](my.cnf)提供数据库配置。[generate_compat.py](generate_compat.py)是维护生成工具，改兼容定义后须做隔离 MySQL 5.6 验收，不为升级重灌旧库。

在仓库根目录通过 `bash dof.sh build --database internal` 构建所选镜像套件。前提为 Docker、完整输入与可获取的基础镜像；产物为镜像，无推送。首次空卷启动才消费种子；已有卷不因重启重建。成功应分别核对初始化日志、库身份和业务兼容；失败保留卷与日志，不执行 seed.sql 覆盖玩家库。外置说明见[指南](../../docs/operations/external-mysql.md)。
