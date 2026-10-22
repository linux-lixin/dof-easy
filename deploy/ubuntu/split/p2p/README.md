# Ubuntu P2P — relay / STUN

完整跨主机地址、数据库授权、启动顺序和公钥同步见 [拆分部署指南](../README.md)。
本角色无需 Script.pvf。

```bash
cd /opt/dof/deploy/ubuntu/split/p2p
cp -n .env.example .env
chmod 600 .env
nano .env
# 完成上面指南的前置步骤后：
docker compose config --quiet
docker compose up -d p2p
docker compose logs -f --tail 100 p2p
docker compose exec p2p python3 /opt/dnf/ubuntu/health.py
```

默认数据在本目录 `runtime/p2p/data`，项目名为 `dnf-ubuntu-p2p`。`RUNTIME_ROOT` 可指定已有绝对运行根目录。
