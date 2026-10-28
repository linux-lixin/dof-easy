# 插件执行归属

用途与读者：本模块使用者与维护者。适用范围：以正文所列实例和模式为准。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[plugin-ownership.md](https://github.com/linux-lixin/dof-easy/blob/ecd4505d1ebdd28486b13a718cd991874efbe96f/docs/admin/plugin-ownership.md)。限制和当前验收边界见[当前状态](../status/README.md)。


网页保存配置、任务和审计，不新增奖励执行器或活动调度器。`native` 模式由 `dof-gateway`、`libdof-plugin.so`、`libdof-inline.so` 及实例启用的 DP2 / Frida 执行对应职责；默认 `dof` 模式不因此自动启用原生组件。

配置写入成功只表示持久化；刷新时机、实际扣除、奖励和返还要逐项验证。未知原生读取或执行链路保留未验证状态，不通过网页另行发奖冒充原插件成功。未启用玩法不因部署网页自动启用。

装备广播采用项目实现，规则和原生接入见 [广播说明](../reference/native/equipment-broadcast.md)。当前真实加载、派发探针和随机盒查询证据与玩家流程分开，见 [状态页](../status/README.md)。
