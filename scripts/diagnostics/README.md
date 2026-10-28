# 离线诊断与文档检查

用途与读者：开发者和运维者。仓库根目录运行，Python 3.11+；以下工具不连接服务器、不启动服务。

- [ubuntu_ports.py](ubuntu_ports.py)：读取游戏模板和 relay 源码，输出 Markdown 端口表。`python scripts/diagnostics/ubuntu_ports.py --group 3 --channels "15,52"` 为安装器示例；不指定频道默认手动模板 11,52。`--output` 会写指定文件，不修改防火墙。
- [check_docs.py](check_docs.py)：读取 Git 管理的文档、基线对象、来源锁与公开证据，检查链接、图片、锚点、目录、可达性、默认值、证据和私有材料。`python scripts/diagnostics/check_docs.py` 零退出才通过；`--include 相对文件` 可明确纳入尚未 staged 的文件，不递归扫描未提交目录。

文档检查需要 Git 中保留 ecd4505 基线对象；浅克隆应先补齐历史。失败输出文件/规则，按[维护规范](../../docs/development/documentation.md)修正文档或证据指针，不改变历史证据迎合结果。产物仅标准输出；不保存凭据或原始运行材料。
