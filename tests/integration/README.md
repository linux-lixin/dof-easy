# 隔离 MySQL 验证

在仓库根目录执行：

```bash
python3 tests/integration/test_mysql56_fixes.py
```

脚本使用独立名称、无外部网络且不发布端口的临时 MySQL 5.6 容器，验证 SQL 种子的 q_id 修复、历史引用保护、幂等性与跨年月表。需要 Docker、完整 SQL 资源和脚本 `IMAGE` 常量指定的固定摘要镜像；首次获取镜像需要联网，测试容器本身禁用网络。脚本结束后清理本次测试容器。

此检查不操作宝塔或线上实例，也不是完整备份恢复演练。涉及实际晴空运行或 Windows 客户端的检查需要配套私有输入，见[验证状态](../../docs/status/README.md)。

[测试入口](../README.md) · [开发维护](../../docs/development/README.md)
