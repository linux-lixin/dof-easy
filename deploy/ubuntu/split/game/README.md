# Ubuntu GAME — 游戏频道

完整跨主机地址、数据库授权、启动顺序和公钥同步见 [拆分部署指南](../README.md)。
启动前手动放置 `runtime/game/data/Script.pvf` 和与后台配对的 `publickey.pem`。

```bash
cd /opt/dof/deploy/ubuntu/split/game
cp -n .env.example .env
chmod 600 .env
nano .env
# 完成上面指南的前置步骤后：
docker compose config --quiet
docker compose up -d game
docker compose logs -f --tail 100 game
docker compose exec game python3 /opt/dnf/ubuntu/health.py
```

默认数据在本目录 `runtime/game/data`，项目名为 `dnf-ubuntu-game`。`RUNTIME_ROOT` 可指定已有绝对运行根目录。
