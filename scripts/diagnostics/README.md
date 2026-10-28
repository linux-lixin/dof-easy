# scripts/diagnostics

用途与读者：维护者。适用范围：以正文所列实例和模式为准。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[ubuntu_ports.py](ubuntu_ports.py)。限制和当前验收边界见[当前状态](../../docs/status/README.md)。

输入与输出：各工具通过 `--help` 列出参数；先阅读所调用脚本，再对隔离副本执行。部署和维护工具可能写入实例、停止容器或生成私有产物；协议探测工具只应指向明确授权的隔离服务。

示例（仓库根目录）：`python scripts/diagnostics/ubuntu_ports.py --help`。不得用此示例替代其他脚本自己的参数。运行条件与分类见[脚本总览](../README.md)。

目录内容：`ubuntu_ports.py`.
