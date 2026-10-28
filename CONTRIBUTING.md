# 开发与贡献

读者：贡献者。适用：所有模式，在仓库根目录执行。先读[架构](docs/architecture/overview.md)、[当前边界](docs/status/README.md)与[文档维护规范](docs/development/documentation.md)。

从最新 main 建立短期 `codex/` 分支，保留其他工作区的未提交文件。按作用拆分提交，经 PR 检查后合并；确认提交进入 main 后删除已合并短期分支。部署必须单独记录源码提交、应用构建提交与实际镜像，文档修改不触发部署。

## 检查

Python 文档检查使用 3.11+，不依赖 Docker，不访问服务器：

```bash
python scripts/diagnostics/check_docs.py
python -m unittest discover -s tests/unit -p 'test_docs_checker.py'
git diff --check
```

业务、构建与部署脚本修改按范围执行：

```bash
python -m unittest discover -s tests/unit -p 'test_*.py'
python -m unittest discover -s tests/unit/dof -p 'test_*.py'
python -m unittest discover -s tests/structure -p 'test_*.py'
```

结构测试的 Compose 解析需要 Docker Compose；集成条件与临时卷清理见[测试总览](tests/README.md)。SQL 修改须跑隔离 MySQL 5.6 契约，Java/Vue/WPF 命令及产物见[开发目录](docs/development/README.md)。缺少私有输入或环境的项记为跳过，不能报告通过。不同历史批次数不相加。

## 提交规则

不得提交 `.env`、实例运行目录、私钥、玩家数据库、原始日志、客户端包、PVF、镜像归档、捕获样本及本地凭据。`services/game/runtime/ubuntu` 是源码，不是部署产物。正式派生资源可按既定锁提交；原始输入保留私有。保留执行权限、许可证和资源哈希；目录调整不能修改业务资源来使检查通过。

提交前审查 staged 文件与 `git diff --check`。文档工具仅检查 Git 管理及明确纳入的文件；不要递归扫描其他人的未提交文件。新增文档必须加入 `docs/catalog.json` 的 pages 并从目录首页可达。更改派生规则后，旧客户端消费证据不自动沿用；`releaseReady` 默认 false，完成情况只更新原功能台账。
