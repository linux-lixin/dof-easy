# 一键端主机验收

用途与读者：本模块使用者与维护者。适用范围：以正文所列实例和模式为准。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[README.md](https://github.com/linux-lixin/dof-easy/blob/ecd4505d1ebdd28486b13a718cd991874efbe96f/tests/integration/installer/README.md)。限制和当前验收边界见[当前状态](../../../docs/status/README.md)。


仅在新建、可丢弃的 Linux systemd 虚拟机内执行。`guest_probe.py` 会安装软件、创建 Swap、调整 UFW 并启动测试容器，禁止直接在开发主机或现有服务器运行。

将当前 `scripts/container/host.py`、`scripts/container/bootstrap.sh` 和本目录 `guest_probe.py` 放入虚拟机同一目录。准备根用户测试访问方式，在虚拟机建立 `/etc/dof-installer-disposable` 标记。需要独立磁盘和外网下载 Docker 依赖；不要挂入宿主机系统目录、凭据或生产数据。

```sh
# 以下命令仅在一次性虚拟机中
python3 guest_probe.py first
# 保存第一次结果并确认后，重启这一台测试虚拟机
reboot
# 在同一台一次性虚拟机重新连接后
python3 guest_probe.py reboot
# 接着在同一空测试 VM 验证另一个防火墙管理器
python3 guest_probe.py firewalld
```

报告在虚拟机 `/root/installer-result.json`。覆盖真实 Docker 安装与复用、活动 Swap、fstab、已有 Swap 保留、UFW 关闭/恢复、Docker HTTP 访问和重启持久化。firewalld 阶段验证已有 Docker 规则时在操作前拦截、HTTP 保持可用；清理测试容器并停止空测试引擎后，再验证无第三方规则时的关闭和恢复。该阶段的引擎启停及 firewalld reload 仅用于一次性 VM 测试布置，生产安装器不执行这些动作。SSH 连通需由宿主机在各阶段通过该虚拟机独立回环转发端口实测。测试镜像摘要记录在报告中。

单元测试覆盖无空间、swapon/swapoff/fstab 失败、错误 context、多个防火墙和 Docker 规则变化等故障路径。该脚本不执行原生游戏完整验收；相应实际结果在发布报告中单独列出。
