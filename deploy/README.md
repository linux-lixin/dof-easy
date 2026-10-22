# 部署导航

只维护 Ubuntu 24.04 游戏镜像；宿主机使用支持 Docker 的 Linux x86_64。MySQL 始终由你独立管理。

```text
deploy/
├── ubuntu/
│   ├── build/          在服务器构建两个镜像
│   ├── all/            游戏 ALL + 后台
│   ├── split/
│   │   ├── core/       核心服务，可选同机后台
│   │   ├── game/       游戏频道
│   │   ├── p2p/        游戏 relay / STUN
│   │   └── admin/      独立后台
│   └── shared/         游戏公共配置
└── shared/             后台公共配置、管理员 SQL 示例
```

从 [整体部署](ubuntu/all/README.md)、[拆分部署](ubuntu/split/README.md) 或 [独立后台](ubuntu/split/admin/README.md) 中选一种。
每个可执行目录均有 `compose.yaml`、`.env.example`、`README.md`；在所选目录运行 Compose，实际数据写入该目录 `runtime/` 或指定的 `RUNTIME_ROOT`。
`build` 和 `shared` 不是独立运行入口。模板通过相对路径复用公共文件，应保留完整仓库层级。

源码、后台和登录器各维护一份；CORE/GAME/P2P 使用同一个游戏镜像。Relay/STUN 用于游戏联机，不是 VPN 隧道。
默认从全新服务器、专用数据库和空运行目录开始，按所选入口完成首次部署。
