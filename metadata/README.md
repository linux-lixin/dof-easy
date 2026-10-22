# 来源与资源清单

- `source-lock.json`：三个上游仓库的固定源码基线和当前组件目录；本地修复与目录调整由本仓库 Git 历史记录。
- `game-assets.lock.json`：394 个保留游戏资源的当前路径、上游来源 URL、大小和 SHA256；用于来源记录，不作为安装下载入口。
- `vipai86-inputs.lock.json`：公共构建输入及历史 86 级入口所需资源，下载地址固定到本仓库 `resources-v1` Release。
- `levels.lock.json`：13 个等级包的 Release 下载地址、归档与程序的大小和 SHA256。
- Ubuntu 额外依赖清单在 [compat-lock.json](../services/game/docker/ubuntu/compat-lock.json)，许可证保留在其同目录。

路径迁移只更新本地 `directory`、`path` 字段；上游 URL、提交、文件内容和原始哈希不因此改写。
源码随 Git 提供，外部资源由一键脚本从 [resources-v1](https://github.com/linux-lixin/dof-easy/releases/tag/resources-v1) 下载后校验并恢复到构建目录；不访问记录中的原始下载源。Release 附带 `resources-manifest.json` 和 `SHA256SUMS`，原文件内容与哈希保持不变。恢复资源后可检查目录布局：

```bash
python tests/structure/test_repository_layout.py
```

这不是软件依赖的完整锁定方案：基础镜像、APT、Maven/Npm 构建仍可能联网。
保留游戏端 [LICENSE](../services/game/LICENSE) 与各第三方版权声明；不替未明确授权的上游内容追加新的许可证。

Ubuntu 单平台清理移除了 6 个不再使用的锁定资源（VPN、旧网关/代理等）；保留资源仍沿用原 SHA256。启动脚本的 Ubuntu 适配改动由 Git 记录，不冒称它们与上游原始脚本逐字节一致。
