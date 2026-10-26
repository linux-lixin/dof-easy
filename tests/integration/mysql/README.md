# 本地数据库模式验证

`compose_matrix.py` 使用真实 Compose CLI 解析内置／外置 × dof／native × 六种有效插件/团本组合，共 24 种模型，不调用 Docker Engine、不启动容器。

```sh
python3 tests/integration/mysql/compose_matrix.py --compose /absolute/path/docker-compose
```

`run_local.py` 是显式启动的集成测试。它用 bubblewrap 建立独立的 PID、挂载和网络命名空间，只启用自己的 loopback，不访问现有服务器或宿主机 MySQL。要求 Linux 的用户命名空间、bubblewrap，以及本地材料：

- 已提取的 Ubuntu 游戏镜像 rootfs，含 Python、iproute2、数据库客户端与运行库。
- 固定 MySQL 5.6.51 镜像提取的 `usr/sbin/mysqld`、`usr/bin/mysql`、`usr/bin/mysqldump`、系统表脚本和对应依赖。dump 客户端 SHA-256 必须符合 `services/game/docker/ubuntu/mysql56-client.lock.json`。
- 与当前正式锁匹配的免授权派生资源目录。
- 不存在的新输出目录；其中保存的测试数据库、配置和日志均为私有材料，不提交 Git。

```sh
python3 tests/integration/mysql/run_local.py \
  --rootfs /local/game-rootfs --mysql-root /local/mysql56-root \
  --native-runtime /local/dof-runtime --output /local/new-db-test
```

可加 `--jdk /local/jdk --java-etc /local/jdk-config --dependencies /local/jars --classes /local/classes` 运行真实 Spring HTTP 测试。classes 应由当前源码通过 `scripts/dof/compile_admin.py` 编译；java-etc 为提取的 Debian OpenJDK 配置真实路径，以保持 JDK 的符号链接有效。

测试覆盖空库导入、桥接库、23 个原生表、重复初始化保留配置、拒绝其他实例接管、版本／UUID 校验、作用域授权（含数据库名下划线）、游戏加密连接配置、项目范围备份恢复与无关站点保留。启用 Java 时另测登录、原生配置读写回读、审计和旧 revision 拒绝。输出 `result.json` 只有结果，不含账号密码或测试数据库 UUID。

这是数据库与网页接口测试，不能代替 Docker 网络防火墙测试、宝塔实际安装或玩家进游戏验收。测试脚本不会下载材料、重启宿主机或清理外部数据库。
