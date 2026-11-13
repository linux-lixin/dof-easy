# 端口表

以下对应默认频道 `15,52` 和服务器组 3。更改频道后，以实例生成的配置和 `client/qingkong-endpoints.json` 为准。

## 玩家入口

| 用途 | 协议 | 端口 |
|---|---|---|
| 晴空原版网关 | TCP | 8887 |
| 频道列表 | TCP / UDP | 7001 |
| relay | TCP / UDP | 7300 |
| 游戏频道 15、52 | TCP | 30015、30052 |
| 游戏频道 15、52 | UDP | 31015、31052 |
| STUN | UDP | 2311–2313 |

同时检查云安全组、宿主机防火墙和实例端口映射。只测试 TCP 通，不足以确认 UDP 和完整游戏流程。

## 管理连接

| 用途 | 常见端口 | 说明 |
|---|---|---|
| 宝塔 MySQL | TCP 3306 | 按实际数据库端口；游戏容器和获授权的后台工具使用，不是玩家入口 |
| SSH | TCP 22 | 按主机实际配置；服务器运维使用，不是游戏端口 |

后台数据库直连需要单独核对来源授权与防火墙。是否允许公网连接由管理员选择，安装器不会自动开放 Windows 后台账号。连接参数见[Windows 工具](../operations/windows-tools.md)，授权说明见[数据库指南](../operations/external-mysql.md)。本分支不启动 Java/Vue 网页后台。

## 开发排查内部端口

在完整源码工作区运行：

```bash
python3 scripts/diagnostics/ubuntu_ports.py --group 3 --channels 15,52
```

输出包含节点内部通信端口，不应把整个内部端口清单都开放到公网。玩家放行以本页玩家入口和实际实例配置为准。

[返回文档目录](../README.md) · [排错](../operations/troubleshooting.md)
