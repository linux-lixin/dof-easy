# 结构与资源测试

在完整仓库根目录执行：

```bash
python3 -m unittest discover -s tests/structure -p 'test_*.py'
```

需要完整资源和 Docker Compose。检查共享资源哈希、镜像 COPY 输入、基础 Compose 的 game/keys 服务，以及旧产品不再参与部署。只解析配置，不启动游戏或连接线上数据库。

稀疏检出可用于文字文档编辑，不能代替完整资源检查。

[测试入口](../README.md) · [开发维护](../../docs/development/README.md)
