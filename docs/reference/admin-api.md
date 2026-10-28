# 后台权限、请求和状态定义

用途与读者：接口调用者与开发者。适用范围：各模块管理员接口；须绑定实例。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[DofAdminController.java](../../services/admin/backend/src/main/java/com/aiyi/game/dnfserver/controller/DofAdminController.java)；[ActivityConfigController.java](../../services/admin/backend/src/main/java/com/aiyi/game/dnfserver/controller/ActivityConfigController.java)。限制和当前验收边界见[当前状态](../status/README.md)。


管理接口需要管理员身份，写入还核对绑定数据库 UUID、模块能力及版本。公开 GET 与管理 GET 不是相同权限。所有固定记录查询保持只读；旧 `POST /api/v1/database`、旧 overTasks 与旧无编号 notice 写入返回 410（匿名先拒绝）。具体路由及字段以控制器、请求 DTO 与服务的校验为准。

| 定义 | 含义 | 不代表 |
|---|---|---|
| requestId | 预览与提交共用的请求身份，防止重复写入 | 换编号可以绕过未决状态 |
| revision | 基于当前内容或状态的版本检查 | 任意旧预览都可提交 |
| PERSISTED | 配置保存并回读 | 组件已刷新或奖励已生效 |
| READY_FOR_TEST | 文件/包检查已完成，等待消费验证 | 完整客户端通过 |
| reconcile | 先核对结果不明的动作 | 自动重放所有写入 |
| restore | 基于原回执字段定向恢复 | 任意数据库整表回滚 |

记录视图由固定目录提供，搜索列必须来自视图允许值；page/size 和 hasMore 按接口约束。30 视图是当前部署观测值，不是所有未来版本恒定协议。其前端操作见[记录指南](../operations/admin/records.md)。

## 插件配置请求


入口为 `/admin/dof` 的“团本与插件”。固定模块为 `plugin`、`anton`、`luke`，对应 `plugin.ini`、`anton-raid.ini`、`luke-raid.ini`。

| 接口 | 用途 |
|---|---|
| `GET /api/v1/admin/dof/plugins` | 模块能力、部署配置和不可用原因 |
| `GET /api/v1/admin/dof/plugins/{module}` | 当前字段、文本、编码和版本 |
| `POST /api/v1/admin/dof/plugins/{module}/preview` | 按版本预览字段变更或导入内容 |
| `PUT /api/v1/admin/dof/plugins/{module}` | 保存已经预览的变更 |
| `GET /api/v1/admin/dof/plugins/{module}/history` | 操作历史 |
| `POST /api/v1/admin/dof/plugins/{module}/history/{id}/reconcile` | 核对中断操作 |
| `POST /api/v1/admin/dof/plugins/{module}/history/{id}/restore` | 恢复该操作涉及的字段 |

预览和保存共用 `requestId`、`revision`，并提交 `values` 或导入的 `content`，二者不能混用。恢复使用新的操作编号。所有接口验证管理员权限及数据库实例身份。

变更保留未编辑字段、未知字段、注释及原文件编码。导入只接受当前文件已有的已知字段变更，不使用上传文件覆盖整个原文件。网页导出是 UTF-8 文本；实际保存仍沿用原文件的 UTF-8 或 GB18030 编码。

奖励表、时间、星期、元组和数值在后端检查格式。原模板必须能够完整读回；验证范围不等于原生执行逻辑已通过。自动补全未知配置或直接开放尚未验证的字段不在当前行为内。

每次文件写入先保存 `PREPARED` 恢复记录，再执行完整文件的原子替换，最后标记 `PERSISTED`。写前再次核对原文件哈希；符号链接、重复字段和未知模块拒绝访问。重复请求只返回原结果，不重复改写。恢复只处理原操作涉及的字段，并拒绝覆盖第三方对这些字段的修改。

文件历史保存在该 admin 实例的私有 `plugin-audit/` 目录中，快照不由 HTTP 返回。备份 admin 数据目录时应包含它。


## 源码查阅

账号、邮件、经济、活动和装备批量请求分属各自控制器；不能用统一 SQL 入口替代固定业务。API 参考采用源码链接保持完整字段定义：[控制器目录](../../services/admin/backend/src/main/java/com/aiyi/game/dnfserver/controller)、[服务目录](../../services/admin/backend/src/main/java/com/aiyi/game/dnfserver/service)。未适配结构应返回不可用原因，不能自行造表。完整业务与消费门禁见[功能状态](../status/capabilities.md)。


活动、装备批量和排行榜字段、接口及文件格式见[专项 API 参考](admin-activities.md)。
