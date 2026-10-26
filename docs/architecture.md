# 项目结构与维护边界

根目录 `dof.sh` 是通用安装入口，`scripts/container/` 负责实例和镜像管理。默认 `dof` 登录链与 `native` 原生链共用部分游戏和网页基础代码，但分别验证客户端协议。当前结果见 [状态页](dof/status.md)。

```text
dof-easy/
├── services/
│   ├── game/
│   │   ├── docker/ubuntu/       镜像、兼容库源码与许可证
│   │   ├── assets/              公共模板、初始化 SQL 和共享游戏资源
│   │   ├── resources/           正式资源：plugins/dp2、plugins/frida、native
│   │   └── runtime/ubuntu/
│   │       └── gateway/         原生服务、适配器、默认配置与正式资源锁
│   └── admin/
│       ├── backend/             Java API 与 dof 原生管理服务
│       ├── frontend/            Vue 门户与 /admin/dof 页面
│       ├── database/            通用种子及 dof-admin.sql 管理表定义
│       └── docker/              通用镜像与 dof 预构建镜像入口
├── clients/launcher/            默认 dof 模式 WPF 工程
├── scripts/
│   ├── container/              通用安装、资源与镜像管理
│   ├── dof/                    原生派生、打包、隔离部署及审计
│   ├── protocol/dof/           配套格式与私有客户端输入准备
│   ├── diagnostics/            只读诊断
│   └── release/                镜像发布工具
├── deploy/                     database、bootstrap、nginx、shared、manual
├── metadata/                   公共输入、等级和来源锁
│   └── dof/                    原生改名规则、哈希锁与加载测试断言
├── tests/                      单元、结构及独立集成入口
└── docs/                       操作说明、当前状态与历史证据
```

## 运行数据和控制关系

通用安装器将实例环境和数据放在 `.dof-easy/instances/<名称>/`；数据库使用独立容器卷。手动 `deploy/manual` 模板使用所选目录的 `.env`、`runtime/` 或 `RUNTIME_ROOT`，连接操作者独立管理的 MySQL。两种目录布局不能混用。

原生运行程序安装在 `/opt/dof-easy/`，配置和密钥在 `/data/dof-easy/`；就绪标记在 `/run/dof-easy/`，日志在 `/var/log/dof-easy/`。具体对象见 [游戏组件](../services/game/README.md)。原生网页数据在 `/app/data/`，客户端发布工作区默认 `/app/data/dof-client-publication`；绑定游戏 PVF 使用只读 `/app/paired/Script.pvf`。宿主机实际路径由私有 profile 明确指定。

网页保存配置、审计与发布任务，游戏网关和插件执行对应玩法。`PERSISTED` 证明写入及回读，`READY_FOR_TEST` 证明文件检查通过；两者都不证明玩家已消费。网页 HTTP 服务不代理游戏和 P2P 流量。默认 WPF 配套与原生配置容器使用不同协议。

## 修改位置与构建

| 内容 | 源码入口 |
|---|---|
| 实例、资源、网络和镜像配置 | `scripts/container/` |
| 游戏启动、健康检查及原生网关 | `services/game/runtime/ubuntu/` |
| ELF 正式派生与来源校验 | `scripts/dof/build_server_runtime.py`、`import_assets.py` |
| 原生数据库定义／访问适配 | `services/game/runtime/ubuntu/gateway/schema.py`、`db_adapter.js` |
| Ubuntu ABI 与依赖 | `services/game/docker/ubuntu/` |
| 网页 API 和页面 | `services/admin/backend/src/`、`services/admin/frontend/src/` |
| 通用／原生网页容器 | `services/admin/docker/entrypoint.sh`、`dof-entrypoint.sh` |
| 默认 Windows 登录器 | `clients/launcher/` |
| 私有部署约束与恢复 | `scripts/dof/deployment_profile.py`、`deploy_game_test.py`、`run_isolated.py` |

普通游戏和后台构建上下文分别为 `services/game` 和 `services/admin`，不能只传 Dockerfile 所在子目录。原生网页通过 `compile_admin.py`、`package_admin.py` 准备独立上下文，现有测试游戏使用限定目标的集成镜像入口；具体见 [部署导航](../deploy/README.md)。

`services/game/runtime/ubuntu` 是受版本管理的源码，不能因为名称含 runtime 就排除提交。托管脚本升级仍遵守 `releases.json` 的冲突保护。公共资源和原生私有输入各自按锁校验，不把客户端、PVF 或实例秘密放进源码目录。

前端生成 `dist/`，后端生成 `target/`，WPF 生成 `bin/`、`obj/`；私有原生输入、部署状态及原始证据留在被忽略的私有目录。公开验收摘要只保留脱敏版本和结果，来源与版权见 [metadata](../metadata/README.md)。
