# 原生服务端资源导入

仅接受 `metadata/dof/source-audit/server-inputs/replacement.lock.json` 锁定的免授权源文件。旧名称只用于识别显式导入材料，不会作为运行时回退路径。输入与输出必须是不同的真实本地目录。

```bash
python3 scripts/dof/import_assets.py --source /local/source/server --output /local/build/dof-runtime
python3 scripts/dof/package_server.py --runtime /local/build/dof-runtime --validation docs/dof/server-runtime-validation.json --output /local/build/dof-server-runtime.tar.gz
```

派生后资源必须与 `services/game/runtime/ubuntu/gateway/runtime.lock.json` 完全一致；输入不匹配会拒绝构建。包内保留第三方来源和版权说明，不包含私钥、数据库、Windows 客户端或原管理工具。此包为资源包；游戏镜像仍须从同一 main 提交构建。

安装器使用 `--launcher native --dof-assets /local/build/dof-runtime`，现有 `dof` 登录模式保持原意。先阅读 [验收状态](server-runtime-validation.md)。首次合并只提供可复现的服务端改造与本地检查，网页契约及 Windows 读取端需继续成套适配，完整联调通过前不能宣布发布就绪。
