# scripts/dof

用途与读者：维护者。适用范围：以正文所列实例和模式为准。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[dof](.)。限制和当前验收边界见[当前状态](../../docs/status/README.md)。

输入与输出：各工具通过 `--help` 列出参数；先阅读所调用脚本，再对隔离副本执行。部署和维护工具可能写入实例、停止容器或生成私有产物；协议探测工具只应指向明确授权的隔离服务。

示例（仓库根目录）：`python scripts/diagnostics/ubuntu_ports.py --help`。不得用此示例替代其他脚本自己的参数。运行条件与分类见[脚本总览](../README.md)。

目录内容：`admin_backup.py`, `admin_inventory.py`, `admin_restore_drill.py`, `audit_admin_tool.py`, `audit_compressed_script.py`, `audit_native_names.py`, `bridge_compat.py`, `build_server_runtime.py`, `client_reader.py`, `compile_admin.py`, `deploy_game_test.py`, `deployment-profile.example.json`, `deployment_profile.py`, `import_assets.py`, `install_client_inputs.py`, `jar_delta.py`, `local_environment.py`, `migrate_local.py`, `native_candidates.py`, `native_equipment.py`, `native_name_contract.py`, `native_residuals.py`, `native_sql.py`, `native_symbols.py`, `original_reference.py`, `package_admin.py`, `package_server.py`, `prepare_isolated.py`, `release_rollback_proxy.py`, `rollback_equipment_batches_proxy.py`, `rollback_receipts_proxy.py`, `run_isolated.py`, `runtime_apply_contract.py`, `space_budget.py`, `stage_server_probe.py`, `verify_admin_baseline.py`.
