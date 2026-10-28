# 最新已存部署、镜像与保留范围

用途与读者：运维与接手人员。适用范围：2026-10-28 测试服最后已存证据。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[main-deployment-2026-10-28.json](../validation/main-deployment-2026-10-28.json)。限制和当前验收边界见[当前状态](README.md)。


这是 2026-10-28 已存证据，非实时探测。本轮未部署服务器。机器事实见[current.json](current.json)，逐字段引用不可改写的[部署原证据](../validation/main-deployment-2026-10-28.json)。

| 对象 | 当前证据 |
|---|---|
| 文档/仓库审计基线 | `ecd4505d1ebdd28486b13a718cd991874efbe96f` |
| 应用构建源码 | `a6ade435a9037a926e8b0c186b0ab31be6db9159` |
| admin 镜像 | `sha256:f8cf56655a258627717439678ecae04d1e3c3eed0f2accf1defc848453bcfe50` |
| admin 标签 | `dof-easy-admin:main-a6ade435` |
| admin JAR | `c3a81e5d0e7cebb6305926affc26ba3ed811d1ec52e741d60b759b20ad90b97d` |
| 最终 game 镜像 | `sha256:d72a5afccc88137c3283e8ffec931a801a0e5e5e1ea24cb78e4ee8bcc4a76c22` |
| game 标签 | `dof-easy-game:main-a6ade435` |

game 取 `gameRecreation.gameImage` 的最终重建结果，不取较早 `deployment.gameImage`。清理时误删旧现用镜像引用，原进程仍健康；随后从原 S45 不可变只读根文件系统恢复，排除可写层与挂载，保留入口和健康检查、移除烘焙实例身份，核对关键源码并按完整有效环境与挂载实际重建。main 游戏代码未变，但这不是全新源码游戏镜像构建。

后台 root 与 /admin/dof 为 200，匿名管理拒绝，记录目录 30 项；签到 200 空样本，历史 409 原因未知。641 回执、保护表、钱包、密钥与 PVF 保持。浏览器测试用真实接口，零页面错误；未证明有数据分页、原生编码和原工具完整等价。

已清理 40 个旧部署目录、100 标签和 57 个旧镜像 ID，最终保留当前两个镜像。仅一份管理备份（约 1.09 GiB），含 admin-data 与管理/原生配置、钱包 SQL，**不是完整玩家数据库备份**。专用隔离恢复核对 61 个成员与全部文件/元数据，数据库恢复和应用接管未验。旧镜像回滚不可用；本次没有建立自动保留策略。

标准 Maven package 未成功；fresh compile 与固定底座确定性打包、前端类型/生产构建有通过证据。releaseReady、完整替代、玩家验收均 false。操作见[升级](../operations/upgrade.md)和[备份恢复](../operations/backup-restore.md)，不能把历史恢复过程作为通用发布方式。
