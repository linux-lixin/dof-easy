# Ubuntu GAME — 游戏频道

适用范围：高级手动 Ubuntu 模板、默认 `dof` 登录链与外置 MySQL。默认一键安装及原生 86 级联调另见 [部署导航](../../../README.md)；不要把本页首次初始化步骤用于现有原生测试实例升级。

完整跨主机地址、数据库授权、启动顺序和公钥同步见 [拆分部署指南](../README.md)。
启动前准备 `runtime/game/data/Script.pvf`、按等级锁校验且可执行的 `df_game_r`，以及与后台配对的 `publickey.pem`。

```bash
cd /opt/dof-easy/deploy/ubuntu/split/game
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
