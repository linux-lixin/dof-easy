# 资源来源与锁定清单

这些清单说明保留资源的来源和校验方式，供构建与审查使用；安装步骤见[安装教程](../docs/operations/qingkong-baota.md)。

| 清单 | 内容 |
|---|---|
| [source-lock.json](source-lock.json) | 游戏服务的上游仓库与基线提交 |
| [build-inputs.lock.json](build-inputs.lock.json) | 395 项共享构建输入的大小与 SHA-256 |
| [game-assets.lock.json](game-assets.lock.json) | 原始共享游戏资源 |
| [levels.lock.json](levels.lock.json) | 外置等级包及其哈希 |
| [晴空输入清单](../services/game/runtime/ubuntu/qingkong/inputs.lock.json) | 私下提供的固定晴空组件 |

默认使用 86 级；目录中存在其他等级资源，不代表其晴空兼容性已经验证。哈希不匹配时核对来源，不直接改锁文件。旧产品的来源与派生记录随原 main 备份保存。

[开发维护](../docs/development/README.md) · [第三方说明](../services/game/THIRD_PARTY_NOTICES.txt)
