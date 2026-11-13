# 隔离数据库验证

`python3 tests/integration/test_mysql56_fixes.py` 使用独立名称、无外部网络和无发布端口的临时 MySQL 5.6 容器，验证游戏 SQL 种子的 q_id 修复、历史引用保护和月表跨年创建。需要 Docker 与对应测试镜像；结束时清理本次测试容器。

此脚本不操作宝塔或线上实例。完整晴空服务器与 Windows 客户端流程需要匹配私有原包及单独验收，边界见[状态](../../docs/status/README.md)。
