# deploy/database

用途与读者：维护者。适用范围：以正文所列实例和模式为准。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[database](.)。限制和当前验收边界见[当前状态](../../docs/status/README.md)。

仅存本职责的共享部署模板与构建配置；实例数据和密码保存在实例目录。选择模板、变量、挂载与启动顺序见[部署总览](../README.md)及[手动部署](../../docs/operations/manual-deployment.md)。先渲染 Compose 检查，再启动；失败时保留日志与卷，不重新初始化已有库。

目录内容：`Dockerfile`, `compat-tables.sql`, `generate_compat.py`, `my.cnf`, `seed.sql`.
