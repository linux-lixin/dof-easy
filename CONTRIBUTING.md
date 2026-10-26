# 开发与维护

先读 [项目结构](docs/architecture.md) 和 [当前状态](docs/dof/status.md)。默认部署入口是根目录 `dof.sh` / `dof.ps1`；手动模板在 `deploy`，应用源码在 `services` 和 `clients`。游戏运行要求 Linux x86_64；原生链另需固定免授权输入，默认 WPF 与原生客户端分别验收。

## 本地检查

在仓库根目录，通用检查使用 Python 3.9+，原生检查使用 Python 3.11+；离线部署修复测试还需 Bash（Windows 使用 Git for Windows）：

```bash
python -m unittest discover -s tests/unit -p "test_*.py"
python -m unittest discover -s tests/unit/dof -p "test_*.py"
python -m unittest discover -s tests/structure -p "test_*.py"
```

structure 中的 Compose 检查需要 Docker Compose，但不会启动服务。容器集成验收另按 [测试文档](tests/README.md)执行，
使用独立临时数据库、网络和卷，不把生产 `.env` 交给测试脚本。

## 构建与改动边界

游戏和后台构建入口见 [游戏](services/game/README.md)、[后台](services/admin/README.md)；
Windows 登录器见 [launcher](clients/launcher/README.md)。只改文档时验证链接即可；改构建路径时必须核对 Docker COPY 与构建上下文。
SQL 初始化改动需跑 MySQL 5.6 集成测试，平台运行脚本改动需跑对应镜像测试。

`services/game/assets` 现在只供 Ubuntu 构建使用。启动与初始化脚本直接维护源码，不再维护旧平台转换逻辑或补丁副本。修改游戏二进制或兼容文件时必须记录来源和对应哈希；目录整理不能改变资源内容来通过检查。
不要更改容器内部路径来配合宿主机源码目录重排，除非明确实施运行时迁移。

## 提交范围

保留脚本执行权限、资源哈希和第三方许可证。提交前运行 `git diff --check`，检查 staged 文件清单。
`.env`、部署 `runtime/`、后台私钥、数据库数据、原始运行日志、客户端发行包、镜像 tar 和测试状态文件不提交。
`services/game/runtime/ubuntu` 是源码，必须提交；它不是部署生成目录。
对外报告区分构建、离线测试、容器验收和真实 Windows 客户端验收，不用其中一项代替其他项。

## 合并与验收记录

从最新 main 建立短期分支，保留其他工作区未提交改动。完成受影响检查后通过 PR 合并，再按 main 提交构建部署。确认分支提交已进入 main 后删除远端短期分支，不删除未合并提交或本地恢复记录。

变更正式派生规则或二进制后，相应原生加载和功能证据失效，必须回归；未受影响的结果不为改文档而重复执行。当前目标为 86 级测试实例，不把通用安装器列出的等级扩写为全部原生配套已验收。迁移工具与历史记录保留，但当前未上线项目不要求数据库迁移。

更新状态时同时维护 [统一状态页](docs/dof/status.md)、相关 Markdown 和机器可读摘要。记录证据日期、提交、镜像及适用范围，区分实际通过、跳过、待验证和不适用；不将文档更新时间写成运行测试时间。
