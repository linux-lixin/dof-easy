# 实例密钥初始化

[init-keys.sh](init-keys.sh)在 game 镜像的一次性容器内初始化和核对晴空实例密钥。只由安装器调用；已有密钥或游戏副本不一致时退出，不覆盖。

历史持久化目录 runtime/admin/data 仍作为密钥存放路径保留，用于兼容已部署晴空实例；这不代表 Java 后台仍在运行。密钥属于私有实例数据，不能提交 Git，也不能通过删除后重新生成来处理冲突。

参见[备份恢复](../../docs/operations/backup-restore.md)。
