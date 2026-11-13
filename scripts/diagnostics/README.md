# 诊断脚本

| 命令 | 检查内容 |
|---|---|
| `python3 scripts/diagnostics/check_docs.py` | Git 管理的文档链接、目录覆盖、导航、CLI 默认值及私有材料 |
| `python3 scripts/diagnostics/ubuntu_ports.py --group 3 --channels 15,52` | 从游戏配置生成内部节点端口表 |

文档检查不联网，不验证外部网站可用性。新增文档先暂存，或通过 `--include 相对路径` 显式纳入检查。

内部端口表包含服务间通信，不直接作为公网放行清单；玩家入口见[端口说明](../../docs/reference/ports.md)。其他检查命令见[开发维护](../../docs/development/README.md)。
