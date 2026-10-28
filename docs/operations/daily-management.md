# 日常实例管理

用途与读者：运维与开发人员。适用范围：dof / native；具体差异见正文。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[manage.py](../../scripts/container/manage.py)。限制和当前验收边界见[当前状态](../status/README.md)。


前提：仓库根目录、已初始化实例，默认名 main；所有命令显式选择正确实例。停止与重启会中断玩家连接，配置变更可能重建容器。

```bash
bash dof.sh status --instance main
bash dof.sh logs --instance main --service game --follow
bash dof.sh stop --instance main
bash dof.sh start --instance main
bash dof.sh restart --instance main
bash dof.sh config --instance main --ip 192.0.2.10
bash dof.sh config --instance main --plugins frida --raid-extension off
bash dof.sh backup --instance main
```

先查状态再操作。Ctrl+C 退出日志跟随不会停服。已有密码、PVF、插件和数据库不因 start 重灌；`restart` 使用实例固定镜像。修改配置后核对新地址、频道、防火墙和后台回读；失败自动恢复配置不保证服务恢复，应查日志。插件的配置保存、组件刷新与实际游戏效果不同，见[执行归属](../architecture/execution-ownership.md)。

成功信号：目标实例运行状态与变更意图一致；网页和游戏健康分别核对。故障见[故障处理](troubleshooting.md)，备份范围见[备份恢复](backup-restore.md)。
