# 端口参考

用途与读者：运维与开发人员。适用范围：dof / native；具体差异见正文。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[ubuntu_ports.py](../../scripts/diagnostics/ubuntu_ports.py)。限制和当前验收边界见[当前状态](../status/README.md)。

以下表格是手动模板的 group=3、channels=11,52 示例。安装器默认 channels=15,52，必须显式传给生成器；不可直接照搬手动表作为安装器安全组。运行 `python scripts/diagnostics/ubuntu_ports.py --group 3 --channels "15,52"` 获取对应清单。

# Ubuntu 角色端口表

由 `python3 scripts/diagnostics/ubuntu_ports.py --group 3 --channels 11,52` 从配置生成。

节点端口仅向可信节点开放；管理、容器内部端口见部署指南。

| 角色 | 用途 | 协议 | 端口 | 配置来源 |
|---|---|---|---|---|
| CORE | 玩家频道列表 | TCP/UDP | 7001 | channel/cfg/server.cfg |
| CORE | 节点 bridge | TCP/UDP | 7000 | bridge/cfg/server.cfg |
| CORE | 节点 monitor | TCP/UDP | 33303 | monitor/cfg/server.cfg |
| CORE | 节点 guild | TCP/UDP | 33403 | guild/cfg/server.cfg |
| CORE | 节点 statistic | UDP | 33503 | statics/cfg/server.cfg |
| CORE | 节点 auction | TCP/UDP | 33803 | auction/cfg/server.cfg |
| CORE | 节点 point | TCP/UDP | 33603 | point/cfg/server.cfg |
| CORE | 节点 community | TCP | 33100 | community/cfg/server.cfg |
| P2P | 玩家 relay | TCP/UDP | 7300 | relay/cfg/relay.cfg |
| GAME | 玩家频道 11 | TCP | 30011 | game/cfg/server.template |
| GAME | 玩家频道 11 | UDP | 31011 | game/cfg/server.template |
| GAME | 玩家频道 52 | TCP | 30052 | game/cfg/server.template |
| GAME | 玩家频道 52 | UDP | 31052 | game/cfg/server.template |
| P2P | 玩家 STUN | UDP | 2311、2312、2313 | stun/cfg/server.cfg |
| CORE | 节点 doublecheck | UDP | 33703 | game/cfg/server.template |
| CORE | 节点 relay 注册 | TCP | 5003 | ubuntu/relay.py |
