# 仓库工具

通用安装器要求 Python 3.9+；原生打包、探针和游戏部署脚本使用 `hashlib.file_digest`，须使用 Python 3.11+。从仓库根目录运行下表中的 Python 入口；参数以各脚本 `--help` 为准。实际凭据、profile、运行状态、二进制和归档留在私有目录。

| 路径 | 用途 |
|---|---|
| `container/manage.py` | 根目录 dof.sh / dof.ps1 调用的通用安装和管理入口 |
| `container/resources.py` | 公共构建输入及等级资源校验 |
| `dof/import_assets.py`、`dof/build_server_runtime.py` | 固定免授权输入导入和确定性 ELF 派生 |
| `dof/package_server.py` | 校验正式资源锁、验收摘要并打包服务端资源 |
| `dof/compile_admin.py`、`dof/package_admin.py` | 编译和打包原生网页后台 |
| `dof/jar_delta.py` | 校验基准及目标摘要的 JAR 差量传输 |
| `dof/prepare_isolated.py`、`dof/run_isolated.py` | 私有目标准备、仅替换网页容器 |
| `dof/deploy_game_test.py` | 独立、限定目标的游戏测试实例部署与恢复 |
| `dof/install_client_inputs.py`、`protocol/dof/prepare_client_inputs.py` | 私有客户端来源校验与安装；不等于 Windows 读取端已适配 |
| `dof/space_budget.py` | 部署和发布的磁盘峰值检查 |
| `dof/audit_native_names.py`、`dof/stage_server_probe.py`、`dof/local_environment.py` | 原生名称审计和本地隔离探测准备 |
| `dof/migrate_local.py` | 显式导入历史本地副本；当前未上线项目不需要数据库迁移 |
| `diagnostics/ubuntu_ports.py` | 从模板生成端口表 |
| `release/admin-image.sh`、`release/admin-image.ps1` | 交互构建并推送通用后台镜像 |

```bash
python3 scripts/container/resources.py
python3 scripts/diagnostics/ubuntu_ports.py --group 3 --channels 11,52
```

原生操作先读 [文档入口](../docs/dof/README.md)。游戏部署与网页部署各有独立保护范围，不能把网页脚本改成通用容器操作入口。部署须使用已合入 main 的版本，不能依赖服务器临时改文件。

release 脚本提示目标仓库并确认推送；只做本地构建时使用 [组件构建说明](../services/admin/README.md)。资源来源见 [metadata](../metadata/README.md)，检查方法见 [tests](../tests/README.md)。
