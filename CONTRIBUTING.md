# 开发与维护

先读 [项目结构](docs/architecture.md)。部署入口在 `deploy`，实际应用源码在 `services` 和 `clients`。
本项目游戏运行要求 x86_64；不在此次整理中改变协议、数据库版本、业务功能或镜像标签。

## 本地检查

在仓库根目录，使用 Python 3.9+；离线部署修复测试还需 Bash（Windows 使用 Git for Windows）：

```bash
python -m unittest discover -s tests/unit -p "test_*.py"
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
