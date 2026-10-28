# Ubuntu P2P — relay / STUN

用途与读者：本模块使用者与维护者。适用范围：以正文所列实例和模式为准。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[README.md](https://github.com/linux-lixin/dof-easy/blob/ecd4505d1ebdd28486b13a718cd991874efbe96f/deploy/manual/split/p2p/README.md)。限制和当前验收边界见[当前状态](../../../../docs/status/README.md)。


适用范围：高级手动 Ubuntu 模板、默认 `dof` 登录链与外置 MySQL。默认一键安装及原生 86 级联调另见 [部署导航](../../../README.md)；不要把本页首次初始化步骤用于现有原生测试实例升级。

完整跨主机地址、数据库授权、启动顺序和公钥同步见 [拆分部署指南](../README.md)。
本角色无需 Script.pvf。

```bash
cd /opt/dof-easy/deploy/manual/split/p2p
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
