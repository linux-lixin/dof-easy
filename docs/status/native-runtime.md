# 原生服务端运行边界

用途与读者：服务端开发与验收者。适用范围：86 级 native 已存证据。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[server-runtime-validation.json](../dof/server-runtime-validation.json)；[main-deployment-2026-10-28.json](../validation/main-deployment-2026-10-28.json)；[README.md](../../services/game/resources/native/README.md)。限制和当前验收边界见[当前状态](README.md)。


86 级固定来源资源已有真实进程、网关、插件加载与配置证据。当前游戏最终镜像取[部署页](deployment.md)，源码、镜像与加载证据分别记录。公共原生派生资源随 Git，重新派生原件仍需来源锁验证。

配置保存和网页任务完成不代表游戏刷新；`<dof>`、dofRobotc、dof_config 等协议实现与组件消费必须分开验收。操作定义见[服务端协议](../reference/native/server-protocol.md)，组件职责见[执行归属](../architecture/execution-ownership.md)。

真实普通账号进服、游戏完整周期、P2P、广播/随机盒与团本流程未全部完成；`releaseReady=false`。此前 Linux 进程和端口健康不能代替 Windows 客户端功能。历史 runtime JSON 保持原对象与范围，不改写成当前通过结果。本轮未部署或重跑服务端。
