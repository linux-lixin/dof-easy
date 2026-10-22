# 测试入口

在仓库根目录使用 Python 3.9 或更新版本。不要对整个 `tests` 递归执行 discovery：集成脚本有独立参数和容器生命周期，必须显式调用。

从 dof-easy 源码检出后，先运行 `python scripts/vipai86/prepare_source.py` 恢复固定摘要资源。Frida 的两项原生测试还需要 Linux 的 `gcc-multilib`；没有 gcc 的精简运行镜像会跳过，镜像构建阶段仍执行原生 Frida 检查。历史结构检查针对上游通用部署布局，不能替代 `deploy/vipai86` 的部署验收。

| 目录 | 范围 | 环境 |
|---|---|---|
| `unit/` | 启动、配置、SQL 修复的离线回归 | Python、Bash；Windows 可用 Git for Windows |
| `structure/` | 资源哈希、构建输入、文档链接、5 个部署模板 | Python、Git、Docker Compose；不启动服务 |
| `integration/` | 真实数据库及镜像启动验收 | Docker、被测镜像和固定 MySQL 测试镜像 |

## 离线和结构检查

```sh
python -m unittest discover -s tests/unit -p "test_*.py"
python -m unittest discover -s tests/structure -p "test_*.py"
```

## 数据库与镜像验收

```sh
python tests/integration/test_mysql56_fixes.py
python tests/integration/test_built_images.py --game-image dnf-game:ubuntu24.04 --admin-image dnf-admin:latest --mode all --state-file dist/ubuntu-all-state.json --stable-seconds 1800
python tests/integration/test_built_images.py --game-image dnf-game:ubuntu24.04 --admin-image dnf-admin:latest --mode split --state-file dist/ubuntu-split-state.json --stable-seconds 1800
```

每次镜像验收使用独立网络、卷、容器名称和测试凭据；测试 MySQL 仅模拟外部数据库。完成后按对应状态文件清理：

```sh
python tests/integration/test_built_images.py --state-file dist/ubuntu-all-state.json --cleanup
python tests/integration/test_built_images.py --state-file dist/ubuntu-split-state.json --cleanup
```

`dist/` 中的状态文件和原始日志可能包含测试凭据，禁止提交。清理仅针对状态文件记录的测试资源；不要用于生产实例。自动化通过不代表真实 Windows 客户端登录、进图、组队或更新已经验收。

初始化测试共用 `deploy/shared/bootstrap-admin.sql.example`。维护要求见[贡献说明](../CONTRIBUTING.md)。
