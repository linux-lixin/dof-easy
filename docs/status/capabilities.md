# 功能边界与已知问题

用途与读者：项目使用者与后续实施者。适用范围：dof / native；具体差异见正文。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[scope.json](../../services/admin/backend/src/main/resources/dof/scope.json)；[execution-plan.json](../../services/admin/backend/src/main/resources/dof/execution-plan.json)；[main-deployment-2026-10-28.json](../validation/main-deployment-2026-10-28.json)。限制和当前验收边界见[当前状态](README.md)。


功能完成只读原[范围台账](../../services/admin/backend/src/main/resources/dof/scope.json)与[执行台账](../../services/admin/backend/src/main/resources/dof/execution-plan.json)。维持 71 类、239 项，wp01～wp13、R0～R8。页面存在或配置持久化不意味着全量替代通过。

| 能力层 | 当前结论 | 仍需验证 |
|---|---|---|
| 网页管理 | 实例绑定、固定记录、活动/装备配置、回执、PVF 和配套任务等已实现，分批证据见索引 | 原工具全部菜单、默认值、导入导出与完整业务等价 |
| 当前部署 | main 应用构建、真实接口与浏览器有证据 | 有数据分页与原生编码、其余实际业务 |
| 原生客户端 | 格式 2 / 配方 2.2 已实现，派生输入和产物可校验 | 新配方真实读取、加载 DLL 全部消费及正常游戏生命周期 |
| 游戏 | 86 级服务加载、关键配置与进程健康有证据 | 登录、选角、进城、进图、战斗、组队/P2P、团本和奖励 |
| 灾备 | 当前管理归档文件/元数据隔离恢复通过 | 完整玩家数据库恢复、应用恢复与自动保留策略 |

源码与部署分开；格式实现与真实消费分开；历史测试数量不相加。`PERSISTED` 与 `READY_FOR_TEST` 定义见[API 参考](../reference/admin-api.md)。原生私有参数压缩、自动更新消费及完整多等级兼容仍不能宣称完成。

已知限制：Maven 标准 package 未完成；game 为只读快照恢复而非 fresh source build；WPF 字符串版本排序且未消费 SHA-256；仅一份管理备份、旧镜像回退不可用。继续执行入口见[活动计划](../development/active-plan.md)。
