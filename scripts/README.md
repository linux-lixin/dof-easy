# 仓库工具

工具按职责分类，不放实际数据库密码、运行状态或生成的镜像包。

| 路径 | 用途 |
|---|---|
| `diagnostics/ubuntu_ports.py` | 从配置模板读取大区/频道端口，输出 Markdown 表格 |
| `release/admin-image.sh` | Bash：交互式构建并推送后台镜像 |
| `release/admin-image.ps1` | PowerShell：交互式构建并推送后台镜像 |

```bash
python scripts/diagnostics/ubuntu_ports.py --group 3 --channels 11,52
```

release 脚本使用自身位置确定仓库根目录，可从任意工作目录调用；会提示目标镜像仓库并要求确认推送。
仅需本地构建时按 [组件构建说明](../services/admin/README.md) 执行 `docker build`，不运行 release 脚本。
脚本没有设置平台参数、切换 Buildx builder 或发布业务配置。

资源清单已归类到 [metadata](../metadata/README.md)，自动化验收工具在 [tests](../tests/README.md)。
