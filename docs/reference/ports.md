# 端口

以下为默认频道 `15,52` 和当前晴空配套的玩家入口。更改频道后按生成的 Compose 配置和实例 `client/qingkong-endpoints.json` 核对实际值。

| 用途 | 协议 | 端口 |
|---|---|---|
| 晴空原版网关 | TCP | 8887 |
| 频道列表、relay | TCP/UDP | 7001、7300 |
| 游戏频道 | TCP | 30015、30052 |
| 游戏频道 | UDP | 31015、31052 |
| STUN | UDP | 2311–2313 |

宝塔数据库的 3306 只允许需要连接的可信 Docker 网段，不作为玩家入口。Windows 晴空后台按原包说明连接，仓库不会启动 Java/Vue 网页端口。

节点内部端口可由 `python3 scripts/diagnostics/ubuntu_ports.py --group 3 --channels 15,52` 从游戏配置生成；不要将内部端口全部放到公网。云安全组和宿主机防火墙需共同配置。
