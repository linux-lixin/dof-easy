# 开发与检查

使用 Python 3.11+；game 镜像的完整检查需要 Linux amd64、Docker/Compose 和完整仓库资源。纯单元测试不应访问线上数据库。

```bash
python3 scripts/diagnostics/check_docs.py
python3 -m unittest discover -s tests/unit -p 'test_*.py'
python3 -m unittest discover -s tests/structure -p 'test_*.py'
git diff --check
sudo bash dof.sh build --tag qingkong-check
```

缺少完整二进制资源、i386 编译器或 Docker 的检查必须明确记为失败或跳过。文档检查验证 Git 管理的链接、导航、目录及 CLI 默认值；新增文档需加入 `docs/catalog.json`。本地设计草稿不发布。

保留原始共享游戏资源和兼容库哈希。晴空私有原包由独立输入清单校验；数据库适配使用 `frida.so`，不得误删或加载已移除的普通玩法脚本。详见[资源目录](../../services/game/resources/README.md)和[测试说明](../../tests/README.md)。
