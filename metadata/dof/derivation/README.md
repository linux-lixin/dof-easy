# 原生派生规则

用途与读者：本模块使用者与维护者。适用范围：以正文所列实例和模式为准。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[README.md](https://github.com/linux-lixin/dof-easy/blob/ecd4505d1ebdd28486b13a718cd991874efbe96f/metadata/dof/derivation/README.md)。限制和当前验收边界见[当前状态](../../../docs/status/README.md)。


此目录只保存显式导入工具执行改名适配所必需的固定哈希、调用点和字符串修改规则，不保存原始程序或派生转储。正常部署使用仓库正式资源，无需运行派生工具。

维护者从自己的本地备份提供原件：

```bash
python3 scripts/dof/import_assets.py --source /local/private/native-inputs --output /local/build/dof-runtime
```

派生输出仍应符合 `services/game/runtime/ubuntu/gateway/runtime.lock.json`。工具生成的本地诊断记录不需要提交仓库。测试原件通过 `DOF_NATIVE_SAMPLE_DIR` 显式提供；缺少时只跳过依赖原件的复现测试，不影响正式安装及打包测试。
