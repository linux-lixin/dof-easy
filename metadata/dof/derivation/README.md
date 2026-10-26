# 原生派生规则

此目录只保存显式导入工具执行改名适配所必需的固定哈希、调用点和字符串修改规则，不保存原始程序或派生转储。正常部署使用仓库正式资源，无需运行派生工具。

维护者从自己的本地备份提供原件：

```bash
python3 scripts/dof/import_assets.py --source /local/private/native-inputs --output /local/build/dof-runtime
```

派生输出仍应符合 `services/game/runtime/ubuntu/gateway/runtime.lock.json`。工具生成的本地诊断记录不需要提交仓库。测试原件通过 `DOF_NATIVE_SAMPLE_DIR` 显式提供；缺少时只跳过依赖原件的复现测试，不影响正式安装及打包测试。
