# 测试入口

用途与读者：本模块使用者与维护者。适用范围：以正文所列实例和模式为准。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[README.md](https://github.com/linux-lixin/dof-easy/blob/ecd4505d1ebdd28486b13a718cd991874efbe96f/tests/README.md)。限制和当前验收边界见[当前状态](../docs/status/README.md)。


最新证据及待验证项见 [当前状态](../docs/status/README.md)。默认模式、原生加载、网页发布与玩家实际流程分别报告；私有样本缺失导致的跳过不能计为通过。下列命令是检查入口，不表示本次文档更新重跑了这些检查。

通用检查使用 Python 3.9+，原生 `tests/unit/dof` 及打包／部署检查使用 Python 3.11+。在仓库根目录执行。不要对整个 `tests` 递归执行 discovery：集成脚本有独立参数和容器生命周期，必须显式调用。

从 dof-easy 源码检出后，公共资源已齐全，可运行 `python scripts/container/resources.py` 离线校验，无需下载归档。Frida 的两项原生测试还需要 Linux 的 `gcc-multilib`；没有 gcc 的精简运行镜像会跳过，镜像构建阶段仍执行原生 Frida 检查。历史部署布局检查针对上游旧入口，不能替代根目录通用容器入口的验收。

| 目录 | 范围 | 环境 |
|---|---|---|
| `unit/` | 启动、配置、SQL 修复的离线回归 | Linux、Python、Bash |
| `structure/` | 资源哈希、构建输入、文档链接、5 个部署模板 | Python、Git、Docker Compose；不启动服务 |
| `integration/` | 真实数据库及镜像启动验收 | Docker、被测镜像和固定 MySQL 测试镜像 |

## 离线和结构检查

```sh
python -m unittest discover -s tests/unit -p "test_*.py"
python -m unittest discover -s tests/unit/dof -p "test_*.py"
python -m unittest discover -s tests/structure -p "test_*.py"
```

## 数据库与镜像验收

镜像仓库拉取及 Compose 引用集成检查（需要本地已有 `dof-easy-game:local`，也可用 `DOF_REGISTRY_TEST_IMAGE` 指定含 Python 的本地镜像）：

```sh
python tests/integration/test_registry_images.py
```

该检查仅在 Docker 主机网络的回环地址创建临时只读 Registry V2 服务，真实拉取三个空测试镜像并检查 Compose，结束后删除测试容器和测试标签。不上传、构建或启动生产镜像，不改变生产 Compose 的网络或 TLS 配置。

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

## 文档和原生验收摘要

文档改动运行 Git 范围完整校验和检查器故障回归，不启动整套实例；文档检查使用 Python 3.11+：

```sh
python scripts/diagnostics/check_docs.py
python -m unittest discover -s tests/unit -p 'test_docs_checker.py'
python3 -m unittest discover -s tests/unit/dof -p 'test_server_runtime_report.py'
```

原生后台 Java 私有样本、隔离 MySQL 和编译要求见 [发布说明](../docs/development/admin-packaging.md)。各历史测试批次有重叠，不把计数相加。Windows 读取、实际登录、奖励入包及团本流程须有对应实际证据，不能由离线合成输入、网页任务状态或进程健康替代。

## 内置／外置 MySQL

数据库模式、初始化边界和备份失败恢复：`python3 -m unittest discover -s tests/unit -p test_database_modes.py`。真实 MySQL 5.6、网页 HTTP 和 Compose 四组合检查见 [本地隔离测试](integration/mysql/README.md)。

菜单式 Shell 入口：`bash dof.sh --help`；交互路由、等级默认／选择、外置密码清理和实例保护测试：`python3 -m unittest discover -s tests/unit -p test_container_menu.py`。这些菜单测试不启动 Docker，也不连接数据库或服务器。

## 容器一键端

```sh
python3 -m unittest tests.unit.test_container_menu tests.unit.test_container_host tests.unit.test_container_manager tests.unit.test_database_modes
bash -n dof.sh scripts/container/bootstrap.sh
```

主机单元测试使用临时目录和模拟命令，绝不修改实际 Swap、防火墙或系统软件。真实主机测试只能在专用可丢弃虚拟机中执行，见 [隔离验收说明](integration/installer/README.md)。
