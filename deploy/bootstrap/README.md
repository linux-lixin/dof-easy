# 密钥初始化

[init-keys.sh](init-keys.sh)由安装器在 game 镜像的一次性容器中调用，初始化或核对实例密钥。已有私钥、公钥或运行副本不一致时退出，不覆盖冲突文件。

为了兼容既有晴空实例，历史路径 `runtime/admin/data` 仍用于存放密钥；这个目录名不表示 Java 后台在运行。密钥是私有实例身份，不能提交 Git，也不能通过删除后重新生成来处理冲突。

[备份与恢复](../../docs/operations/backup-restore.md) · [部署模板](../README.md)
