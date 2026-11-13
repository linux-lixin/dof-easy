# 结构测试

`python3 -m unittest discover -s tests/structure -p 'test_*.py'`

需要完整 Git 工作区和 Docker Compose。检查保留资源的哈希、镜像 COPY 来源、只有 game/keys 的基础 Compose，以及已移除产品不再参与部署。检查只解析配置，不启动游戏或连接数据库。
