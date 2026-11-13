# 来源和资源锁

- [source-lock.json](source-lock.json)：保留的游戏服务上游来源。
- [build-inputs.lock.json](build-inputs.lock.json)：参与构建的共享资源，校验大小和 SHA-256。
- [game-assets.lock.json](game-assets.lock.json)：原始游戏资源清单，删除旧产品不修改这些资源。
- [levels.lock.json](levels.lock.json)：外置等级资源包及哈希，默认使用 86 级；其他等级需要独立配套验证。
- [晴空输入清单](../services/game/runtime/ubuntu/qingkong/inputs.lock.json)：用户私下提供的固定原版组件。

旧登录器和 Java/Vue 的来源及派生记录随旧分支保存。本分支不再打包其产物。
