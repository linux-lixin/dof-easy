# 共享后台、初始化示例与日志模板

用途与读者：手动模板维护者。适用明确选择的 Compose 角色；共享片段不能单独当作完整实例启动。

- [admin.yaml](admin.yaml)：共享 admin 服务定义，变量与挂载由所选叶子 Compose/.env 补齐。
- [bootstrap-admin.sql.example](bootstrap-admin.sql.example)：仅隔离测试库的手动管理员创建示例。先检查字段和现有账号；已存在则停止。必须替换密码占位，不对玩家库直接重跑，不用此账号进游戏。
- [logrotate.conf.template](logrotate.conf.template)：供部署工具按实例渲染的日志轮转模板；不表示测试服务器已配置新自动保留策略。

顺序与条件见[手动部署](../../docs/operations/manual-deployment.md)。先 `docker compose --env-file 所选模板/.env -f 所选模板/compose.yaml config` 核对变量和挂载；成功后按模板建库、初始化、启服务。失败先修配置，不复用其他实例 runtime。实际保留情况见[当前部署](../../docs/status/deployment.md)。
