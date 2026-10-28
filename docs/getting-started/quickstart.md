# 第一次安装与访问后台

用途与读者：首次安装者。适用范围：dof / native；具体差异见正文。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[menu.py](../../scripts/container/menu.py)；[manage.py](../../scripts/container/manage.py)。限制和当前验收边界见[当前状态](../status/README.md)。


前提：[环境与材料](prerequisites.md)全部就绪。下面是菜单创建新实例；已有实例先读[升级](../operations/upgrade.md)。

```bash
cd dof-easy
sudo bash dof.sh
```

选择新安装、实例名、等级（默认 86）、PVF、客户端可达 IP、频道、插件、数据库与网页监听地址。菜单默认 native、本机构建、网页监听所有接口。确认前检查磁盘、Swap 与防火墙选择；云安全组需自行按最终端口清单配置。不要跳过确认页信息。

安装结束记录实例名和显示的后台地址。默认实例为 `main`；下例在仓库根目录执行：

```bash
bash dof.sh status --instance main
bash dof.sh logs --instance main --service admin
bash dof.sh logs --instance main --service game
```

成功信号是对应容器正常、游戏健康和后台 HTTP 可达。访问安装输出的网页地址，账号 `admin`，密码在该实例私有 `.env` 的 `ADMIN_PASSWORD`。用本机编辑器读取，不将密码复制进工单或 Git。高级命令默认 `127.0.0.1` 网页绑定，远程不能直接访问，需 HTTPS 反向代理或明确更改绑定。公共部署应使用自己的 HTTPS 入口。

登录后进入 `/admin/dof`，核对数据库实例身份；先查询能力与只读记录。原生 UI 配置失败不代表所有模块不可用；保存配置不代表游戏已消费。不要为“绑定成功”重建玩家库。常见失败按[故障指南](../operations/troubleshooting.md)处理。

下一步：[后台日常操作](../operations/admin/README.md)、[客户端配套](../reference/clients/compatibility.md)、[备份](../operations/backup-restore.md)。完整玩家登录、选角、进图、P2P 和团本仍需独立验收。
