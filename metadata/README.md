# 来源与资源清单

- `source-lock.json`：三个上游仓库的固定源码基线和当前组件目录；本地修复与目录调整由本仓库 Git 历史记录。
- `game-assets.lock.json`：392 个随 Git 提供的公共游戏资源，记录路径、原始来源和摘要。已去掉不用的模板 PVF 归档及固定等级程序。来源 URL 不作为下载入口。
- `build-inputs.lock.json`：424 个仓库内构建输入的大小与 SHA-256，包括公共资源、已处理的插件和数据库初始化文件。
- `levels.lock.json`：13 个等级包的 Release 下载地址、归档与程序的大小和 SHA256。
- Ubuntu 额外依赖清单在 [compat-lock.json](../services/game/docker/ubuntu/compat-lock.json)，许可证保留在其同目录。

路径迁移只更新本地 `directory`、`path` 字段；上游 URL、提交、文件内容和原始哈希不因此改写。
公共输入直接随 Git 提供。下面的校验离线可执行，不下载、不还原、不覆盖本地文件：

```bash
python scripts/container/resources.py
```

这不是软件依赖的完整锁定方案：基础镜像、APT、Maven/Npm 构建仍可能联网。
保留游戏端 [LICENSE](../services/game/LICENSE) 与各第三方版权声明；不替未明确授权的上游内容追加新的许可证。

修改仓库内输入时，维护者须复核并更新对应摘要；修改插件还需更新 `services/game/plugin-assets/manifest.json` 中的文件记录。构建脚本不自动信任新内容。操作者的插件修改放在实例运行目录，不受源码构建摘要检查影响。

旧 `vipai86-inputs.lock.json` 已撤出当前入口，原始归档校验记录可在 Git 历史和旧 Release 清单查阅。分配理由与兼容策略见 [资源布局](../docs/resource-layout.md)。

Ubuntu 单平台清理移除了 6 个不再使用的锁定资源（VPN、旧网关/代理等）；保留资源仍沿用原 SHA256。启动脚本的 Ubuntu 适配改动由 Git 记录，不冒称它们与上游原始脚本逐字节一致。
