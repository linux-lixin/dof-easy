# 后台日常操作入口

用途与读者：运维与开发人员。适用范围：已绑定管理后台；操作按模块能力开放。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[DofAdminController.java](../../../services/admin/backend/src/main/java/com/aiyi/game/dnfserver/controller/DofAdminController.java)；[execution-plan.json](../../../services/admin/backend/src/main/resources/dof/execution-plan.json)。限制和当前验收边界见[当前状态](../../status/README.md)。


前提：已部署后台、管理员认证、确认数据库 UUID 与实例绑定。`/admin/dof` 为原生管理入口；模块能力由绑定、结构与权限分别决定，不把登录模式或一个 UI 错误当成全后台能力。

1. 先查询能力和实例信息，只读查看[记录](records.md)。匿名访问必须拒绝。
2. 修改[活动与规则](activities.md)前确认模块、目标、周期和当前版本，创建预览；核对预览后才保存。取消预览与恢复已保存操作是不同动作。
3. 保存后检查回执和回读。`PERSISTED` 仅证明持久化；`READY_FOR_TEST` 仅证明文件准备可测。网页不替代游戏组件消费与游戏流程验收。
4. 结果不明时先 reconcile，再根据回执定向恢复；不要重复提交新编号绕过幂等与未决状态。批量任务的取消、终止与重试链按任务状态处理。

字段、权限与请求定义见[管理 API 参考](../../reference/admin-api.md)，执行归属见[架构](../../architecture/execution-ownership.md)。旧任意 SQL、旧完成任务与旧无编号公告写入口已停用，管理员获得 410，不能用于日常管理。

失败时保留 requestId 与状态，按诊断提示处理，不手工改业务表“消除”回执。实际完整原工具替代尚未完成，范围以[唯一功能台账](../../status/capabilities.md)为准。
