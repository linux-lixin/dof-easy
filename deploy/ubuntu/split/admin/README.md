# 独立 onlyGuo 后台

本目录只启动后台，可放在单独主机。与整体部署或 CORE 目录内的 admin 二选一；不能同时启动两个后台写同一数据库。
先完成游戏数据库初始化，再从仓库根构建 `docker build -f services/admin/docker/Dockerfile -t dnf-admin:latest services/admin`。

```bash
cd /opt/dof/deploy/ubuntu/split/admin
cp -n .env.example .env
chmod 600 .env
nano .env
docker compose config --quiet
docker compose up -d admin
docker compose logs -f --tail 100 admin
```

后台地址为 `http://服务器IP:18080/login`。数据和 RSA 密钥在 `runtime/admin/data/`。
把 `publickey.pem` 安全复制到所有游戏频道节点的 `/data/publickey.pem` 对应宿主机目录，重启游戏节点。
首次启动自动生成密钥和数据库初始化标记，`private.key` 仅保留在后台。后续正常启停继续使用同一个 `runtime/`。
管理员初始化 SQL 位于 [共享示例](../../../shared/bootstrap-admin.sql.example)，账号 `admin`，密码由你设置。
后台地址、注册与更新设置见 [后台和客户端配置](../../../../docs/launcher/deployment.md#1-开放端口并配置后台)。
独立后台没有跨主机 `depends_on`；启动顺序由操作者确认。
