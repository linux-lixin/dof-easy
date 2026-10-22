# 来源与资源清单

- `source-lock.json`：三个上游仓库的固定源码基线和当前组件目录；本地修复与目录调整由本仓库 Git 历史记录。
- `game-assets.lock.json`：394 个保留游戏资源的当前路径、上游 URL、大小和 SHA256。
- Ubuntu 额外依赖清单在 [compat-lock.json](../services/game/docker/ubuntu/compat-lock.json)，许可证保留在其同目录。

路径迁移只更新本地 `directory`、`path` 字段；上游 URL、提交、文件内容和原始哈希不因此改写。
资源直接随 Git 提供，不需要资源下载脚本或 Git LFS。校验方式：

```bash
python tests/structure/test_repository_layout.py
```

这不是软件依赖的完整锁定方案：基础镜像、APT、Maven/Npm 构建仍可能联网。
保留游戏端 [LICENSE](../services/game/LICENSE) 与各第三方版权声明；不替未明确授权的上游内容追加新的许可证。

Ubuntu 单平台清理移除了 6 个不再使用的锁定资源（VPN、旧网关/代理等）；保留资源仍沿用原 SHA256。启动脚本的 Ubuntu 适配改动由 Git 记录，不冒称它们与上游原始脚本逐字节一致。
