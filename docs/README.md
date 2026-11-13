# 使用文档

适用于 main：晴空原版配套、默认 86 级、宝塔已有的外置 MySQL 5.6。

## 第一次使用

按下面的顺序阅读；已经装好的服务器从第二步开始即可。

1. [安装服务端](operations/qingkong-baota.md)：环境、材料、获取代码、新建实例和首次检查。
2. [连接 Windows 后台与登录器](operations/windows-tools.md)：数据库连接、网关连接、公钥与客户端材料。
3. [日常管理](operations/daily-management.md)：启停、日志、隐藏数据目录、配置与密码位置。

## 按问题查找

| 问题或操作 | 文档 |
|---|---|
| 宝塔与 Docker 怎么连接？两个 game 账号有什么区别？ | [数据库与账号](operations/external-mysql.md) |
| 怎么备份？备份能否直接恢复？ | [备份与恢复](operations/backup-restore.md) |
| 怎么更新 main 和镜像？失败如何回退？ | [升级与回退](operations/upgrade.md) |
| 数据库拒绝连接、健康检查失败、客户端进不去 | [排错指南](operations/troubleshooting.md) |
| 需要哪些端口？ | [端口表](reference/ports.md) |
| 命令有哪些参数、默认值是什么？ | [命令参考](reference/cli.md) |
| 哪些检查通过了，哪些还没测？ | [验证状态](status/README.md) |

## 开发与维护

[开发维护](development/README.md)统一介绍源码目录、检查命令和修改边界；[贡献说明](../CONTRIBUTING.md)说明提交要求。各代码目录的 README 用于解释该目录，不作为另一套安装教程。

文档中的 IP、路径和实例名是示例，实际部署信息以自己的实例为准。真实密码、密钥、PVF、玩家数据和原始日志不放入公开文档。所有运维命令均在仓库根目录执行，示例实例名为 `qingkong86`。
