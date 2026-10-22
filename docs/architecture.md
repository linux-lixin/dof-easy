# 项目结构与维护边界

本仓库把游戏服务、管理后台和 Windows 登录器放在一起维护。按职责分目录，游戏统一使用 Ubuntu 构建入口，
按实际节点分部署目录；共享资源和后台只维护一份。目录重组不修改游戏协议、数据库表结构或 Java/C# 业务逻辑。

```text
dof/
├── README.md
├── CONTRIBUTING.md
├── services/
│   ├── game/
│   │   ├── docker/ubuntu/       Ubuntu Dockerfile、兼容库源码、来源与许可证
│   │   ├── assets/              共享游戏程序、模板、种子 SQL、Script.tgz
│   │   ├── runtime/ubuntu/      Ubuntu 启动适配、健康检查、启动完成提示
│   │   └── LICENSE
│   └── admin/
│       ├── backend/            Maven pom.xml、Java src、Maven 包装脚本
│       ├── frontend/           Vue/Vite 前端与 npm 锁文件
│       ├── docker/             Dockerfile、入口脚本、Nginx 模板
│       └── database/           后台初始化 SQL
├── clients/launcher/            Windows WPF 工程、源码和编译资源
├── deploy/
│   ├── ubuntu/                 build、all、split、shared
│   └── shared/                 后台公共定义、管理员 SQL 示例
├── scripts/
│   ├── diagnostics/            端口表生成等只读诊断
│   └── release/                显式确认后构建并推送后台镜像
├── tests/
│   ├── unit/                   不连接数据库的离线回归
│   ├── structure/              目录、链接、资源、Compose 检查
│   └── integration/            临时数据库与实际镜像验收
├── metadata/                   上游提交、资源路径和哈希清单
└── docs/
    ├── ubuntu/                 Ubuntu 运维、兼容性与历史验收
    ├── admin/                  后台开发、上游说明和配图
    ├── launcher/               登录器使用说明和配图
    └── history/                早期分析与修复记录
```

## 数据和控制关系

登录器调用后台 HTTP API 获取配置、注册/登录及更新信息；后台访问自建 MySQL 并保存自己的 RSA 私钥。
游戏服务读取对应公钥、PVF、配置并访问同一部署所用的 MySQL。玩家游戏连接和 P2P 流量不经过后台 HTTP 服务。
Ubuntu 可运行 ALL，也可拆成 CORE/GAME/P2P；所有角色复用同一后台镜像。

`services/game/assets` 是构建输入，不能把这里直接当在线运行目录。实际部署配置和数据位于
所选 `deploy/...` 目录的 `.env`、`runtime/` 中，或者 `.env` 指定的 `RUNTIME_ROOT`。
`services/game/runtime/ubuntu` 则是**受版本管理的源代码**，与被忽略的部署运行数据不同。

## 修改位置

| 要改什么 | 应修改的目录 |
|---|---|
| Ubuntu 参数、启动、健康检查 | `services/game/runtime/ubuntu` |
| 初始化/月份表修复 | `services/game/assets/home/template/init` |
| Ubuntu ABI 兼容与镜像依赖 | `services/game/docker/ubuntu` |
| 管理后台 API、注册和更新业务 | `services/admin/backend/src` |
| 后台和门户页面 | `services/admin/frontend/src` |
| 后台容器初始化、SSL、密钥保护 | `services/admin/docker/entrypoint.sh` |
| 登录器 UI 和客户端更新逻辑 | `clients/launcher` |
| 部署节点、端口、数据挂载 | `deploy/<系统>/<方式>` |
| 来源与资源校验 | `metadata`，Ubuntu 兼容来源另见 `services/game/docker/ubuntu/compat-lock.json` |

启动脚本直接以 Ubuntu 版本维护，不再维护旧平台生成或补丁覆盖流程。
发行版本的托管脚本升级规则仍由 `services/game/runtime/ubuntu/releases.json` 管理，目录重排不能绕过用户脚本冲突检查。

## 入口与输出

游戏构建上下文固定为 `services/game`；后台构建上下文固定为 `services/admin`，分别使用其中的 Dockerfile。
不能把 `docker/ubuntu` 或 `admin/docker` 当成完整构建上下文，资源在它们的上级组件目录中。
构建命令见 [游戏组件](../services/game/README.md) 与 [后台组件](../services/admin/README.md)。

前端产物为 `services/admin/frontend/dist/`，后端为 `services/admin/backend/target/`，登录器为自身 `bin/`、`obj/`。
临时镜像验收记录与含临时密码的状态文件统一放根目录 `dist/`，均不提交。
正式整理过的验收摘要放在 `docs`；不得复制原始日志、实际 `.env`、生产密钥或镜像 tar 进 Git。

来源和许可证见 [metadata](../metadata/README.md)。真实 Windows 客户端进服、组队与自动更新仍需独立验收。
