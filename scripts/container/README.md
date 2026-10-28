# 安装器内部模块

用途与读者：维护根 dof.sh 的开发者。Linux 部署使用 Python 3.11+；这些模块共用实例目录和锁，不另建部署链。

| 模块 | 输入、输出与影响 |
|---|---|
| [manage.py](manage.py) / [menu.py](menu.py) | CLI/交互选择 → 实例配置、Compose、初始化、服务启停与备份；会修改主机和实例 |
| [resources.py](resources.py) | 元数据锁与缓存 → 文件哈希核对和等级包准备，下载按显式模式 |
| [image_config.py](image_config.py) | 全局/实例仓库与标签 → 镜像引用，不将全局值覆盖已建实例 |
| [database.py](database.py) | 已核对的实例/数据库身份 → 内外置初始化、权限与导出，禁止覆盖已有外置项目库 |
| [deployment.py](deployment.py) | 实例选择 → 部署布局与持久化配置 |
| [host.py](host.py) | 主机状态 → 预算、Docker、Swap 和防火墙处理；按交互确认执行 |

在仓库根用 `bash dof.sh install --help` 查看参数，不在 Windows 导入后试部署。默认值以[CLI 参考](../../docs/reference/cli.md)为唯一说明，操作见[安装](../../docs/operations/install.md)与[维护](../../docs/operations/daily-management.md)。成功需配置、镜像、实例身份与健康相符；失败保留日志和数据，不手工清锁绕过并发保护。
