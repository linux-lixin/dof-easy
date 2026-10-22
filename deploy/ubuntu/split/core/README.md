# Ubuntu CORE — 核心服务

完整跨主机地址、数据库授权、启动顺序和公钥同步见 [拆分部署指南](../README.md)。
本角色无需 Script.pvf。

```bash
cd /opt/dof/deploy/ubuntu/split/core
cp -n .env.example .env
chmod 600 .env
nano .env
# 完成上面指南的前置步骤后：
docker compose config --quiet
docker compose up -d core
docker compose logs -f --tail 100 core
docker compose exec core python3 /opt/dnf/ubuntu/health.py
```

默认数据在本目录 `runtime/core/data`，项目名为 `dnf-ubuntu-core`。`RUNTIME_ROOT` 可指定已有绝对运行根目录。
