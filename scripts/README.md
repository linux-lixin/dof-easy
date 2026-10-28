# 仓库工具

用途与读者：本模块使用者与维护者。适用范围：以正文所列实例和模式为准。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[README.md](https://github.com/linux-lixin/dof-easy/blob/ecd4505d1ebdd28486b13a718cd991874efbe96f/scripts/README.md)。限制和当前验收边界见[当前状态](../docs/status/README.md)。


服务端安装和镜像发布仅提供 Linux Shell 入口，使用 Python 3.11+。从仓库根目录运行下表中的 Python 入口；参数以各脚本 `--help` 为准。实际凭据、profile、运行状态和原始归档留在私有目录，正式插件资源位于 `services/game/resources/`。

| 路径 | 用途 |
|---|---|
| `container/manage.py` | 根目录 dof.sh 调用的通用安装和管理入口 |
| `container/resources.py` | 公共构建输入及等级资源校验 |
| `imports/prepare_assets.py` | 维护者显式导入本地普通插件归档；默认输出私有 `.dof-easy/imported-assets`，正常安装无需执行 |
| `imports/prepare_source.py` | 公共资源校验的早期命令入口，转交 `container/resources.py` |
| `dof/import_assets.py`、`dof/build_server_runtime.py` | 固定免授权输入导入和确定性 ELF 派生 |
| `dof/package_server.py` | 校验正式资源锁、验收摘要并打包服务端资源 |
| `dof/compile_admin.py`、`dof/package_admin.py` | 编译和打包原生网页后台 |
| `dof/jar_delta.py` | 校验基准及目标摘要的 JAR 差量传输 |
| `dof/prepare_isolated.py`、`dof/run_isolated.py` | 私有目标准备、仅替换网页容器 |
| `dof/deploy_game_test.py` | 独立、限定目标的游戏测试实例部署与恢复 |
| `dof/install_client_inputs.py`、`protocol/dof/prepare_client_inputs.py` | 私有客户端来源校验与安装；不等于 Windows 读取端已适配 |
| `dof/space_budget.py` | 部署和发布的磁盘峰值检查 |
| `dof/admin_restore_drill.py` | POSIX 主机将固定哈希的 admin-data 归档恢复到全新私有目录，核对全部文件、元数据和容量；不恢复数据库或覆盖当前实例 |
| `dof/audit_native_names.py`、`dof/stage_server_probe.py`、`dof/local_environment.py` | 原生名称审计和本地隔离探测准备 |
| `dof/migrate_local.py` | 显式导入历史本地副本；当前未上线项目不需要数据库迁移 |
| `diagnostics/ubuntu_ports.py` | 从模板生成端口表 |
| `operations/` | 管理维护脚本；按脚本参数限定目标 |
| `release/admin-image.sh` | 交互构建并推送通用后台镜像 |

```bash
python3 scripts/container/resources.py
python3 scripts/diagnostics/ubuntu_ports.py --group 3 --channels 11,52
```

原生操作先读 [文档入口](../docs/getting-started/login-modes.md)。游戏部署与网页部署各有独立保护范围，不能把网页脚本改成通用容器操作入口。部署须使用已合入 main 的版本，不能依赖服务器临时改文件。

脚本按用途分类，目录不绑定等级或面板。原 `scripts/vipai86/` 已整理为 `imports/`；游戏内普通插件加载器为 `services/game/runtime/ubuntu/plugins.py`，原生网关适配仍在同级 `gateway/`。安装时由统一菜单选择等级、DP2、Frida 和团本扩展。

release 脚本提示目标仓库并确认推送；只做本地构建时使用 [组件构建说明](../services/admin/README.md)。资源来源见 [metadata](../metadata/README.md)，检查方法见 [tests](../tests/README.md)。
