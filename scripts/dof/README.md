# 原生资源、隔离部署与恢复工具

用途与读者：native 维护者与验收者。Python 3.11+，在仓库根目录运行；真实容器操作要求 Linux、显式私有 profile 和已核对实例身份。

| 分类 | 主要入口 | 输入、输出与副作用 |
|---|---|---|
| 来源与资源派生 | import_assets.py / build_server_runtime.py / package_server.py | 固定私有原件与锁 → 新目录资源/归档；输入输出分离 |
| 后台构建 | compile_admin.py / package_admin.py | 源码、JDK/依赖或固定底座 → 类与 JAR/镜像上下文；不等价于 Maven package |
| 客户端 | client_reader.py / install_client_inputs.py | 固定原件或输入归档 → 派生读取器或实例发布材料；核对 UUID、公钥、来源 |
| 隔离部署 | prepare_isolated.py / run_isolated.py / deploy_game_test.py | 显式 profile → 私有身份/配置或目标容器重建；不能交叉指向玩家实例 |
| 归档与恢复 | admin_backup.py / admin_restore_drill.py | 管理归档、固定 SHA 与新隔离目录 → 文件/元数据校验；不做完整玩家数据库恢复 |
| 协议/审计 | audit_* / native_* / stage_server_probe.py | 逐工具指定来源和隔离对象 → 范围有限的诊断；不得把目录当一套可批量执行命令 |

只查看参数的例子：`python scripts/dof/package_server.py --help`。实际调用按[资源派生](../../docs/development/native-resource-derivation.md)、[后台打包](../../docs/development/admin-packaging.md)、[隔离后台](../../docs/operations/isolated-admin.md)、[备份恢复](../../docs/operations/backup-restore.md)执行；不同归档不能混用恢复工具。

成功应同时记录输入来源、产物哈希、目标身份和所验证层级。失败保留恢复点和未决状态，不重放写入或重跑首次初始化。密码、profile、捕获、归档、客户端与 PVF 全部留私有目录。
