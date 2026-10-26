# 本地材料导入工具

仅供维护者从自己保存的原始归档准备候选材料，**新安装不需要执行这些脚本**。仓库正式插件位于 `services/game/resources/plugins/`、原生材料位于 `services/game/resources/native/`，由 [统一安装器](../../docs/container-installer.md) 按需启用。

- `prepare_assets.py --source <本地归档目录>`：校验来源清单和归档路径，默认生成 `.dof-easy/imported-assets/`。普通 DP2/Frida 可使用 `--plugins-only`；完整导入保留其原有 86 级来源选择，不替代通用等级选择。种子输出为 `seed/seed.sql`。
- `prepare_source.py`：转交公共资源校验入口。新命令直接使用 `python3 scripts/container/resources.py`。

原始归档不改名、不改内容、不提交仓库。候选导入结果不能直接覆盖正式资源；发布时更新对应锁文件并完成受影响检查。
